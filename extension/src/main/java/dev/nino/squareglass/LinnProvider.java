/* SquareGlass. GPL-3.0-or-later. */
package dev.nino.squareglass;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import com.aspiro.wamp.broadcast.*;
import com.aspiro.wamp.player.k0;
import java.util.*;
import java.util.concurrent.*;
import io.reactivex.Single;

public final class LinnProvider implements o,q {
    static LinnProvider instance;
    final Application app;
    final Handler main=new Handler(Looper.getMainLooper());
    final ScheduledExecutorService io=Executors.newSingleThreadScheduledExecutor();
    final ScheduledExecutorService controls=Executors.newSingleThreadScheduledExecutor();
    final ScheduledExecutorService observations=Executors.newSingleThreadScheduledExecutor();
    final ScheduledExecutorService discovery=Executors.newSingleThreadScheduledExecutor();
    final ExecutorService descriptions=Executors.newFixedThreadPool(2);
    final ExecutorService warming=Executors.newFixedThreadPool(2);
    final ConcurrentHashMap<String,Integer> probing=new ConcurrentHashMap<>();
    final CopyOnWriteArrayList<l> listeners=new CopyOnWriteArrayList<>();
    final ConcurrentHashMap<String,Device> devices=new ConcurrentHashMap<>();
    final LinnNativeVolume nativeVolume=new LinnNativeVolume(this);
    volatile Device connected;
    volatile LinnPlayer player;
    volatile LinnPlayer pendingPlayer;
    volatile boolean connecting,scanning;
    enum Phase { DISCONNECTED, CHECKING, READY, TRANSFERRING, CONFIRMING, ACTIVE, RECONNECTING, ERROR }
    volatile Phase phase=Phase.DISCONNECTED;
    volatile String status="";
    volatile int generation;
    volatile long attemptExpires;
    volatile boolean resuming;
    void phase(Phase value,String message){phase=value;status=message;OpenHome.trace.record("attempt="+generation+" phase="+value);SquareOutput.linnProgress(message);}
    private volatile int scanRevision;
    private ScheduledFuture<?> scan;
    private volatile LinnDiscovery scanSocket;
    private android.net.wifi.WifiManager.MulticastLock multicast;
    String error="";
    static final class Device extends p2.a {
        volatile OpenHome transport;
        final String location,uuid;
        volatile boolean verified;
        volatile long expires;
        volatile String bootId="",configId="";
        Device(OpenHome transport){this(transport.uuid,transport.name,transport.location);this.transport=transport;verified=true;}
        Device(String uuid,String name,String location){super("LINN_"+uuid,name,0);this.uuid=uuid;this.location=location;expires=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);}
        void resolve()throws Exception {
            OpenHome loaded=verified?OpenHome.cached(location):new OpenHome(location);
            if(!loaded.uuid.equals(uuid))throw new java.io.IOException("A different renderer now uses this address. Refresh outputs.");
            transport=loaded;verified=true;
            OpenHome.descriptions.put(location,loaded);
        }
        @Override public boolean a(){return true;}
    }
    public LinnProvider(Application app){
        this.app=app;instance=this;
        OpenHome.trace=message -> android.util.Log.i("SquareLinn",message);
        try{OpenHome.network=new LinnNetwork(app,() -> main.post(() -> {
            OpenHome.invalidateDescriptions();for(Device device:devices.values())device.verified=false;
            if(scanning)startScanning();
            LinnPlayer active=player;if(active!=null){active.nextRecovery=0;active.failedObservations=Math.max(3,active.failedObservations);active.eventChanged();}
        }));}catch(RuntimeException failure){OpenHome.trace.record("network_binding=unavailable");}
    }
    public o get(){return this;}
    public boolean isAvailable(){return true;}
    public void refreshAvailability(){}
    public void addListener(q.a listener){}
    public void addListener(l listener){listeners.addIfAbsent(listener);}
    public com.aspiro.wamp.broadcast.p getBroadcastProviderButton(){return null;}
    public com.aspiro.wamp.broadcast.r getBroadcastProviderGroupButton(){return null;}
    public fq.f getVolumeControl(){return nativeVolume;}
    public p2.a getConnectedItem(){return connected;}
    public boolean isConnected(){return connected!=null;}
    public boolean isConnecting(){return connecting;}
    public boolean isValidItem(p2.a item){return item instanceof Device;}
    public void init(){main.post(() -> resume(0));}
    void resume(int attempt){
        if(connected!=null || connecting || resuming || attempt>=40)return;
        android.content.SharedPreferences prefs=app.getSharedPreferences("squareglass_linn",0);
        String location=prefs.getString("sessionLocation",prefs.getString("location",""));
        if(location.isEmpty() || prefs.getString("sessionUuid","").isEmpty() || !prefs.getBoolean("autoResume",true))return;
        k0 local;
        try{local=NativeLinnBridge.player();}catch(RuntimeException failure){main.postDelayed(() -> resume(attempt+1),250);return;}
        if(local==null || local.getPlayQueue()==null){main.postDelayed(() -> resume(attempt+1),250);return;}
        if(!local.isLocal() || local.getState()==com.aspiro.wamp.enums.MusicServiceState.PLAYING)return;
        int revision=generation;
        resuming=true;
        io.execute(() -> {
            OpenHome.deadline.set(System.nanoTime()+TimeUnit.SECONDS.toNanos(12));
            try {
                OpenHome transport;
                try{transport=OpenHome.cached(location);if(!transport.uuid.equals(prefs.getString("sessionUuid","")))throw new java.io.IOException("Renderer address changed");transport.read("Playlist","TransportState");}catch(Exception moved){
                    transport=null;
                    for(String candidate:OpenHome.discover(3500))try{
                        OpenHome found=OpenHome.cached(candidate);
                        if(found.uuid.equals(prefs.getString("sessionUuid",""))){transport=found;break;}
                    }catch(Exception ignored){}
                    if(transport==null)throw moved;
                }
                Device device=new Device(transport);LinnPlayer restored=new LinnPlayer(this,device,local);restored.reattach();
                main.post(() -> {
                    if(connected!=null || connecting || revision!=generation || local.getState()==com.aspiro.wamp.enums.MusicServiceState.PLAYING){restored.close();return;}
                    local.onActionPause();restored.restoreNativeQueue();
                    devices.put(device.a,device);connected=device;player=restored;
                    NativeLinnBridge.activate(restored);NativeOutputBridge.confirmSelection(device);for(l listener:listeners)listener.a(this);restored.start();
                    phase(Phase.ACTIVE,"");
                });
            }catch(Exception failure){if(revision==generation)fail("Could not reattach Linn. Its device queue was left unchanged.");}
            finally{resuming=false;OpenHome.deadline.remove();}
        });
    }
    List<p2.a> snapshot(){
        ArrayList<p2.a> available=new ArrayList<>(devices.values());
        String remembered="LINN_"+app.getSharedPreferences("squareglass_linn",0).getString("uuid","");
        available.sort(Comparator.comparing((p2.a device) -> !device.a.equals(remembered)).thenComparing(device -> device.b));return available;
    }
    public Single<List<p2.a>> getAllAvailableDevices(){return Single.fromCallable(this::snapshot);}
    public void startScanning(){
        if(multicast==null)try {
            android.net.wifi.WifiManager wifi=(android.net.wifi.WifiManager)app.getApplicationContext().getSystemService(android.content.Context.WIFI_SERVICE);
            if(wifi!=null){multicast=wifi.createMulticastLock("SquareGlass Linn discovery");multicast.setReferenceCounted(false);multicast.acquire();}
        }catch(RuntimeException unavailable){multicast=null;}
        scanning=true;int revision=++scanRevision;
        if(scan!=null)scan.cancel(false);
        LinnDiscovery previous=scanSocket;if(previous!=null)previous.close();
        android.content.SharedPreferences prefs=app.getSharedPreferences("squareglass_linn",0);
        String cached=prefs.getString("location",""),uuid=prefs.getString("uuid","");
        if(!cached.isEmpty()){
            if(!uuid.isEmpty())devices.putIfAbsent("LINN_"+uuid,new Device(uuid,prefs.getString("name","Linn Majik DSM"),cached));
            probe(cached,revision,null);notifyDevices(revision);
        }
        scan=discovery.schedule(() -> discover(revision),0,TimeUnit.MILLISECONDS);
    }
    void discover(int revision){
        if(!scanning || revision!=scanRevision)return;
        try(LinnDiscovery socket=new LinnDiscovery()) {
            scanSocket=socket;if(!scanning || revision!=scanRevision)return;
            socket.search(null,2500,result -> probe(result.location,revision,result));
            main.post(() -> {
                if(!scanning || revision!=scanRevision)return;
                String remembered="LINN_"+app.getSharedPreferences("squareglass_linn",0).getString("uuid","");
                for(Device device:devices.values())if(device.expires<System.nanoTime() && device!=connected){
                    if(device.a.equals(remembered))device.verified=false;else devices.remove(device.a,device);
                }
                notifyDevices(revision);
            });
        } catch(Exception failure){if(scanning && revision==scanRevision)OpenHome.trace.record("discovery=unavailable");}
        if(scanning && revision==scanRevision)scan=discovery.schedule(() -> discover(revision),8,TimeUnit.SECONDS);
    }
    void notifyDevices(int revision){main.post(() -> {if(scanning && revision==scanRevision){for(l listener:listeners)listener.c();SquareOutput.linnDevicesChanged();}});}
    void probe(String location,int revision,LinnDiscovery.Result result){
        Integer pending=probing.putIfAbsent(location,revision);
        if(pending!=null){if(pending==revision)return;probing.replace(location,pending,revision);}
        descriptions.execute(() -> {
            if(!scanning || revision!=scanRevision){probing.remove(location,revision);return;}
            OpenHome.deadline.set(System.nanoTime()+TimeUnit.SECONDS.toNanos(3));
            try {
                Device old=null;for(Device known:devices.values())if(known.location.equals(location)){old=known;break;}
                if(result!=null && old!=null && ((!old.bootId.isEmpty() && !old.bootId.equals(result.bootId)) || (!old.configId.isEmpty() && !old.configId.equals(result.configId))))OpenHome.invalidateDescriptions();
                OpenHome cached=OpenHome.descriptions.get(location);
                if(cached!=null && result!=null && !result.uuid.isEmpty() && !result.uuid.equals(cached.uuid)){OpenHome.invalidateDescriptions();cached=null;}
                OpenHome transport=OpenHome.cached(location);
                if(result!=null && !result.uuid.isEmpty() && !result.uuid.equals(transport.uuid))throw new java.io.IOException("Renderer identity changed during discovery");
                if(cached!=null && result==null)transport.read("Playlist","TransportState");
                Device device=new Device(transport);
                if(result!=null){device.bootId=result.bootId;device.configId=result.configId;device.expires=System.nanoTime()+result.leaseNanos;}
                main.post(() -> {
                    if(!scanning || revision!=scanRevision)return;
                    devices.put(device.a,device);
                    if(connected!=null && connected.a.equals(device.a)){
                        connected=device;LinnPlayer active=player;if(active!=null && active.device.transport!=transport)controls.execute(() -> active.readdress(device));
                    }
                    notifyDevices(revision);
                });
                warming.execute(() -> {OpenHome.deadline.set(System.nanoTime()+TimeUnit.SECONDS.toNanos(4));try{if(scanning && revision==scanRevision)transport.checkCapabilities();}catch(Exception ignored){}finally{OpenHome.deadline.remove();}});
            }catch(Exception ignored){/* One slow or vanished renderer cannot hold up the other rows. */}
            finally{OpenHome.deadline.remove();probing.remove(location,revision);}
        });
    }
    public void stopScanning(){scanning=false;++scanRevision;if(scan!=null)scan.cancel(false);LinnDiscovery socket=scanSocket;if(socket!=null)socket.close();if(multicast!=null){if(multicast.isHeld())multicast.release();multicast=null;}}
    public static void addAddress(String address){
        LinnProvider p=instance;if(p==null)return;
        if(!address.startsWith("http://") && !address.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")){p.fail("Enter a local IP or the discovered device description URL.");return;}
        p.discovery.execute(() -> {
            try {
                boolean found=false;
                Set<String> locations=address.startsWith("http://")?Collections.singleton(address):OpenHome.discover(address,3500);
                for(String location:locations) {
                    OpenHome transport=OpenHome.cached(location);Device device=new Device(transport);p.devices.put(device.a,device);found=true;
                    p.app.getSharedPreferences("squareglass_linn",0).edit().putString("location",location).putString("uuid",transport.uuid).putString("name",transport.name).apply();
                }
                if(!found)throw new Exception("No Linn renderer answered at this address.");
                p.main.post(() -> {for(l listener:p.listeners)listener.c();SquareOutput.changed();});
            }catch(Exception failure){p.fail(failure.getMessage());}
        });
    }
    public void connect(p2.a item){
        android.util.Log.i("SquareLinn","Native provider connect requested");
        if(!(item instanceof Device) || connecting)return;
        Device device=(Device)item;k0 previous=NativeLinnBridge.player();
        if(connected!=null && connected.a.equals(device.a) && player!=null){for(l listener:listeners)listener.a(this);return;}
        if(previous==null || (!previous.isLocal() && !(previous instanceof LinnPlayer))){fail("The previous output must finish disconnecting before connecting Linn.");return;}
        LinnPlayer candidate=new LinnPlayer(this,device,previous);
        if(previous.getPlayQueue()==null){fail("TIDAL player is still starting. Retry shortly.");return;}
        boolean empty=previous.getPlayQueue()==null || previous.getPlayQueue().getItems().isEmpty();
        try {if(!empty)candidate.capture();}catch(Exception failure){fail(failure.getMessage());return;}
        connecting=true;error="";
        int revision=++generation;
        candidate.generation=revision;
        pendingPlayer=candidate;
        attemptExpires=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(empty?12000:Math.min(120000,20000L+candidate.tracks.size()*150L));
        phase(Phase.CHECKING,"Checking device…");
        controls.execute(() -> {
            OpenHome.attempt.set(Integer.toString(revision));
            OpenHome.deadline.set(attemptExpires);
            android.util.Log.i("SquareLinn","Queue transfer started");
            try {
                device.resolve();
                if(empty){device.transport.preflight();candidate.prepareReady();candidate.adoptExisting();}else {candidate.transfer();candidate.transferred=true;}
                main.post(() -> {
                    if(revision!=generation){candidate.close();return;}
                    LinnPlayer old=player;if(old!=null && old!=candidate)old.close();
                    player=candidate;connected=device;connecting=false;
                    pendingPlayer=null;
                    NativeLinnBridge.activate(candidate);
                    NativeOutputBridge.confirmSelection(device);
                    app.getSharedPreferences("squareglass_linn",0).edit().putString("location",device.transport.location)
                        .putString("uuid",device.transport.uuid).putString("name",device.transport.name).putBoolean("autoResume",!candidate.ready).apply();
                    for(l listener:listeners)listener.a(this);
                    if(!candidate.ready && empty)candidate.restoreNativeQueue();
                    candidate.start();
                    phase(candidate.ready?Phase.READY:Phase.ACTIVE,candidate.ready?"Ready – select music":"");
                });
            } catch(Exception | LinkageError failure){android.util.Log.e("SquareLinn","Linn adapter connection failed: "+failure.getClass().getSimpleName());main.post(() -> {
                if(revision!=generation)return;
                if(candidate.rollbackConfirmed)pendingPlayer=null;
                connecting=false;NativeOutputBridge.restoreLocalSelection(previousOutput);fail(explain(failure));
                // Selecting the local provider here can start audio while a timed-out Linn is playing.
            });}finally{OpenHome.attempt.remove();OpenHome.deadline.remove();}
        });
    }
    public void disconnect(o.a callback){
        LinnPlayer active=player;
        ++generation;connecting=true;
        controls.execute(() -> {
            OpenHome.deadline.set(System.nanoTime()+TimeUnit.SECONDS.toNanos(6));
            try {
                LinnPlayer pending=pendingPlayer;
                if(pending!=null && !pending.rollbackConfirmed)throw new java.io.IOException("Linn recovery was not confirmed");
                if(pending!=null && pending.transferred){
                    pending.device.transport.call("Playlist","Pause");
                    pending.device.transport.confirm(Long.parseLong(pending.device.transport.read("Playlist","Id")),"Paused",5000);
                    pending.close();
                }
                if(active==null){main.post(() -> {pendingPlayer=null;connecting=false;phase(Phase.DISCONNECTED,"");NativeLinnBridge.disconnected(callback);});return;}
                if(!active.ready){
                    active.device.transport.call("Playlist","Pause");
                    active.device.transport.confirm(Long.parseLong(active.device.transport.read("Playlist","Id")),"Paused",5000);
                    Map<String,String> timing=active.device.transport.call("Time","Time");
                    active.position=Integer.parseInt(timing.get("Seconds"))*1000;
                    long selected=Long.parseLong(active.device.transport.read("Playlist","Id"));int at=active.remote.indexOf(selected);
                    if(at>=0)active.index=active.nativeIndices.get(at);
                }
                active.state=com.aspiro.wamp.enums.MusicServiceState.PAUSED;
                main.post(() -> {
                    connected=null;player=null;pendingPlayer=null;connecting=false;active.close();
                    phase(Phase.DISCONNECTED,"");
                    if(!active.ready)active.persist();
                    app.getSharedPreferences("squareglass_linn",0).edit().putBoolean("autoResume",false).apply();
                    NativeLinnBridge.publish(com.aspiro.wamp.enums.MusicServiceState.PAUSED,active.position,active.duration);
                    NativeLinnBridge.activate(active.previous);
                    // Return paused. No unsolicited local audio on network loss or handback.
                    active.previous.onActionPause();if(!active.ready){active.previous.onActionPlayPosition(active.index,false,false);active.previous.onActionSeekTo(active.position);}
                    for(l listener:listeners)listener.d(this);
                    NativeLinnBridge.disconnected(callback);
                });
            }catch(Exception failure){connecting=false;fail("Could not confirm Linn pause. Retry before switching outputs.");}
            finally{OpenHome.deadline.remove();}
        });
    }
    public void cancelPending(){
        LinnPlayer candidate=pendingPlayer;if(candidate==null || !connecting)return;
        int revision=++generation;
        phase(Phase.CONFIRMING,"Verbindung abbrechen…");
        attemptExpires=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        controls.execute(() -> {
            OpenHome.deadline.set(System.nanoTime()+TimeUnit.SECONDS.toNanos(5));
            try{
                if(!candidate.rollbackConfirmed)throw new java.io.IOException("Wiederherstellung nicht bestätigt; Gerätezustand vor erneutem Versuch prüfen.");
                if(candidate.transferred){candidate.device.transport.call("Playlist","Pause");candidate.device.transport.confirm(Long.parseLong(candidate.device.transport.read("Playlist","Id")),"Paused",5000);}
                candidate.close();
                main.post(() -> {
                    if(revision!=generation)return;
                    pendingPlayer=null;connecting=false;
                    NativeOutputBridge.restoreLocalSelection(previousOutput);
                    phase(player==null?Phase.DISCONNECTED:player.ready?Phase.READY:Phase.ACTIVE,"");
                    SquareOutput.linnFailure("Verbindung abgebrochen");
                });
            }catch(Exception failure){main.post(() -> {if(revision==generation){connecting=false;fail(failure.getMessage());}});}
            finally{OpenHome.deadline.remove();}
        });
    }
    public static boolean canRestoreSelection(Object item){return item instanceof Device && instance!=null && instance.connected!=null && instance.player!=null && !instance.player.closed && instance.connected.a.equals(((Device)item).a);}
    public static boolean directSelection(Object item){
        LinnProvider provider=instance;
        if(!(item instanceof Device) || provider==null || provider.player==null || provider.connected==null)return false;
        if(!provider.connecting)provider.connect((Device)item);
        return true;
    }
    void fail(String message){
        phase=Phase.ERROR;
        error=message==null?"Linn request failed. Retry.":message;
        status=error;
        android.util.Log.w("SquareLinn",error);
        main.post(() -> {
            if(player==null && !connecting)NativeOutputBridge.restoreLocalSelection(previousOutput);
            Toast.makeText(app,error,Toast.LENGTH_LONG).show();SquareOutput.linnFailure(error);
        });
    }
    static String explain(Throwable failure){
        String message=failure.getMessage();
        if(failure.getSuppressed().length>0)message+=". Recovery unconfirmed; verify device state before retrying.";
        return message;
    }
    public static Object override(Object proposed){
        LinnProvider provider=instance;
        return provider!=null && provider.connected!=null && provider.player!=null?provider.player:proposed;
    }
    public static String label(Object device){return device instanceof Device && instance!=null && instance.connected!=null && ((Device)device).a.equals(instance.connected.a) && !instance.status.isEmpty()?instance.status:device instanceof Device?((Device)device).verified?"Linn · OpenHome":"Zuletzt verwendet · Gerät prüfen…":null;}
    static Object previousOutput;
    public static void prepareSelection(Object item){if(item instanceof Device){android.util.Log.i("SquareLinn","Linn output selected");previousOutput=NativeOutputBridge.active();}else if(instance!=null && instance.connecting){++instance.generation;instance.connecting=false;instance.phase(Phase.DISCONNECTED,"");}}
    public static Object confirmed(Object nativeActive){
        LinnProvider p=instance;
        return nativeActive instanceof Device?p!=null && p.connected!=null && p.player!=null?p.connected:previousOutput:nativeActive;
    }
}
