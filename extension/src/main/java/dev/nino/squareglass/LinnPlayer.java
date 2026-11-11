/* SquareGlass. GPL-3.0-or-later. One k0 adapter for UI, service and transport. */
package dev.nino.squareglass;

import com.aspiro.wamp.player.*;
import com.aspiro.wamp.playqueue.*;
import com.aspiro.wamp.model.*;
import com.aspiro.wamp.enums.*;
import androidx.media.VolumeProviderCompat;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import android.util.Base64;
import com.aspiro.wamp.playqueue.source.model.ItemSource;
import org.w3c.dom.Element;
import java.net.URI;
import java.net.URLDecoder;

public final class LinnPlayer implements k0 {
    final LinnProvider provider;
    volatile LinnProvider.Device device;
    final k0 previous;
    k0 sourcePlayer;
    final y queue;
    final List<MediaItem> tracks=new ArrayList<>();
    final List<Long> remote=new ArrayList<>();
    final List<Integer> nativeIndices=new ArrayList<>();
    final List<String> occurrences=new ArrayList<>();
    final LinnCommandQueue latest;
    final LinnTrackSelection selection;
    final java.util.concurrent.atomic.AtomicLong controlRevision=new java.util.concurrent.atomic.AtomicLong();
    volatile long completedControl,lastFullObservation;
    volatile int position,duration;
    volatile MusicServiceState state=MusicServiceState.PAUSED;
    volatile int index;
    boolean wasPlaying;
    volatile boolean syncing;
    volatile boolean ready;
    volatile boolean transferred;
    volatile boolean rollbackConfirmed=true;
    volatile int failedObservations;
    volatile long nextRecovery;
    int generation;
    volatile boolean closed;
    final java.util.concurrent.atomic.AtomicBoolean eventQueued=new java.util.concurrent.atomic.AtomicBoolean();
    boolean shuffled;
    RepeatMode repeat=RepeatMode.OFF;
    String token,signature="";
    ScheduledFuture<?> poll;
    LinnEvents events;
    VolumeProviderCompat volume;
    LinnPlayer(LinnProvider provider,LinnProvider.Device device,k0 previous){
        this.provider=provider;this.device=device;this.sourcePlayer=previous;
        this.previous=previous instanceof LinnPlayer?((LinnPlayer)previous).previous:previous;
        queue=previous.getPlayQueue();latest=new LinnCommandQueue(provider.controls);
        selection=new LinnTrackSelection(provider.controls,() -> this.device.transport,() -> !closed && !syncing && !provider.connecting,
            () -> {completedControl=controlRevision.get();eventChanged();},
            failure -> {completedControl=controlRevision.get();provider.fail(failure.getMessage());eventChanged();});
        generation=provider.generation;
    }
    void capture() throws Exception {
        if(queue==null || queue.getItems().isEmpty())throw new Exception("Open an online TIDAL track first.");
        tracks.clear();occurrences.clear();
        for(u0 item:queue.getItems()) {
            MediaItem media=item.getMediaItem();
            if(!(media instanceof Track) || ((Track)media).isUpload() || media.getId()<=0){
                String reason=!(media instanceof Track)?"not an online music track":((Track)media).isUpload()?"Upload":"no valid TIDAL ID";
                throw new Exception("Linn: Track "+(tracks.size()+1)+" in queue is unsupported ("+reason+"). Please select an online music queue.");
            }
            tracks.add(media);
            occurrences.add(item.getUid()+":"+media.getId());
        }
        index=queue.getCurrentItemPosition();
        if(index<0 || index>=tracks.size())throw new Exception("Current queue position is unavailable.");
        position=sourcePlayer.getCurrentMediaPosition();wasPlaying=sourcePlayer.getState()==MusicServiceState.PLAYING;
        shuffled=queue.isShuffled();repeat=queue.getRepeatMode();
        signature=snapshotSignature();
    }
    String metadata(MediaItem track,String uri){
        String title=OpenHome.escape(track.getDisplayTitle()),artist=OpenHome.escape(track.getArtistNames()==null?"":track.getArtistNames());
        return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"><item id=\""+track.getId()+"\" parentID=\"0\" restricted=\"1\"><dc:title>"+title+"</dc:title><upnp:artist>"+artist+"</upnp:artist><upnp:class>object.item.audioItem.musicTrack</upnp:class><res>"+OpenHome.escape(uri)+"</res></item></DIDL-Lite>";
    }
    android.content.SharedPreferences sessionPreferences(){
        String key=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(device.uuid.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        android.content.SharedPreferences saved=provider.app.getSharedPreferences("squareglass_linn_session_"+key,0);
        if(device.uuid.equals(saved.getString("sessionUuid","")))return saved;
        return provider.app.getSharedPreferences("squareglass_linn",0);
    }
    void transfer() throws Exception {
        provider.phase(LinnProvider.Phase.CHECKING,"Checking device…");
        OpenHome s=device.transport;
        token=s.preflight();int max=Integer.parseInt(s.read("Playlist","TracksMax"));
        String source=s.read("Product","SourceIndex");int playlistSource=s.playlistIndex();
        String oldState=s.read("Playlist","TransportState"),oldId=s.read("Playlist","Id");
        String oldRepeat=s.read("Playlist","Repeat"),oldShuffle=s.read("Playlist","Shuffle");
        int oldPosition=Integer.parseInt(s.call("Time","Time").get("Seconds"));
        int level=Integer.parseInt(s.read("Volume","Volume"));
        List<LinnQueueTransaction.Track> incoming=new ArrayList<>();
        for(int at=0;at<tracks.size();at++){
            if(repeat==RepeatMode.SINGLE && at!=index)continue;
            MediaItem track=tracks.get(at);String uri=OpenHome.tidalUri(track.getId(),token);
            incoming.add(new LinnQueueTransaction.Track(occurrences.get(at),uri,metadata(track,uri)));nativeIndices.add(at);
        }
        List<String> knownKeys=new ArrayList<>();List<Long> knownIds=new ArrayList<>();
        android.content.SharedPreferences saved=sessionPreferences();
        if(device.uuid.equals(saved.getString("sessionUuid","")))try{
            org.json.JSONArray keys=new org.json.JSONArray(saved.getString("occurrences","[]"));
            for(int i=0;i<keys.length();i++)knownKeys.add(keys.getString(i));
            for(String entry:saved.getString("mapping","").split(","))if(!entry.isEmpty())knownIds.add(Long.parseLong(entry.split(":")[0]));
        }catch(Exception invalid){knownKeys.clear();knownIds.clear();}
        LinnQueueTransaction transaction=new LinnQueueTransaction(s,incoming,knownKeys,knownIds,max);
        boolean committed=false,switched=false;
        try{
            transaction.stage(() -> generation==provider.generation && !closed,
                count -> provider.phase(LinnProvider.Phase.TRANSFERRING,"Queue transfer "+count+"/"+incoming.size()));
            remote.addAll(transaction.remote);
            LinnQueueTransaction.requireCurrent(() -> generation==provider.generation && !closed);
            int selected=nativeIndices.indexOf(index);
            boolean sameStream=provider.player!=null && provider.player.device.uuid.equals(device.uuid) &&
                source.equals(Integer.toString(playlistSource)) && oldId.equals(Long.toString(remote.get(selected)));
            if(!sameStream){
                FutureTask<Void> pause=new FutureTask<>(() -> {
                    if(sourcePlayer.isLocal())position=sourcePlayer.getCurrentMediaPosition();
                    if(sourcePlayer.isLocal())sourcePlayer.onActionPause();
                    return null;
                });
                provider.main.post(pause);pause.get(5,TimeUnit.SECONDS);
                if(sourcePlayer instanceof LinnPlayer && !((LinnPlayer)sourcePlayer).device.uuid.equals(device.uuid)){
                    LinnPlayer old=(LinnPlayer)sourcePlayer;
                    old.device.transport.call("Playlist","Pause");
                    old.device.transport.confirm(Long.parseLong(old.device.transport.read("Playlist","Id")),"Paused",5000);
                    position=Integer.parseInt(old.device.transport.call("Time","Time").get("Seconds"))*1000;
                    int at=old.remote.indexOf(Long.parseLong(old.device.transport.read("Playlist","Id")));
                    if(at>=0)index=old.nativeIndices.get(at);
                    selected=nativeIndices.indexOf(index);
                }
            }
            provider.phase(LinnProvider.Phase.CONFIRMING,"Confirming playback…");
            switched=true;
            if(!sameStream)s.call("Playlist","Pause");
            if(!source.equals(Integer.toString(playlistSource)))s.call("Product","SetSourceIndex",Integer.toString(playlistSource));
            if(!oldRepeat.equals(repeat==RepeatMode.OFF?"0":"1"))s.call("Playlist","SetRepeat",repeat==RepeatMode.OFF?"0":"1");
            if(!oldShuffle.equals(shuffled?"1":"0"))s.call("Playlist","SetShuffle",shuffled?"1":"0");
            if(!sameStream)s.selectTrack(remote.get(selected),position,wasPlaying);
            else if(!oldState.equals(wasPlaying?"Playing":"Paused")){
                s.call("Playlist",wasPlaying?"Play":"Pause");s.confirm(remote.get(selected),wasPlaying?"Playing":"Paused",5000);
            }
            transaction.commit(() -> generation==provider.generation && !closed);committed=true;
            state=wasPlaying?MusicServiceState.PLAYING:MusicServiceState.PAUSED;
            createVolume(level);
            OpenHome.trace.record("queue_reused="+transaction.retained.size()+" queue_inserted="+transaction.inserted.size());
        }catch(Exception failure){
            if(!committed)try{
                if(failure instanceof OpenHome.Fault && ((OpenHome.Fault)failure).kind==OpenHome.Failure.UNCERTAIN)
                    throw new IOException("Recovery paused: device write outcome is uncertain");
                OpenHome.deadline.set(System.nanoTime()+TimeUnit.SECONDS.toNanos(15));
                transaction.verifyRollback();
                if(switched)s.call("Playlist","Stop");
                Map<Long,Long> restored=transaction.rollback();
                if(switched){
                    s.call("Playlist","SetRepeat",oldRepeat);s.call("Playlist","SetShuffle",oldShuffle);
                    if(!"0".equals(oldId))s.selectTrack(restored.get(Long.parseLong(oldId)),oldPosition*1000,"Playing".equals(oldState));
                    s.call("Product","SetSourceIndex",source);
                    s.call("Playlist","Playing".equals(oldState)?"Play":"Stopped".equals(oldState)?"Stop":"Pause");
                }
            }catch(Exception recovery){rollbackConfirmed=false;failure.addSuppressed(recovery);OpenHome.trace.record("recovery=unconfirmed original="+failure.getClass().getSimpleName());}
            throw failure;
        }
    }
    void createVolume(int level){
        volume=new VolumeProviderCompat(VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE,100,level){
            public void onSetVolumeTo(int value){commandLatest("volume",() -> device.transport.call("Volume","SetVolumeNoUnmute",Integer.toString(Math.max(0,Math.min(100,value)))));}
            public void onAdjustVolume(int direction){if(direction!=0)command(() -> device.transport.call("Volume",direction>0?"VolumeIncNoUnmute":"VolumeDecNoUnmute"));}
        };
    }
    static void seekConfirmed(OpenHome device,int millis) throws Exception {
        device.seekConfirmed(millis);
    }
    String snapshotSignature(){StringBuilder out=new StringBuilder();for(u0 item:queue.getItems())out.append(item.getUid()).append(':').append(item.getMediaItem().getId()).append(',');return out.append(queue.isShuffled()).append(':').append(queue.getRepeatMode()).toString();}
    void prepareReady()throws Exception {
        ready=true;signature=snapshotSignature();
        int level=Integer.parseInt(device.transport.read("Volume","Volume"));
        createVolume(level);
        provider.app.getSharedPreferences("squareglass_linn",0).edit().putBoolean("autoResume",false).apply();
    }
    void adoptExisting()throws Exception{
        if(!device.transport.playlistSource())return;
        List<Long> ids=device.transport.ids();if(ids.isEmpty())return;
        List<MediaItem> adopted;
        try{adopted=readRemoteTracks(ids);}catch(Exception unsupported){return;}
        int at=ids.indexOf(Long.parseLong(device.transport.read("Playlist","Id")));
        if(at<0 || !device.transport.ids().equals(ids))return;
        tracks.addAll(adopted);remote.addAll(ids);for(int i=0;i<ids.size();i++)nativeIndices.add(i);
        index=at;ready=false;
        shuffled="1".equals(device.transport.read("Playlist","Shuffle"));
        repeat="1".equals(device.transport.read("Playlist","Repeat"))?RepeatMode.ALL:RepeatMode.OFF;
    }
    void start(){sourcePlayer=previous;if(!ready)persist();poll=provider.observations.scheduleWithFixedDelay(this::observe,0,2,TimeUnit.SECONDS);
        events=new LinnEvents(device.transport,this::eventChanged,this::eventReceived);events.start();}
    void eventReceived(LinnEvent event){
        long revision=controlRevision.get();
        provider.main.post(() -> {
            if(closed || syncing || (selection.active() && !"Volume".equals(event.service)) || provider.connecting || revision!=controlRevision.get() || completedControl<revision)return;
            if(!"Volume".equals(event.service) && !snapshotSignature().equals(signature)){eventChanged();return;}
            try{
                Map<String,String> values=event.values;boolean reconcile=false;
                if("Product".equals(event.service)){if(values.containsKey("SourceXmlChangeCount"))device.transport.invalidateSources();reconcile=true;}
                if("Playlist".equals(event.service)){
                    if(values.containsKey("IdArray") && !OpenHome.decodeIds(values.get("IdArray")).equals(remote))reconcile=true;
                    if(values.containsKey("Id")){int at=remote.indexOf(Long.parseLong(values.get("Id")));if(at>=0){
                        int current=nativeIndices.get(at);if(current!=index){index=current;position=0;}
                    }else reconcile=true;}
                    String transport=values.get("TransportState");
                    if(transport!=null && !"Buffering".equals(transport))state="Playing".equals(transport)?MusicServiceState.PLAYING:"Paused".equals(transport)?MusicServiceState.PAUSED:MusicServiceState.STOPPED;
                }
                if("Time".equals(event.service)){
                    if(values.containsKey("Seconds"))position=(int)Math.min(Integer.MAX_VALUE,Long.parseLong(values.get("Seconds"))*1000);
                    if(values.containsKey("Duration"))duration=(int)Math.min(Integer.MAX_VALUE,Long.parseLong(values.get("Duration"))*1000);
                }
                if(index>=0 && index<queue.getItems().size() && queue.getCurrentItemPosition()!=index)queue.goTo(index,false);
                NativeLinnBridge.publish(state,position,duration);
                if("Volume".equals(event.service) && values.containsKey("Volume")){
                    long level=Long.parseLong(values.get("Volume"));if(level<=100 && volume!=null){volume.setCurrentVolume((int)level);provider.nativeVolume.changed((int)level);}
                }
                if(reconcile)eventChanged();
            }catch(Exception invalid){eventChanged();}
        });
    }
    void eventChanged(){if(!closed && eventQueued.compareAndSet(false,true))provider.observations.execute(() -> {try{observe(true);}finally{eventQueued.set(false);}});}
    void close(){closed=true;selection.close();if(poll!=null)poll.cancel(false);if(events!=null)events.close();}
    void readdress(LinnProvider.Device replacement){
        if(provider.player!=this)return;
        selection.close();completedControl=controlRevision.incrementAndGet();
        device=replacement;if(events!=null)events.close();
        events=new LinnEvents(device.transport,this::eventChanged,this::eventReceived);events.start();
        if(closed){closed=false;poll=provider.observations.scheduleWithFixedDelay(this::observe,0,2,TimeUnit.SECONDS);}
        provider.app.getSharedPreferences("squareglass_linn",0).edit().putString("sessionLocation",device.transport.location).apply();
        eventChanged();
    }
    void observe(){observe(false);}
    void observe(boolean force){
        if(closed || syncing || selection.active() || provider.connecting)return;
        long revision=controlRevision.get();if(completedControl<revision)return;
        if(failedObservations>=3){recover();return;}
        OpenHome.deadline.set(System.nanoTime()+TimeUnit.SECONDS.toNanos(6));
        try {
            OpenHome s=device.transport;
            if(!force && !ready && events!=null && events.healthy() && System.nanoTime()-lastFullObservation<TimeUnit.SECONDS.toNanos(30)){
                Map<String,String> timing=s.call("Time","Time");
                int observedPosition=Integer.parseInt(timing.get("Seconds"))*1000,observedDuration=Integer.parseInt(timing.get("Duration"))*1000;
                failedObservations=0;if(provider.phase==LinnProvider.Phase.RECONNECTING)provider.phase(LinnProvider.Phase.ACTIVE,"");
                provider.main.post(() -> {if(!closed && !syncing && revision==controlRevision.get()){position=observedPosition;duration=observedDuration;NativeLinnBridge.publish(state,position,duration);}});return;
            }
            if(ready){s.read("Playlist","TransportState");failedObservations=0;if(provider.phase==LinnProvider.Phase.RECONNECTING)provider.phase(LinnProvider.Phase.READY,"Ready – select music");return;}
            if(!s.playlistSource()){ready=true;state=MusicServiceState.PAUSED;provider.phase(LinnProvider.Phase.READY,"Other source active – select music");return;}
            List<Long> actual=s.ids();
            if(!actual.equals(remote) && !syncing){adoptRemote(actual);return;}
            long id=Long.parseLong(s.read("Playlist","Id"));
            Map<String,String> timing=s.call("Time","Time");
            int observedPosition=Integer.parseInt(timing.get("Seconds"))*1000,observedDuration=Integer.parseInt(timing.get("Duration"))*1000;
            String transport=s.read("Playlist","TransportState");
            MusicServiceState observedState="Buffering".equals(transport)?state:"Playing".equals(transport)?MusicServiceState.PLAYING:"Paused".equals(transport)?MusicServiceState.PAUSED:MusicServiceState.STOPPED;
            int at=LinnQueuePlan.confirmedIndex(remote,id);int current=at<0?-1:nativeIndices.get(at);
            int level=Integer.parseInt(s.read("Volume","Volume"));
            failedObservations=0;
            lastFullObservation=System.nanoTime();
            if(provider.phase==LinnProvider.Phase.RECONNECTING)provider.phase(LinnProvider.Phase.ACTIVE,"");
            provider.main.post(() -> {
                if(closed || syncing || revision!=controlRevision.get())return;
                state=observedState;
                position=observedPosition;duration=observedDuration;
                if(!snapshotSignature().equals(signature)){
                    if(queue.getItems().isEmpty()) {
                        signature=snapshotSignature();command(() -> {
                            List<Long> expected=new ArrayList<>(remote);
                            if(!s.ids().equals(expected))throw new IOException("Queue changed in another controller");
                            s.call("Playlist","Stop");
                            for(long entry:remote){if(!s.ids().equals(expected))throw new IOException("Queue changed in another controller");s.call("Playlist","DeleteId",Long.toString(entry));expected.remove(entry);}
                            remote.clear();nativeIndices.clear();ready=true;
                            provider.phase(LinnProvider.Phase.READY,"Ready – select music");
                        });
                    }else synchronize(queue.getCurrentItemPosition(),state==MusicServiceState.PLAYING,position);
                    return;
                }
                if(current>=0 && current!=index){index=current;queue.goTo(current,false);}
                NativeLinnBridge.publish(state,position,duration);volume.setCurrentVolume(level);provider.nativeVolume.changed(level);
            });
        }catch(Exception failure){
            if(closed || syncing || provider.connecting || revision!=controlRevision.get())return;
            failedObservations++;
            provider.phase(LinnProvider.Phase.RECONNECTING,"Connection lost – reconnecting…");
            OpenHome.trace.record("observation_failed count="+failedObservations+" type="+failure.getClass().getSimpleName());
        }finally{OpenHome.deadline.remove();}
    }
    void recover(){
        long now=System.nanoTime();if(now<nextRecovery || closed || syncing || provider.connecting)return;
        nextRecovery=now+TimeUnit.SECONDS.toNanos(Math.min(30,1L<<Math.min(5,failedObservations-3)));
        OpenHome.deadline.set(now+TimeUnit.SECONDS.toNanos(8));
        try{
            OpenHome[] found={null};
            OpenHome.deadline.set(System.nanoTime()+TimeUnit.SECONDS.toNanos(2));
            try{
                OpenHome known=new OpenHome(device.location);
                if(known.uuid.equals(device.uuid)){known.read("Playlist","TransportState");found[0]=known;}
            }catch(Exception unavailable){}
            OpenHome.deadline.set(now+TimeUnit.SECONDS.toNanos(8));
            if(found[0]==null)try(LinnDiscovery scan=new LinnDiscovery()){
                scan.search(null,2000,result -> {
                    if(found[0]!=null || (!result.uuid.isEmpty() && !result.uuid.equals(device.uuid)))return;
                    try{
                        OpenHome candidate=new OpenHome(result.location);
                        if(candidate.uuid.equals(device.uuid)){candidate.read("Playlist","TransportState");found[0]=candidate;scan.close();}
                    }catch(Exception ignored){}
                });
            }
            if(found[0]!=null){
                LinnProvider.Device replacement=new LinnProvider.Device(found[0]);failedObservations=0;
                provider.controls.execute(() -> {
                    if(closed || syncing || provider.connecting || provider.player!=this)return;
                    readdress(replacement);provider.connected=replacement;provider.devices.put(replacement.a,replacement);
                    provider.phase(ready?LinnProvider.Phase.READY:LinnProvider.Phase.ACTIVE,ready?"Ready – select music":"");
                });
                return;
            }
        }catch(Exception ignored){}finally{OpenHome.deadline.remove();}
        failedObservations++;
    }
    interface Action {void run() throws Exception;}
    List<MediaItem> readRemoteTracks(List<Long> actual) throws Exception {
        List<MediaItem> adopted=new ArrayList<>();
        Map<Long,Map<String,String>> entries=device.transport.readTracks(actual);
        for(long id:actual) {
            Map<String,String> entry=entries.get(id);
            URI uri=new URI(entry.get("Uri"));
            if(!"tidal".equals(uri.getScheme()))throw new IOException("Linn queue now contains non-TIDAL content; its playback was left untouched.");
            Map<String,String> query=new HashMap<>();for(String part:uri.getRawQuery().split("&")){
                String[] pair=part.split("=",2);if(pair.length==2)query.put(pair[0],URLDecoder.decode(pair[1],"UTF-8"));
            }
            int trackId=Integer.parseInt(query.get("trackId"));Track track=null;
            for(MediaItem known:tracks)if(known.getId()==trackId){track=new Track((Track)known);break;}
            if(track==null) {
                Element metadata=OpenHome.xml(entry.get("Metadata").getBytes(java.nio.charset.StandardCharsets.UTF_8)).getDocumentElement();
                track=new Track();track.setId(trackId);track.setTitle(OpenHome.text(metadata,"title"));
                Artist artist=new Artist();artist.setName(OpenHome.text(metadata,"artist"));track.setArtist(artist);track.setArtists(Collections.singletonList(artist));
                Album album=new Album();album.setTitle(OpenHome.text(metadata,"album"));track.setAlbum(album);track.setAllowStreaming(true);track.setStreamReady(true);
            }
            adopted.add(track);
        }
        return adopted;
    }
    void adoptRemote(List<Long> actual) throws Exception {
        List<MediaItem> adopted=readRemoteTracks(actual);
        if(!device.transport.ids().equals(actual))return; // Retry next observation after a concurrent device edit.
        long selected=Long.parseLong(device.transport.read("Playlist","Id"));int at=actual.indexOf(selected);
        provider.main.post(() -> {
            if(closed || syncing)return;
            tracks.clear();tracks.addAll(adopted);remote.clear();remote.addAll(actual);nativeIndices.clear();
            for(int i=0;i<remote.size();i++)nativeIndices.add(i);index=Math.max(0,at);
            restoreNativeQueue();persist();
        });
    }
    void command(Action action){
        submit(null,action,3000);
    }
    void commandLatest(String key,Action action){submit(key,action,3000);}
    void submit(String key,Action action,long budget){
        if(closed || syncing || provider.connecting)return;
        long revision=controlRevision.incrementAndGet();
        final long queued=System.nanoTime();
        Runnable work=() -> {
            if(closed || syncing)return;
            OpenHome.trace.record("control_queue_ms="+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-queued));
            OpenHome.deadline.set(System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(budget));
            try{action.run();}
            catch(Exception failure){provider.fail(failure.getMessage());}
            finally{completedControl=Math.max(completedControl,revision);OpenHome.deadline.remove();eventChanged();}
        };
        if(key==null)provider.controls.execute(work);else latest.execute(key,work);
    }
    void optimistic(MusicServiceState target){
        state=target;provider.main.post(() -> {if(!closed)NativeLinnBridge.publish(state,position,duration);});
    }
    void synchronize(int target,boolean play,int millis){
        if(syncing || closed)return;syncing=true;
        LinnPlayer replacement=new LinnPlayer(provider,device,this);
        try{replacement.capture();replacement.index=target;replacement.wasPlaying=play;replacement.position=millis;}
        catch(Exception failure){syncing=false;provider.fail(failure.getMessage());return;}
        provider.controls.execute(() -> {
            int revision=provider.generation;
            OpenHome.deadline.set(System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(Math.min(120000,20000L+replacement.tracks.size()*150L)));
            try {
                replacement.transfer();
                provider.main.post(() -> {
                    if(closed || revision!=provider.generation){replacement.close();return;}
                    close();provider.player=replacement;NativeLinnBridge.activate(replacement);replacement.start();
                    provider.phase(LinnProvider.Phase.ACTIVE,"");
                    for(com.aspiro.wamp.broadcast.l listener:provider.listeners)listener.c();
                });
            }catch(Exception failure){provider.main.post(() -> {if(closed || revision!=provider.generation)return;if(!ready)restoreNativeQueue();syncing=false;provider.fail(LinnProvider.explain(failure));});}
            finally{OpenHome.deadline.remove();}
        });
    }
    void select(int target,boolean play){
        if(closed || syncing || provider.connecting)return;
        if(target<0 || target>=queue.getItems().size())return;
        if(ready || !snapshotSignature().equals(signature) || !nativeIndices.contains(target)){synchronize(target,play,0);return;}
        int at=nativeIndices.indexOf(target);
        index=target;position=0;optimistic(play?MusicServiceState.PLAYING:MusicServiceState.PAUSED);
        controlRevision.incrementAndGet();selection.select(remote.get(at),0,play);
    }
    void persist(){
        try {
            ArrayList<Track> saved=new ArrayList<>();for(MediaItem item:tracks){Track copy=new Track((Track)item);copy.setSource(null);saved.add(copy);}
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ObjectOutputStream out=new ObjectOutputStream(bytes)){out.writeObject(saved);}
            if(bytes.size()>4*1024*1024)throw new IOException("Queue snapshot too large");
            StringBuilder mapping=new StringBuilder();for(int i=0;i<remote.size();i++)mapping.append(remote.get(i)).append(':').append(nativeIndices.get(i)).append(',');
            org.json.JSONArray keys=new org.json.JSONArray();
            for(int at:nativeIndices)keys.put(occurrences.get(at));
            String key=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(device.uuid.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            for(String file:new String[]{"squareglass_linn","squareglass_linn_session_"+key})
                provider.app.getSharedPreferences(file,0).edit().putString("sessionUuid",device.uuid)
                    .putString("sessionLocation",device.location)
                    .putString("queue",Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP)).putString("mapping",mapping.toString())
                    .putString("occurrences",keys.toString()).putBoolean("shuffled",shuffled).putString("repeat",repeat.name()).apply();
        }catch(Exception failure){provider.fail("Linn is connected; saving its reconnect state failed.");}
    }
    @SuppressWarnings("unchecked")
    void reattach() throws Exception {
        if(!device.transport.playlistSource())throw new IOException("Another Linn source is active");
        android.content.SharedPreferences prefs=sessionPreferences();
        shuffled=prefs.getBoolean("shuffled",false);repeat=RepeatMode.valueOf(prefs.getString("repeat","OFF"));
        if(!device.transport.uuid.equals(prefs.getString("sessionUuid","")))throw new IOException("Different Linn session");
        byte[] data=Base64.decode(prefs.getString("queue",""),Base64.NO_WRAP);
        if(data.length>4*1024*1024)throw new IOException("Invalid queue snapshot");
        try(ObjectInputStream in=new ObjectInputStream(new ByteArrayInputStream(data))){tracks.addAll((List<Track>)in.readObject());}
        for(String entry:prefs.getString("mapping","").split(","))if(!entry.isEmpty()) {
            String[] pair=entry.split(":");remote.add(Long.parseLong(pair[0]));int nativeIndex=Integer.parseInt(pair[1]);
            if(nativeIndex<0 || nativeIndex>=tracks.size())throw new IOException("Invalid queue mapping");nativeIndices.add(nativeIndex);
        }
        List<Long> actual=device.transport.ids();
        if(!actual.equals(remote)) {
            List<MediaItem> adopted=readRemoteTracks(actual);
            if(!device.transport.ids().equals(actual))throw new IOException("Linn queue is changing. Retry reconnecting.");
            tracks.clear();tracks.addAll(adopted);remote.clear();remote.addAll(actual);nativeIndices.clear();
            for(int i=0;i<remote.size();i++)nativeIndices.add(i);
        }
        shuffled="1".equals(device.transport.read("Playlist","Shuffle"));
        if(!"1".equals(device.transport.read("Playlist","Repeat")))repeat=RepeatMode.OFF;
        else if(repeat!=RepeatMode.SINGLE)repeat=RepeatMode.ALL;
        long selected=Long.parseLong(device.transport.read("Playlist","Id"));int at=remote.indexOf(selected);
        if(at<0)throw new IOException("Linn current track is unavailable");index=nativeIndices.get(at);
        int level=Integer.parseInt(device.transport.read("Volume","Volume"));
        createVolume(level);
    }
    void restoreNativeQueue(){
        ItemSource source=new ItemSource("squareglass-linn","Linn queue",ItemSource.NavigationType.None.INSTANCE,null);
        for(MediaItem track:tracks)source.addSourceItem(new Track((Track)track));
        // The persisted list already has its shuffled occurrence order. TURN_ON
        // reshuffles it and changes the current item in the pinned native host.
        queue.prepare(source,new w0(index,false,ShuffleMode.TURN_OFF,repeat,false,false));
        NativeLinnBridge.restoreShuffle(queue,shuffled);
        queue.goTo(index,false);
        signature=snapshotSignature();
        occurrences.clear();for(u0 item:queue.getItems())occurrences.add(item.getUid()+":"+item.getMediaItem().getId());
    }
    public void onActionPlay(){
        if(ready){select(queue.getCurrentItemPosition(),true);return;}
        optimistic(MusicServiceState.PLAYING);
        if(selection.play(true)){controlRevision.incrementAndGet();return;}
        command(() -> device.transport.call("Playlist","Play"));
    }
    public void onActionPause(){if(!ready){optimistic(MusicServiceState.PAUSED);if(selection.play(false)){controlRevision.incrementAndGet();return;}command(() -> device.transport.call("Playlist","Pause"));}}
    void skip(String action){command(() -> {OpenHome s=device.transport;boolean play="Playing".equals(s.read("Playlist","TransportState"));s.call("Playlist",action);if(!play)s.call("Playlist","Pause");});}
    void singleSkip(boolean previous){int target=NativeLinnBridge.skip(queue,previous);if(target>=0)select(target,state==MusicServiceState.PLAYING);}
    public void onActionNext(){if(ready)return;if(repeat==RepeatMode.SINGLE || selection.active())singleSkip(false);else skip("Next");}
    public void onActionPrevious(boolean force){if(ready)return;if(!force && position>5000)onActionSeekTo(0);else if(repeat==RepeatMode.SINGLE || selection.active())singleSkip(true);else skip("Previous");}
    public void onActionSeekTo(int millis){
        if(ready || closed || syncing || provider.connecting)return;
        position=Math.max(0,millis);controlRevision.incrementAndGet();
        if(selection.seek(millis))return;
        int at=nativeIndices.indexOf(index);
        if(at>=0)selection.seekCurrent(remote.get(at),millis,state==MusicServiceState.PLAYING);
        else commandLatest("seek",() -> seekConfirmed(device.transport,millis));
    }
    public void onActionStop(PlaybackEndReason reason){if(!ready){selection.close();optimistic(MusicServiceState.STOPPED);command(() -> device.transport.call("Playlist","Stop"));}}
    public void onActionTogglePlayback(){if(state==MusicServiceState.PLAYING)onActionPause();else onActionPlay();}
    public void onActionPlayPosition(int at,boolean keep,boolean play){select(at,play);}
    public void onActivated(int position,k0 old){}
    public void onDeactivated(){}
    public void onServicePreEnterForeground(){}
    public void onServicePreLeaveForeground(){}
    public void onTaskRemoved(){}
    public void onActionWifiQualityChanged(){}
    public y getPlayQueue(){return queue;}
    public MusicServiceState getState(){return state;}
    public int getCurrentMediaDuration(){return duration;}
    public int getCurrentMediaPosition(){return position;}
    public VolumeProviderCompat getVolumeProvider(){return volume;}
    public li.a getVideoPlayerController(){return null;}
    public Integer getCurrentBitDepth(){return null;}
    public Integer getCurrentBitRate(){return null;}
    public Integer getCurrentSampleRate(){return null;}
    public String getCurrentCodec(){return null;}
    public boolean isLocal(){return false;}
    public boolean isRepeatSupported(){return true;}
    public boolean isSeekingSupported(){return true;}
    public boolean isCurrentStreamAudioOnly(){return true;}
    public boolean isCurrentStreamOnline(){return true;}
    public boolean isCurrentStreamDolbyAtmos(){return false;}
    public boolean isCurrentStreamHiResLosslessQuality(){return false;}
    public boolean isCurrentStreamHighQuality(){return false;}
    public boolean isCurrentStreamLossless(){return false;}
    public boolean isCurrentStreamLowQuality(){return false;}
    public boolean isCurrentStreamMasterQuality(){return false;}
    public boolean isCurrentStreamPaygatedPreview(){return false;}
    public boolean isCurrentStreamPreview(){return false;}
}
