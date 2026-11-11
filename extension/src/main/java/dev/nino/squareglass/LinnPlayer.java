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
    final y queue;
    final List<MediaItem> tracks=new ArrayList<>();
    final List<Long> remote=new ArrayList<>();
    final List<Integer> nativeIndices=new ArrayList<>();
    volatile int position,duration;
    volatile MusicServiceState state=MusicServiceState.PAUSED;
    int index;
    boolean wasPlaying,syncing;
    volatile boolean ready;
    int failedObservations;
    long nextRecovery;
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
        this.provider=provider;this.device=device;this.previous=previous;queue=previous.getPlayQueue();
        generation=provider.generation;
    }
    void capture() throws Exception {
        if(queue==null || queue.getItems().isEmpty())throw new Exception("Open an online TIDAL track first.");
        tracks.clear();
        for(u0 item:queue.getItems()) {
            MediaItem media=item.getMediaItem();
            if(!(media instanceof Track) || ((Track)media).isUpload() || media.getId()<=0){
                String reason=!(media instanceof Track)?"not an online music track":((Track)media).isUpload()?"Upload":"no valid TIDAL ID";
                throw new Exception("Linn: Track "+(tracks.size()+1)+" in queue is unsupported ("+reason+"). Please select an online music queue.");
            }
            tracks.add(media);
        }
        index=queue.getCurrentItemPosition();
        if(index<0 || index>=tracks.size())throw new Exception("Current queue position is unavailable.");
        position=previous.getCurrentMediaPosition();wasPlaying=previous.getState()==MusicServiceState.PLAYING;
        shuffled=queue.isShuffled();repeat=queue.getRepeatMode();
        signature=snapshotSignature();
    }
    String metadata(MediaItem track,String uri){
        String title=OpenHome.escape(track.getDisplayTitle()),artist=OpenHome.escape(track.getArtistNames()==null?"":track.getArtistNames());
        return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"><item id=\""+track.getId()+"\" parentID=\"0\" restricted=\"1\"><dc:title>"+title+"</dc:title><upnp:artist>"+artist+"</upnp:artist><upnp:class>object.item.audioItem.musicTrack</upnp:class><res>"+OpenHome.escape(uri)+"</res></item></DIDL-Lite>";
    }
    void transfer() throws Exception {
        SquareOutput.linnProgress("Preparing Linn queue…");
        OpenHome s=device.transport;int max=Integer.parseInt(s.read("Playlist","TracksMax"));
        android.util.Log.i("SquareLinn","Queue capacity read");
        List<Long> original=s.ids();
        int count=repeat==RepeatMode.SINGLE?1:tracks.size();
        LinnQueuePlan.capacity(original.size(),count,max);
        s.preflight();token=s.tidalTokenId();String source=s.read("Product","SourceIndex");
        int playlistSource=s.playlistIndex();
        android.util.Log.i("SquareLinn","Device TIDAL login verified");
        String oldState=s.read("Playlist","TransportState"),oldId=s.read("Playlist","Id");
        boolean committed=false,conflict=false,switched=false;
        String oldRepeat=s.read("Playlist","Repeat"),oldShuffle=s.read("Playlist","Shuffle");
        List<Long> remainingOriginal=new ArrayList<>(original);
        Map<Long,Map<String,String>> backup=s.readTracks(original);
        SquareOutput.linnProgress("Preparing Linn queue…");
        int oldPosition=Integer.parseInt(s.call("Time","Time").get("Seconds"));
        int level=Integer.parseInt(s.read("Volume","Volume"));
        try {
            long after=original.isEmpty()?0:original.get(original.size()-1);
            for(int nativeIndex=0;nativeIndex<tracks.size();nativeIndex++) {
                if(generation!=provider.generation)throw new IOException("Linn handoff was cancelled");
                if(repeat==RepeatMode.SINGLE && nativeIndex!=index)continue;
                MediaItem track=tracks.get(nativeIndex);
                String uri=OpenHome.tidalUri(track.getId(),token);
                after=s.insertConfirmed(LinnQueuePlan.expected(original,remote),after,uri,metadata(track,uri));
                remote.add(after);
                nativeIndices.add(nativeIndex);
                provider.phase(LinnProvider.Phase.TRANSFERRING,"Queue transfer "+remote.size()+"/"+count);
            }
            // Pause the local adapter on the main thread before activating remote transport.
            if(!s.ids().equals(LinnQueuePlan.expected(original,remote))){conflict=true;throw new Exception("Linn queue changed during transfer. Retry.");}
            if(generation!=provider.generation)throw new IOException("Linn handoff was cancelled");
            FutureTask<Void> pause=new FutureTask<>(() -> {if(provider.player==null)position=previous.getCurrentMediaPosition();previous.onActionPause();return null;});
            provider.main.post(pause);pause.get(5,TimeUnit.SECONDS);
            provider.phase(LinnProvider.Phase.CONFIRMING,"Starting playback…");
            switched=true;
            s.call("Playlist","Pause");
            s.call("Product","SetSourceIndex",Integer.toString(playlistSource));
            int selected=nativeIndices.indexOf(index);
            s.call("Playlist","SetRepeat",repeat==RepeatMode.OFF?"0":"1");
            s.call("Playlist","SetShuffle",shuffled?"1":"0");
            s.selectTrack(remote.get(selected),position,wasPlaying);
            for(long id:original){
                if(!s.ids().equals(LinnQueuePlan.expected(remainingOriginal,remote)))throw new OpenHome.Fault(OpenHome.Failure.CONFLICT,"Queue commit",0,"","Linn queue changed in another controller",null);
                s.call("Playlist","DeleteId",Long.toString(id));remainingOriginal.remove(id);
                SquareOutput.linnProgress("Finishing Linn connection…");
            }
            committed=true;
            android.util.Log.i("SquareLinn","Device queue committed; creating remote volume adapter");
            volume=new VolumeProviderCompat(VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE,100,level) {
                @Override public void onSetVolumeTo(int value){command(() -> {device.transport.call("Volume","SetVolumeNoUnmute",Integer.toString(Math.max(0,Math.min(100,value))));});}
                @Override public void onAdjustVolume(int direction){if(direction!=0)command(() -> {device.transport.call("Volume",direction>0?"VolumeIncNoUnmute":"VolumeDecNoUnmute");});}
            };
            android.util.Log.i("SquareLinn","Queue transfer complete");
        } catch(Exception failure) {
            if(!committed)try {
                // Uncertain writes or another controller must never trigger destructive rollback.
                boolean uncertain=failure instanceof OpenHome.Fault && ((OpenHome.Fault)failure).kind==OpenHome.Failure.UNCERTAIN;
                if(conflict || uncertain || !s.ids().equals(LinnQueuePlan.expected(remainingOriginal,remote)))throw new IOException("Recovery paused: device state must be checked before retrying");
                if(switched)s.call("Playlist","Stop");
                List<Long> expected=LinnQueuePlan.expected(remainingOriginal,remote);
                for(long id:remote){if(!s.ids().equals(expected))throw new IOException("Queue changed during recovery");s.call("Playlist","DeleteId",Long.toString(id));expected.remove(id);}
                long after=0;
                for(long id:original){
                    if(!s.ids().equals(expected))throw new IOException("Queue changed during recovery");
                    if(expected.contains(id)){after=id;continue;}
                    Map<String,String> track=backup.get(id);
                    long restored=s.insertConfirmed(expected,after,track.get("Uri"),track.get("Metadata"));
                    expected.add(after==0?0:expected.indexOf(after)+1,restored);after=restored;
                    if(Long.toString(id).equals(oldId))oldId=Long.toString(restored);
                }
                if(switched){
                    s.call("Playlist","SetRepeat",oldRepeat);s.call("Playlist","SetShuffle",oldShuffle);
                    if(!"0".equals(oldId)){s.call("Playlist","SeekId",oldId);s.call("Playlist","SeekSecondAbsolute",Integer.toString(oldPosition));}
                    s.call("Product","SetSourceIndex",source);
                    s.call("Playlist","Playing".equals(oldState)?"Play":"Stopped".equals(oldState)?"Stop":"Pause");
                    // Remain locally paused after an attempted handoff; do not produce double audio.
                }
            }catch(Exception recovery){failure.addSuppressed(recovery);OpenHome.trace.record("recovery=unconfirmed original="+failure.getClass().getSimpleName());}
            throw failure;
        }
    }
    static void seekConfirmed(OpenHome device,int millis) throws Exception {
        device.seekConfirmed(millis);
    }
    String snapshotSignature(){StringBuilder out=new StringBuilder();for(u0 item:queue.getItems())out.append(item.getUid()).append(':').append(item.getMediaItem().getId()).append(',');return out.append(queue.isShuffled()).append(':').append(queue.getRepeatMode()).toString();}
    void prepareReady()throws Exception {
        ready=true;signature=snapshotSignature();
        int level=Integer.parseInt(device.transport.read("Volume","Volume"));
        volume=new VolumeProviderCompat(VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE,100,level){
            public void onSetVolumeTo(int value){command(() -> device.transport.call("Volume","SetVolumeNoUnmute",Integer.toString(Math.max(0,Math.min(100,value)))));}
            public void onAdjustVolume(int direction){if(direction!=0)command(() -> device.transport.call("Volume",direction>0?"VolumeIncNoUnmute":"VolumeDecNoUnmute"));}
        };
        provider.app.getSharedPreferences("squareglass_linn",0).edit().remove("sessionUuid").remove("queue").remove("mapping").apply();
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
    void start(){if(!ready)persist();poll=provider.observations.scheduleWithFixedDelay(this::observe,0,2,TimeUnit.SECONDS);
        events=new LinnEvents(device.transport,this::eventChanged);events.start();}
    void eventChanged(){if(!closed && eventQueued.compareAndSet(false,true))provider.observations.execute(() -> {try{observe();}finally{eventQueued.set(false);}});}
    void close(){closed=true;if(poll!=null)poll.cancel(false);if(events!=null)events.close();}
    void readdress(LinnProvider.Device replacement){
        if(provider.player!=this)return;
        device=replacement;if(events!=null)events.close();
        events=new LinnEvents(device.transport,this::eventChanged);events.start();
        if(closed){closed=false;poll=provider.observations.scheduleWithFixedDelay(this::observe,0,2,TimeUnit.SECONDS);}
        provider.app.getSharedPreferences("squareglass_linn",0).edit().putString("location",device.transport.location).apply();
    }
    void observe(){
        if(closed || syncing)return;
        if(failedObservations>=3){recover();return;}
        OpenHome.deadline.set(System.nanoTime()+TimeUnit.SECONDS.toNanos(6));
        try {
            OpenHome s=device.transport;
            if(ready){s.read("Playlist","TransportState");failedObservations=0;if(provider.phase==LinnProvider.Phase.RECONNECTING)provider.phase(LinnProvider.Phase.READY,"Ready – select music");return;}
            if(!s.playlistSource()){ready=true;state=MusicServiceState.PAUSED;provider.phase(LinnProvider.Phase.READY,"Other source active – select music");return;}
            List<Long> actual=s.ids();
            if(!actual.equals(remote) && !syncing){adoptRemote(actual);return;}
            long id=Long.parseLong(s.read("Playlist","Id"));
            Map<String,String> timing=s.call("Time","Time");
            position=Integer.parseInt(timing.get("Seconds"))*1000;duration=Integer.parseInt(timing.get("Duration"))*1000;
            String transport=s.read("Playlist","TransportState");
            state="Playing".equals(transport)?MusicServiceState.PLAYING:"Paused".equals(transport)?MusicServiceState.PAUSED:MusicServiceState.STOPPED;
            int at=LinnQueuePlan.confirmedIndex(remote,id);int current=at<0?-1:nativeIndices.get(at);
            int level=Integer.parseInt(s.read("Volume","Volume"));
            failedObservations=0;
            if(provider.phase==LinnProvider.Phase.RECONNECTING)provider.phase(LinnProvider.Phase.ACTIVE,"");
            provider.main.post(() -> {
                if(closed || syncing)return;
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
            failedObservations++;
            provider.phase(LinnProvider.Phase.RECONNECTING,"Connection lost – reconnecting…");
            OpenHome.trace.record("observation_failed count="+failedObservations+" type="+failure.getClass().getSimpleName());
        }finally{OpenHome.deadline.remove();}
    }
    void recover(){
        long now=System.nanoTime();if(now<nextRecovery)return;
        nextRecovery=now+TimeUnit.SECONDS.toNanos(Math.min(30,1L<<Math.min(5,failedObservations-3)));
        try{
            Set<String> locations=new LinkedHashSet<>();locations.add(device.transport.location);locations.addAll(OpenHome.discover(1500));
            for(String location:locations)try{
                OpenHome transport=new OpenHome(location);
                if(!transport.uuid.equals(device.transport.uuid))continue;
                transport.read("Playlist","TransportState");
                LinnProvider.Device replacement=new LinnProvider.Device(transport);
                readdress(replacement);provider.connected=replacement;provider.devices.put(replacement.a,replacement);
                failedObservations=0;provider.phase(ready?LinnProvider.Phase.READY:LinnProvider.Phase.ACTIVE,ready?"Ready – select music":"");return;
            }catch(Exception ignored){}
        }catch(Exception ignored){}
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
        if(closed || syncing)return;
        final long queued=System.nanoTime();
        provider.controls.execute(() -> {
            if(closed || syncing)return;
            OpenHome.trace.record("control_queue_ms="+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-queued));
            try{action.run();}
            catch(Exception failure){provider.fail(failure.getMessage());}
        });
    }
    void optimistic(MusicServiceState target){
        state=target;provider.main.post(() -> {if(!closed)NativeLinnBridge.publish(state,position,duration);});
    }
    void synchronize(int target,boolean play,int millis){
        if(syncing || closed)return;syncing=true;
        LinnPlayer replacement=new LinnPlayer(provider,device,previous);
        try{replacement.capture();replacement.index=target;replacement.wasPlaying=play;replacement.position=millis;}
        catch(Exception failure){syncing=false;provider.fail(failure.getMessage());return;}
        provider.io.execute(() -> {
            int revision=provider.generation;
            try {
                replacement.transfer();
                provider.main.post(() -> {
                    if(closed || revision!=provider.generation){replacement.close();return;}
                    close();provider.player=replacement;NativeLinnBridge.activate(replacement);replacement.start();
                    provider.phase(LinnProvider.Phase.ACTIVE,"");
                    for(com.aspiro.wamp.broadcast.l listener:provider.listeners)listener.c();
                });
            }catch(Exception failure){provider.main.post(() -> {if(closed || revision!=provider.generation)return;if(!ready)restoreNativeQueue();syncing=false;provider.fail(LinnProvider.explain(failure));});}
        });
    }
    void select(int target,boolean play){
        if(target<0 || target>=queue.getItems().size())return;
        if(ready || !snapshotSignature().equals(signature) || !nativeIndices.contains(target)){synchronize(target,play,0);return;}
        int at=nativeIndices.indexOf(target);
        command(() -> device.transport.selectTrack(remote.get(at),0,play));
    }
    void persist(){
        try {
            ArrayList<Track> saved=new ArrayList<>();for(MediaItem item:tracks){Track copy=new Track((Track)item);copy.setSource(null);saved.add(copy);}
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ObjectOutputStream out=new ObjectOutputStream(bytes)){out.writeObject(saved);}
            if(bytes.size()>4*1024*1024)throw new IOException("Queue snapshot too large");
            StringBuilder mapping=new StringBuilder();for(int i=0;i<remote.size();i++)mapping.append(remote.get(i)).append(':').append(nativeIndices.get(i)).append(',');
            provider.app.getSharedPreferences("squareglass_linn",0).edit().putString("sessionUuid",device.transport.uuid)
                .putString("queue",Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP)).putString("mapping",mapping.toString())
                .putBoolean("shuffled",shuffled).putString("repeat",repeat.name()).apply();
        }catch(Exception failure){provider.fail("Linn is connected; saving its reconnect state failed.");}
    }
    @SuppressWarnings("unchecked")
    void reattach() throws Exception {
        if(!device.transport.playlistSource())throw new IOException("Another Linn source is active");
        android.content.SharedPreferences prefs=provider.app.getSharedPreferences("squareglass_linn",0);
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
        volume=new VolumeProviderCompat(VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE,100,level) {
            public void onSetVolumeTo(int value){command(() -> device.transport.call("Volume","SetVolumeNoUnmute",Integer.toString(Math.max(0,Math.min(100,value)))));}
            public void onAdjustVolume(int direction){if(direction!=0)command(() -> device.transport.call("Volume",direction>0?"VolumeIncNoUnmute":"VolumeDecNoUnmute"));}
        };
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
    }
    public void onActionPlay(){
        if(ready){select(queue.getCurrentItemPosition(),true);return;}
        optimistic(MusicServiceState.PLAYING);command(() -> device.transport.call("Playlist","Play"));
    }
    public void onActionPause(){if(!ready){optimistic(MusicServiceState.PAUSED);command(() -> device.transport.call("Playlist","Pause"));}}
    void skip(String action){command(() -> {OpenHome s=device.transport;boolean play="Playing".equals(s.read("Playlist","TransportState"));s.call("Playlist",action);if(!play)s.call("Playlist","Pause");});}
    void singleSkip(boolean previous){int target=NativeLinnBridge.skip(queue,previous);if(target>=0)select(target,state==MusicServiceState.PLAYING);}
    public void onActionNext(){if(ready)return;if(repeat==RepeatMode.SINGLE)singleSkip(false);else skip("Next");}
    public void onActionPrevious(boolean force){if(ready)return;if(!force && position>5000)onActionSeekTo(0);else if(repeat==RepeatMode.SINGLE)singleSkip(true);else skip("Previous");}
    public void onActionSeekTo(int millis){if(!ready){position=Math.max(0,millis);command(() -> seekConfirmed(device.transport,millis));}}
    public void onActionStop(PlaybackEndReason reason){if(!ready)command(() -> device.transport.call("Playlist","Stop"));}
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
