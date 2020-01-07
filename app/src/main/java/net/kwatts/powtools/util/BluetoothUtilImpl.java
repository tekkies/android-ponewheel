package net.kwatts.powtools.util;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Environment;
import android.os.ParcelUuid;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;
import android.databinding.ObservableField;

import com.google.common.base.Stopwatch;

import net.kwatts.powtools.App;
import net.kwatts.powtools.connection.BluetoothStateMachine;
import net.kwatts.powtools.connection.states.DisabledState;
import net.kwatts.powtools.Event;
import net.kwatts.powtools.PayloadUtil;
import net.kwatts.powtools.MainActivity;
import net.kwatts.powtools.model.IUnlocker;
import net.kwatts.powtools.model.OWDevice;
import net.kwatts.powtools.model.Session;
import net.kwatts.powtools.Event.InkeyFoundV2;
import net.kwatts.powtools.model.V3Unlocker;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.io.ByteArrayOutputStream;

import de.artcom.hsm.Action;
import de.artcom.hsm.State;
import de.artcom.hsm.StateMachine;
import de.artcom.hsm.Sub;
import de.artcom.hsm.TransitionKind;
import timber.log.Timber;
import uk.co.tekkies.hsm.plantuml.PlantUmlBuilder;
import uk.co.tekkies.hsm.plantuml.PlantUmlUrlEncoder;

import static android.speech.tts.TextToSpeech.SUCCESS;

public class BluetoothUtilImpl implements BluetoothUtil, DiagramCache.CacheFilledCallback {


    private static final String TAG = BluetoothUtilImpl.class.getSimpleName();

    private static final int REQUEST_ENABLE_BT = 1;
    public static ByteArrayOutputStream inkey = new ByteArrayOutputStream();
    public static ObservableField<String> isOWFound = new ObservableField<>();
    public Context mContext;
    Queue<BluetoothGattCharacteristic> characteristicReadQueue = new LinkedList<>();
    Queue<BluetoothGattDescriptor> descriptorWriteQueue = new LinkedList<>();
    private android.bluetooth.BluetoothAdapter mBluetoothAdapter;
    BluetoothGatt mGatt;
    BluetoothGattService owGatService;

    private Map<String, String> mScanResults = new HashMap<>();

    private MainActivity mainActivity;
    OWDevice mOWDevice;
    public boolean sendKey = true;
    private ScanSettings settings;
    private boolean mScanning;
    private long mDisconnected_time;
    private int mRetryCount = 0;
    private int isUnlocked = 0;

    private Handler handler;
    private static int periodicSchedulerCount = 0;

    StateMachine stateMachine; //see docs of https://github.com/artcom/hsm-cs
    private DiagramCache diagramCache;
    private ConnectionEnabledStateMachineBuilder connectionEnabledStateMachineBuilder;
    private StateAnnouncer stateAnnouncer;
    private Runnable timeoutEventRunnable;
    private File ponewheelDirectory;
    private BluetoothStateMachine bluetoothStateMachine;

    //TODO: decouple this crap from the UI/MainActivity
    @Override
    public void init(MainActivity mainActivity, OWDevice mOWDevice) {


        this.mainActivity = mainActivity;
        this.mContext = mainActivity.getApplicationContext();
        this.mOWDevice = mOWDevice;
        stateAnnouncer = new StateAnnouncer(mainActivity);

        this.mBluetoothAdapter = ((BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();

        bluetoothStateMachine = new BluetoothStateMachine();

        connectionEnabledStateMachineBuilder = new ConnectionEnabledStateMachineBuilder(bluetoothStateMachine);
        stateMachine = connectionEnabledStateMachineBuilder.build();

        String cacheDir = mainActivity.getCacheDir().getAbsolutePath() + File.separator + "stateDiagram";
        diagramCache = new DiagramCache(cacheDir, stateMachine)
                .ensurePathExists()
                .fill(this);

        updateStateDiagram();
        Timber.i("Initial state: %s", stateMachine.getAllActiveStates());

        mOWDevice.bluetoothLe.set("On");

        handler = new Handler(Looper.getMainLooper());
        periodicCharacteristics();
    }

    @Override
    public void onDiagramCacheFilled(Boolean success) {
        if (success) {
            saveDiagramToSdCard();
        }
    }

    private void saveDiagramToSdCard() {
        File sdFolder = prepareStorageFolder();
        List<State> allActiveStates = stateMachine.getAllActiveStates();
        State currentState = allActiveStates.get(allActiveStates.size() - 1);
        String sourcePath = diagramCache.getDiagramFilePath(currentState);
        String pngFile = sdFolder + File.separator + "ponewheel-state.png";
        try {
            FileInputStream inStream = new FileInputStream(sourcePath);
            FileOutputStream outStream = new FileOutputStream(pngFile);
            FileChannel inChannel = inStream.getChannel();
            FileChannel outChannel = outStream.getChannel();
            inChannel.transferTo(0, inChannel.size(), outChannel);
            inStream.close();
            outStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String plantUml = new PlantUmlBuilder(stateMachine).build();
        String plantUmlFile = sdFolder + File.separator + "ponewheel-state.plantuml";
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(plantUmlFile);
            stream.write(plantUml.getBytes());

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
        } finally {
            try {
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private File prepareStorageFolder() {
        File externalStorageDirectory = Environment.getExternalStorageDirectory();
        ponewheelDirectory = new File(externalStorageDirectory.toString() + File.separator + "ponewheel");
        if(!ponewheelDirectory.exists()){
            ponewheelDirectory.mkdirs();
        }
        return ponewheelDirectory;
    }

    private void updateStateDiagram() {
        mainActivity.updateStateMachine(diagramCache);
    }

    private String getPlanTextUrl(String plantUml) {
        String url = new PlantUmlUrlEncoder().getUrl(plantUml);
        Timber.i(url);
        return url;
    }


    @NotNull
    private HashMap<String, Object> newSimplePayload(Object result) {
        HashMap<String, Object> payload = new HashMap<String, Object>(1);
        payload.put(result.getClass().getSimpleName(), result);
        return payload;
    }


    public BluetoothUtil getInstance() {
        return this;
    }

    private void updateLog(String s) {
        mainActivity.updateLog(s);
    }

    private void handleStateMachineEvent(String event) {
        handleStateMachineEvent(event, new HashMap<String, Object>());
    }

    private void handleStateMachineEvent(String event, Map<String, Object> payload) {
        cancelStateTimeout();
        stateMachine.handleEvent(event, payload);
        logTransition(event);
        announceState();
        updateStateDiagram();
    }

    private void announceState() {
        State activeState = getActiveState();
        if(activeState != null) {
            stateAnnouncer.announce(activeState.getId());
        }
     }


    private State getActiveState() {
        State activeState = null;
        try {
            if(stateMachine != null) {
                List<State> allActiveStates = stateMachine.getAllActiveStates();
                int index = allActiveStates.size() - 1;
                activeState = allActiveStates.get(index);
        }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return activeState;
    }

    private void logTransition(String event) {
        StringBuilder sb = new StringBuilder(String.format("-- %s -->%s", event, System.lineSeparator()));
        String[] states = stateMachine.toString().replace("(", "").replace(")", "").split("/");
        for (int i = 0; i < states.length; i++) {
            sb.append(new String(new char[(i + 1) * 4]).replace("\0", " "));
            sb.append(states[i]);
            sb.append(System.lineSeparator());
        }
        Timber.i(sb.toString());
    }


    public void connectToGatt(BluetoothGatt gatt) {
        Timber.d("connectToGatt:" + gatt.getDevice().getName());
        gatt.connect();
    }

    private void onOWStateChangedToDisconnected(BluetoothGatt gatt, Context context) {
        //TODO: we really should have a BluetoothService we kill and restart
        Timber.i("We got disconnected from our Device: " + gatt.getDevice().getAddress());
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        Toast.makeText(mainActivity, "We got disconnected from our device: " + gatt.getDevice().getAddress(), Toast.LENGTH_SHORT).show();

        mainActivity.deviceConnectedTimer(false);
        mOWDevice.isConnected.set(false);
        App.INSTANCE.releaseWakeLock();
        mScanResults.clear();

        if (App.INSTANCE.getSharedPreferences().shouldAutoReconnect()) {
            mRetryCount++;
            Timber.i("mRetryCount=" + mRetryCount);

            try {
                if (mRetryCount == 20) {
                    Timber.i("Reached too many retries, stopping search");
                    gatt.close();
                    stopScanning();
                    disconnect();
                    //mainActivity.invalidateOptionsMenu();
                } else {
                    Timber.i("Waiting for 5 seconds until trying to connect to OW again.");
                    TimeUnit.SECONDS.sleep(5);
                    Timber.i("Trying to connect to OW at " + mOWDevice.deviceMacAddress.get());
                    //BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mOWDevice.deviceMacAddress.get());
                    //connectToDevice(device);
                    gatt.connect();
                }
            } catch (InterruptedException e) {
                Timber.d("Connection to OW got interrupted:" + e.getMessage());
            }
        } else {
            gatt.close();
            mainActivity.invalidateOptionsMenu();
        }

    }

    public static boolean isCharacteristicWriteable(BluetoothGattCharacteristic c) {
        return (c.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0;
    }

    public static boolean isCharacteristicReadable(BluetoothGattCharacteristic c) {
        return ((c.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0);
    }

    public static boolean isCharacteristicNotifiable(BluetoothGattCharacteristic c) {
        return (c.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
    }


    // Helpers
    public static int unsignedByte(byte var0) {
        return var0 & 255;
    }

    public static int unsignedShort(byte[] var0) {
        // Short.valueOf(ByteBuffer.wrap(v_bytes).getShort()) also works
        int var1;
        if (var0.length < 2) {
            var1 = -1;
        } else {
            var1 = (unsignedByte(var0[0]) << 8) + unsignedByte(var0[1]);
        }

        return var1;
    }


    @Override
    public boolean isConnected() {
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
    }

    @Override
    public boolean isGemini() {
        boolean isGemini = false;
        Session instance = Session.getInstance();
        if (instance != null) {
            IUnlocker unlocker = instance.getUnlocker();
            if (unlocker != null) {
                isGemini = unlocker.isGemini();
            }
        }
        return isGemini;
    }

    @Override
    public void reconnect(MainActivity activity) {
        activity.startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 1);
    }

    @Override
    public void stopScanning() {
        handleStateMachineEvent(bluetoothStateMachine.events.DISABLE_CONNECTION);
        //AJWOZ scanLeDevice(false);
        if (mGatt != null) {
            mGatt.disconnect();
            mGatt.close();
            mGatt = null;
        }
        mOWDevice.isConnected.set(false);
        this.mScanResults.clear();
        descriptorWriteQueue.clear();
        this.mRetryCount = 0;
        // Added stuff 10/23 to clean fix
        owGatService = null;
        // Added more 3/12/2018
        this.characteristicReadQueue.clear();
        inkey.reset();
        isOWFound.set("false");
        this.sendKey = true;

    }

    @Override
    public boolean isScanning() {
        return mScanning;
    }


    @Override
    public void startScanning() {
        handleStateMachineEvent(bluetoothStateMachine.events.ENABLE_CONNECTION);
        mainActivity.invalidateOptionsMenu();
    }


    @Override
    public void disconnect() {
        handleStateMachineEvent(bluetoothStateMachine.events.DISABLE_CONNECTION);
        //AJWOZ scanLeDevice(false);
        if (mGatt != null) {
            mGatt.disconnect();
            mGatt.close();
            mGatt = null;
        }
        this.mScanResults.clear();
        descriptorWriteQueue.clear();
        // Added stuff 10/23 to clean fix
        owGatService = null;
        inkey.reset();
        isOWFound.set("false");
        this.sendKey = true;
        App.INSTANCE.resetSession();
        isUnlocked = 0;
    }

    @Override
    public BluetoothGattCharacteristic getCharacteristic(String uuidLookup) {
        return owGatService.getCharacteristic(UUID.fromString(uuidLookup));
    }

    @Override
    public void writeCharacteristic(BluetoothGattCharacteristic bluetoothGattCharacteristic) {
        mGatt.writeCharacteristic(bluetoothGattCharacteristic);
    }

    @Override
    public int getIsUnlocked() {
        return isUnlocked;
    }

    @Override
    public boolean isPeriodicChallengeRequired() {
        return isGemini();
    }

    private void periodicCharacteristics() {
        final int repeatTime = 60000; //every minute

        periodicSchedulerCount++;

        if (isUnlocked == 2) {
            walkReadQueue(1);
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isUnlocked == 2) {
                    walkReadQueue(1);
                }
                if (periodicSchedulerCount == 1) {
                    handler.postDelayed(this, repeatTime);
                } else {
                    periodicSchedulerCount--;
                }
            }
        }, repeatTime);
    }

    private void walkNotifyQueue(int state) {
        for (OWDevice.DeviceCharacteristic dc : mOWDevice.getNotifyCharacteristics()) {
            String uuid = dc.uuid.get();
            if (uuid != null && dc.isNotifyCharacteristic && dc.state == state) {
                BluetoothGattCharacteristic localCharacteristic = owGatService.getCharacteristic(UUID.fromString(uuid));
                if (localCharacteristic != null) {
                    if (isCharacteristicNotifiable(localCharacteristic) && dc.isNotifyCharacteristic) {
                        mGatt.setCharacteristicNotification(localCharacteristic, true);
                        BluetoothGattDescriptor descriptor = localCharacteristic.getDescriptor(UUID.fromString(OWDevice.OnewheelConfigUUID));
                        Timber.d("descriptorWriteQueue.size:" + descriptorWriteQueue.size());
                        if (descriptor == null) {
                            Timber.e(uuid + " has a null descriptor!");
                        } else {
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            descriptorWriteQueue.add(descriptor);
                            if (descriptorWriteQueue.size() == 1) {
                                mGatt.writeDescriptor(descriptor);
                            }
                            Timber.d(uuid + " has been set for notifications");
                        }
                    }
                }
            }
        }
    }

    private void walkReadQueue(int state) {
        for (OWDevice.DeviceCharacteristic dc : mOWDevice.getReadCharacteristics()) {
            if (dc.uuid.get() != null && !dc.isNotifyCharacteristic && dc.state == state) {
                Timber.d("uuid:%s, state:%d", dc.uuid.get(), dc.state);
                BluetoothGattCharacteristic c = owGatService.getCharacteristic(UUID.fromString(dc.uuid.get()));
                if (c != null) {
                    if (isCharacteristicReadable(c)) {
                        characteristicReadQueue.add(c);
                        //Read if 1 in the queue, if > 1 then we handle asynchronously in the onCharacteristicRead callback
                        //GIVE PRECEDENCE to descriptor writes.  They must all finish first.
                        Timber.d("characteristicReadQueue.size =" + characteristicReadQueue.size() + " descriptorWriteQueue.size:" + descriptorWriteQueue.size());
                        if (characteristicReadQueue.size() == 1 && (descriptorWriteQueue.size() == 0)) {
                            Timber.i(dc.uuid.get() + " is readable and added to queue");
                            mGatt.readCharacteristic(c);
                        }
                    }
                }
            }
        }
    }

    public void whenActuallyConnected() {
        walkNotifyQueue(0);
        walkReadQueue(0);
        walkReadQueue(1);

        isUnlocked = 2;
    }

    @Override
    public boolean isConectionEnabled() {
        return stateMachine.getAllActiveStates().contains(bluetoothStateMachine.states.enabled);
    }

    private class ConnectionEnabledStateMachineBuilder {


        private BluetoothStateMachine bluetoothStateMachine;


        public ConnectionEnabledStateMachineBuilder(BluetoothStateMachine bluetoothStateMachine) {

            this.bluetoothStateMachine = bluetoothStateMachine;
        }

        private StateMachine build() {
            BluetoothStateMachine.States states = bluetoothStateMachine.states;
            BluetoothStateMachine.Events events = bluetoothStateMachine.events;
            states.disabled = new DisabledState();
            states.enabled = new AdapterEnabledStateMachineBuilder().build();

            states.disabled.addHandler(events.ENABLE_CONNECTION, states.enabled, TransitionKind.External);
            states.enabled.addHandler(events.DISABLE_CONNECTION, states.disabled, TransitionKind.External);

            StateMachine stateMachine = new StateMachine(states.disabled, states.enabled);
            stateMachine.init();
            return stateMachine;
        }



        private class ConnectionEnabledState extends Sub {
            public static final String ID = "Connection Enabled";

            BroadcastReceiver receiver;

            public ConnectionEnabledState(StateMachine bluetoothStateMachine) {
                super(ID, bluetoothStateMachine);
                onEnter(new ListenForBluetoothToggle());
                onExit(new StopListeningForBluetoothToggle());
            }

            private class StopListeningForBluetoothToggle extends Action {
                @Override
                public void run() {
                    mainActivity.unregisterReceiver(receiver);
                }
            }

            private class ListenForBluetoothToggle extends Action {
                @Override
                public void run() {
                    IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
                    receiver = setupAdapterListener();
                    mainActivity.registerReceiver(receiver, filter);

                    if (mBluetoothAdapter.enable()) {
                        handleStateMachineEvent(ConnectionEnabledStateMachineBuilder.AdapterEnabledStateMachineBuilder.ADAPTER_ENABLED);
                    } else {
                        handleStateMachineEvent(ConnectionEnabledStateMachineBuilder.AdapterEnabledStateMachineBuilder.ADAPTER_DISABLED);
                    }
                }
            }

            private BroadcastReceiver setupAdapterListener() {
                final BroadcastReceiver mReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        final String action = intent.getAction();

                        if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                                    BluetoothAdapter.ERROR);
                            switch (state) {
                                case BluetoothAdapter.STATE_OFF:
                                    handleStateMachineEvent(ConnectionEnabledStateMachineBuilder.AdapterEnabledStateMachineBuilder.ADAPTER_DISABLED);
                                    break;
                                case BluetoothAdapter.STATE_TURNING_OFF:
                                    //setButtonText("Turning Bluetooth off...");
                                    break;
                                case BluetoothAdapter.STATE_ON:
                                    handleStateMachineEvent(ConnectionEnabledStateMachineBuilder.AdapterEnabledStateMachineBuilder.ADAPTER_ENABLED);
                                    break;
                                case BluetoothAdapter.STATE_TURNING_ON:
                                    //setButtonText("Turning Bluetooth on...");
                                    break;
                            }
                        }
                    }
                };
                return mReceiver;
            }

        }

        private class AdapterEnabledStateMachineBuilder {

            public static final String ADAPTER_DISABLED = "Adapter Disabled";
            public static final String ADAPTER_ENABLED = "Adapter Enabled";
            public static final String TBC = "TBC";

            public State build() {

                InitState init = new InitState();
                State adapterDisabledState = new State(ADAPTER_DISABLED);
                ConnectionStateMachine connectionStateMachine = new ConnectionStateMachine();
                State adapterEnabledState = new Sub(ADAPTER_ENABLED, connectionStateMachine.createConnectionStateMachine());

                adapterEnabledState.addHandler(ADAPTER_DISABLED, adapterDisabledState, TransitionKind.External);

                adapterDisabledState.addHandler(ADAPTER_ENABLED, adapterEnabledState, TransitionKind.External);



                init.addHandler(ADAPTER_ENABLED, adapterEnabledState, TransitionKind.External);
                init.addHandler(ADAPTER_DISABLED, adapterDisabledState, TransitionKind.External);

                return new ConnectionEnabledState(new StateMachine(init, adapterDisabledState, adapterEnabledState));
            }

            private class InitState extends State {
                public static final String ID = "Init";

                public InitState() {
                    super(ID);
                }

            }

        }
    }

    private void setStateTimeout(String event, int ms) {
        cancelStateTimeout();
        timeoutEventRunnable = () -> {
            handleStateMachineEvent(event);
        };
        handler.postDelayed(timeoutEventRunnable, ms);
    }

    private void cancelStateTimeout() {
        if(timeoutEventRunnable != null) {
            handler.removeCallbacks(timeoutEventRunnable);
            timeoutEventRunnable = null;
        }
    }

    class ConnectionStateMachine {


        public static final String ONEWHEEL_FOUND = "Device found";
        private ScanningState scanningState;
        private State tbcState;
        public RecoveryState recoveryState;


        public StateMachine createConnectionStateMachine() {
            scanningState = new ScanningState();
            recoveryState = new RecoveryState();

            State discoverServices = new DiscoverSericesStateBuilder().build();
            scanningState.addHandler(ONEWHEEL_FOUND, discoverServices, TransitionKind.External);

            tbcState = new State(ConnectionEnabledStateMachineBuilder.AdapterEnabledStateMachineBuilder.TBC);
            discoverServices.addHandler(ConnectionEnabledStateMachineBuilder.AdapterEnabledStateMachineBuilder.TBC, tbcState, TransitionKind.External);


            recoveryState.addHandler(Event.Timeout.ID, scanningState, TransitionKind.External);



            return new StateMachine(scanningState, discoverServices, recoveryState, tbcState);
        }

        private class ScanningState extends State {
            public static final String ID = "Scanning";

            private BluetoothLeScanner mBluetoothLeScanner;


            public ScanningState() {
                super(ID);
                onEnter(new StartScan());
                onExit(new StopScan());
            }

            private class StartScan extends Action {
                @Override
                public void run() {
                    mScanning = true;
                    List<ScanFilter> filters_v2 = new ArrayList<>();
                    ScanFilter scanFilter = new ScanFilter.Builder()
                            .setServiceUuid(ParcelUuid.fromString(OWDevice.OnewheelServiceUUID))
                            .build();
                    filters_v2.add(scanFilter);
                    mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
                    settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
                    mBluetoothLeScanner.startScan(filters_v2, settings, mScanCallback);
                }

            }

            private class StopScan extends Action {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothLeScanner.stopScan(mScanCallback);
                    // added 10/23 to try cleanup
                    mBluetoothLeScanner.flushPendingScanResults(mScanCallback);
                }
            }

            private ScanCallback mScanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    String deviceName = result.getDevice().getName();
                    String deviceAddress = result.getDevice().getAddress();

                    Timber.i("ScanCallback.onScanResult: " + mScanResults.entrySet());
                    if (!mScanResults.containsKey(deviceAddress)) {
                        Timber.i("ScanCallback.deviceName:" + deviceName);
                        mScanResults.put(deviceAddress, deviceName);

                        if (deviceName == null) {
                            Timber.i("Found " + deviceAddress);
                        } else {
                            Timber.i("Found " + deviceAddress + " (" + deviceName + ")");
                        }

                        if (deviceName != null && (deviceName.startsWith("ow") || deviceName.startsWith("Onewheel"))) {
                            mRetryCount = 0;
                            handleStateMachineEvent(ConnectionStateMachine.ONEWHEEL_FOUND, newSimplePayload(result));
                            Timber.i("Looks like we found our OW device (" + deviceName + ") discovering services!");
                        } else {
                            Timber.d("onScanResult: found another device:" + deviceName + "-" + deviceAddress);
                        }

                    } else {
                        Timber.d("onScanResult: mScanResults already had our key, still connecting to OW services or something is up with the BT stack.");
                        //  Timber.d("onScanResult: mScanResults already had our key," + "deviceName=" + deviceName + ",deviceAddress=" + deviceAddress);
                        // still connect
                        //connectToDevice(result.getDevice());
                    }


                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    for (ScanResult sr : results) {
                        Timber.i("ScanCallback.onBatchScanResults.each:" + sr.toString());
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Timber.e("ScanCallback.onScanFailed:" + errorCode);
                }
            };


        }

        private class DiscoverSericesState extends Sub {
            public static final String ID = "Discover Services";

            public DiscoverSericesState(State initialState, State... states) {
                super(ID, initialState, states);
            }

            private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {

                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    Timber.d("Bluetooth connection state change: address=" + gatt.getDevice().getAddress() + " status=" + status + " newState=" + newState);
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Timber.d("STATE_CONNECTED: name=" + gatt.getDevice().getName() + " address=" + gatt.getDevice().getAddress());
                        BluetoothUtilImpl.isOWFound.set("true");
                        handleStateMachineEvent(DiscoverSericesStateBuilder.GATT_CONNECTED, newSimplePayload(gatt));
                        Battery.initStateTwoX(App.INSTANCE.getSharedPreferences());
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Timber.d("STATE_DISCONNECTED: name=" + gatt.getDevice().getName() + " address=" + gatt.getDevice().getAddress());



                        switch(status) {
                            case BluetoothGatt.GATT_CONNECTION_CONGESTED:
                                handleStateMachineEvent(bluetoothStateMachine.events.GATT_CONGESTED_ERROR);
                                break;

                            default:
                                handleStateMachineEvent(bluetoothStateMachine.events.GATT_CONNECT_OTHER_ERROR);
                                break;

                        }
                        /* AJWOZ
                        isUnlocked = 0;
                        BluetoothUtilImpl.isOWFound.set("false");
                        if (gatt.getDevice().getAddress().equals(mOWDevice.deviceMacAddress.get())) {
                            BluetoothUtilImpl bluetoothUtilImpl = BluetoothUtilImpl.this;
                            onOWStateChangedToDisconnected(gatt, bluetoothUtilImpl.mContext);
                        }*/
                    }
                }


                //@SuppressLint("WakelockTimeout")
                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    Timber.d("Only should be here if connecting to OW:" + gatt.getDevice().getAddress());
                    owGatService = gatt.getService(UUID.fromString(OWDevice.OnewheelServiceUUID));

                    if (owGatService == null) {
                        if (gatt.getDevice().getName() == null) {
                            Timber.i("--> " + gatt.getDevice().getAddress() + " not OW, moving on.");
                        } else {
                            Timber.i("--> " + gatt.getDevice().getName() + " not OW, moving on.");
                        }
                        handleStateMachineEvent(DiscoverSericesStateBuilder.TODO_NOT_ONEWHEEL);
                        return;
                    }

                    mGatt = gatt;
                    Timber.i("Hey, I found the OneWheel Service: " + owGatService.getUuid().toString());
                    mainActivity.deviceConnectedTimer(true);
                    mOWDevice.isConnected.set(true);
                    App.INSTANCE.acquireWakeLock();
                    String deviceMacAddress = mGatt.getDevice().toString();
                    String deviceMacName = mGatt.getDevice().getName();
                    mOWDevice.deviceMacAddress.set(deviceMacAddress);
                    mOWDevice.deviceMacName.set(deviceMacName);
                    App.INSTANCE.resetSession();
                    App.INSTANCE.getSharedPreferences().saveMacAddress(
                            mOWDevice.deviceMacAddress.get(),
                            mOWDevice.deviceMacName.get()
                    );
                    Timber.d("Stability Step 1: Only reading the firmware version!");
                    handleStateMachineEvent(DiscoverSericesStateBuilder.READ_FIRMWARE_REVISION);
                }

                @Override
                public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic bluetoothGattCharacteristic, int status) {
                    String characteristic_uuid = bluetoothGattCharacteristic.getUuid().toString();
                    Timber.d("BluetoothGattCallback.onCharacteristicRead: CharacteristicUuid=" +
                            characteristic_uuid +
                            ",status=" + status +
                            ",isGemini=" + isGemini());
                    if (characteristicReadQueue.size() > 0) {
                        characteristicReadQueue.remove();
                    }


                    Timber.d("Stability Step 2: In OnCharacteristicRead, if the value is of the char firmware version, parse it's value.\n" +
                            "If its >= 4034, JUST write the descriptor for the Serial Read characteristic to Enable notifications,\n" +
                            "and set notify to true with gatt. Otherwise its Andromeda or lower and we can call the method to\n" +
                            "read & notify all the characteristics we want. (Although I learned doing this that some android devices\n" +
                            "have a max of 12 notify characteristics at once for some reason. At least I'm pretty sure.)\n" +
                            "I also set a class-wide boolean value isGemini to true here so I don't have to keep checking if its Andromeda\n" +
                            "or Gemini later on.");
                    if (characteristic_uuid.equals(OWDevice.OnewheelCharacteristicFirmwareRevision)) {
                        Timber.d("We have the firmware revision! Checking version.");
                        int firmwareVersion = unsignedShort(bluetoothGattCharacteristic.getValue());
                        Session session = Session.Create(firmwareVersion);

                        if(firmwareVersion <= 4033)
                        {
                            handleStateMachineEvent(DiscoverSericesStateBuilder.GEN_1_FIRMWARE, newSimplePayload(mainActivity.getBluetoothUtil()));
                        } else {
                            PayloadUtil payloadUtil = new PayloadUtil()
                                    .add(owGatService)
                                    .add(gatt)
                                    .add(Event.GeminiFirmware.FIRMWARE_VERSION, firmwareVersion);
                            handleStateMachineEvent(Event.GeminiFirmware.ID, payloadUtil.build());
                        }

                        IUnlocker unlocker = session.getUnlocker();
                        unlocker.start(getInstance(), owGatService, gatt);

                    } else if (characteristic_uuid.equals(OWDevice.OnewheelCharacteristicRidingMode)) {
                        Timber.d("Got ride mode from the main UI thread:" + bluetoothGattCharacteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1));
                    }

                    mOWDevice.processUUID(bluetoothGattCharacteristic);
                    mOWDevice.setBatteryRemaining(mainActivity);
                    if (characteristicReadQueue.size() > 0) {
                        gatt.readCharacteristic(characteristicReadQueue.element());
                    }
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic bluetoothGattCharacteristic) {
                    if (isGemini() && (bluetoothGattCharacteristic.getUuid().toString().equals(OWDevice.OnewheelCharacteristicUartSerialRead))) {
                        handleStateMachineEvent(DiscoverSericesStateBuilder.SERIAL_READ, newSimplePayload(bluetoothGattCharacteristic));
                        /*AJWOZ
                        try {
                            Timber.d("Setting up inkey!");
                            inkey.write(bluetoothGattCharacteristic.getValue());
                            if (inkey.toByteArray().length >= 20 && sendKey) {
                                StringBuilder sb = new StringBuilder();
                                sb.append("GEMINI Step #2: convert inkey=");
                                sb.append(Arrays.toString(inkey.toByteArray()));
                                Timber.d("GEMINI:" + sb.toString());
                                ByteArrayOutputStream outkey = new ByteArrayOutputStream();
                                outkey.write(Util.StringToByteArrayFastest("43:52:58"));
                                byte[] arrayToMD5_part1 = Arrays.copyOfRange(BluetoothUtilImpl.inkey.toByteArray(), 3, 19);
                                byte[] arrayToMD5_part2 = Util.StringToByteArrayFastest("D9255F0F23354E19BA739CCDC4A91765");
                                ByteBuffer arrayToMD5 = ByteBuffer.allocate(arrayToMD5_part1.length + arrayToMD5_part2.length);
                                arrayToMD5.put(arrayToMD5_part1);
                                arrayToMD5.put(arrayToMD5_part2);
                                MessageDigest localMessageDigest = MessageDigest.getInstance("MD5");
                                DigestInputStream digestInputStream = new DigestInputStream(new ByteArrayInputStream(arrayToMD5.array()), localMessageDigest);
                                while (digestInputStream.read(new byte[]{101}) != -1) {
                                }
                                digestInputStream.close();
                                outkey.write(localMessageDigest.digest());
                                byte checkByte = 0;
                                for (byte b : outkey.toByteArray()) {
                                    checkByte = (byte) (b ^ checkByte);
                                }
                                outkey.write(checkByte);
                                StringBuilder sb2 = new StringBuilder();
                                byte[] bArr = arrayToMD5_part1;
                                sb2.append("GEMINI Step #3: write outkey=");
                                sb2.append(Arrays.toString(outkey.toByteArray()));
                                Timber.d("GEMINI" + sb2.toString());
                                BluetoothGattCharacteristic lc = owGatService.getCharacteristic(UUID.fromString(OWDevice.OnewheelCharacteristicUartSerialWrite));
                                lc.setValue(outkey.toByteArray());
                                if (!bluetoothGatt.writeCharacteristic(lc)) {
                                    BluetoothGattCharacteristic bluetoothGattCharacteristic2 = lc;
                                    sendKey = true;
                                } else {
                                    sendKey = false;
                                    bluetoothGatt.setCharacteristicNotification(bluetoothGattCharacteristic, false);
                                }
                                outkey.reset();
                            }
                        } catch (Exception e) {
                            StringBuilder sb3 = new StringBuilder();
                            sb3.append("Exception with GEMINI obfuckstation:");
                            sb3.append(e.getMessage());
                            Timber.d("GEMINI" + sb3.toString());
                        }*/
                    }

                    mOWDevice.processUUID(bluetoothGattCharacteristic);
                    mOWDevice.setBatteryRemaining(mainActivity);
                    handleStateMachineEvent(Event.ReveivedData.ID);
                }


                @Override
                public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic c, int status) {
                    Timber.i("onCharacteristicWrite: " + status + ", CharacteristicUuid=" + c.getUuid().toString());
                    if (c.getUuid().toString().equals(OWDevice.OnewheelCharacteristicUartSerialWrite)) {
                        Timber.d("Step 3: Is Gemini, writing the descriptor onto itself");
                        handleStateMachineEvent(Event.OutkeyWritten.ID);
                    }
                }

                @Override
                public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                    Timber.i("onDescriptorWrite: " + status + ",descriptor=" + descriptor.getUuid().toString() +
                            ",descriptor_characteristic=" + descriptor.getCharacteristic().getUuid().toString());

                    if (isGemini() && descriptor.getCharacteristic().getUuid().toString().equals(OWDevice.OnewheelCharacteristicUartSerialRead)) {
                        Timber.d("Stability Step 3: if isGemini and the characteristic descriptor that was written was Serial Write" +
                                "then trigger the 20 byte input key over multiple serial ble notification stream by writing the firmware version onto itself");
                        gatt.writeCharacteristic(owGatService.getCharacteristic(UUID.fromString(OWDevice.OnewheelCharacteristicFirmwareRevision)));
                    }

                    if (descriptorWriteQueue.size() > 0) {
                        descriptorWriteQueue.remove();
                        if (descriptorWriteQueue.size() > 0) {
                            gatt.writeDescriptor(descriptorWriteQueue.element());
                        } else if (characteristicReadQueue.size() > 0) {
                            gatt.readCharacteristic(characteristicReadQueue.element());
                        }
                    }
                }
            };
        }

        public class DiscoverSericesStateBuilder {

            public static final String GATT_CONNECTED = "Gatt Connected";
            public static final String TODO_NOT_ONEWHEEL = "ToDo Not Onewheel";
            public static final String READ_FIRMWARE_REVISION = "Read Firmware";
            public static final String GEN_1_FIRMWARE = "Gen 1 Firmware";
            public static final String SERIAL_READ = "Serial Read";
            private GetInkeyState getInkeyState;
            private ConnectingState connectingState;
            private DiscoveringServicesState discoveringServicesState;
            private GetOutkeyV3State getOutKeyV3State;
            private GetOutkeyV2State getOutKeyV2State;
            private SendingOutkeyState sendingOutkeyState;
            public ShowTimeState showTimeState;

            public State build() {
                BluetoothStateMachine.States states = bluetoothStateMachine.states;
                BluetoothStateMachine.Events events = bluetoothStateMachine.events;

                connectingState = new ConnectingState();
                discoveringServicesState = new DiscoveringServicesState();
                State readingFirmawareState = new ReadingFirmwareState();
                getInkeyState = new GetInkeyState();
                getOutKeyV2State = new GetOutkeyV2State();
                getOutKeyV3State = new GetOutkeyV3State();
                sendingOutkeyState = new SendingOutkeyState();

                State gattConnectOtherFail = new State("GATT connect other fail");
                State notOnewheelFail = new State("Not Onewheel Fail");
                State connectionLost = new State("Connection lost");
                bluetoothStateMachine.states.gattCongestedState = new State("GATT Congested");

                discoveringServicesState.addHandler(TODO_NOT_ONEWHEEL, notOnewheelFail, TransitionKind.External);
                discoveringServicesState.addHandler(READ_FIRMWARE_REVISION, readingFirmawareState, TransitionKind.External);

                connectingState.addHandler(GATT_CONNECTED, discoveringServicesState, TransitionKind.External);
                connectingState.addHandler(events.GATT_CONNECT_OTHER_ERROR, gattConnectOtherFail, TransitionKind.External);
                connectingState.addHandler(events.GATT_CONGESTED_ERROR, states.gattCongestedState, TransitionKind.External);

                showTimeState = new ShowTimeState();
                readingFirmawareState.addHandler(GEN_1_FIRMWARE, showTimeState, TransitionKind.External);
                readingFirmawareState.addHandler(Event.GeminiFirmware.ID, getInkeyState, TransitionKind.External);

                getInkeyState.addHandler(SERIAL_READ, getInkeyState, TransitionKind.Internal, new OnSerialRead());
                getInkeyState.addHandler(InkeyFoundV2.ID, getOutKeyV2State, TransitionKind.External);
                getInkeyState.addHandler(Event.InkeyFoundV3.ID, getOutKeyV3State, TransitionKind.External);
                getOutKeyV2State.addHandler(Event.GotOutkey.ID, sendingOutkeyState, TransitionKind.External);
                getOutKeyV3State.addHandler(Event.GotOutkey.ID, sendingOutkeyState, TransitionKind.External);

                sendingOutkeyState.addHandler(Event.OutkeyWritten.ID, showTimeState, TransitionKind.External);

                showTimeState.addHandler(Event.ReveivedData.ID, showTimeState, TransitionKind.Internal, new OnReceivedData());
                showTimeState.addHandler(Event.Timeout.ID, connectionLost, TransitionKind.External);

                DiscoverSericesState discoverSericesState = new DiscoverSericesState(
                        connectingState,
                        discoveringServicesState,
                        readingFirmawareState,
                        showTimeState,
                        getInkeyState,
                        getOutKeyV2State,
                        getOutKeyV3State,
                        sendingOutkeyState,

                        gattConnectOtherFail,
                        bluetoothStateMachine.states.gattCongestedState,
                        notOnewheelFail,
                        connectionLost);


                connectingState.inject(discoverSericesState.bluetoothGattCallback);
                return discoverSericesState;
            }

            private class ConnectingState extends State {
                private BluetoothGattCallback bluetoothGattCallback;

                public ConnectingState() {
                    super("Connecting");
                    onEnter(new TryToConnect());
                }

                public void inject(BluetoothGattCallback bluetoothGattCallback) {

                    this.bluetoothGattCallback = bluetoothGattCallback;
                }


                private class TryToConnect extends Action {
                    @Override
                    public void run() {
                        ScanResult result = (ScanResult) mPayload.get(ScanResult.class.getSimpleName());
                        BluetoothDevice device = result.getDevice();
                        device.connectGatt(mainActivity, false, bluetoothGattCallback);
                    }
                }
            }


            private class DiscoveringServicesState extends State {
                public DiscoveringServicesState() {
                    super("Discovering Services");
                    onEnter(new DiscoverServices());
                }

                private class DiscoverServices extends Action {
                    @Override
                    public void run() {
                        discoverGattServices(mPayload);
                    }
                }
            }

            private void discoverGattServices(Map<String, Object> mPayload1) {
                BluetoothGatt gatt = (BluetoothGatt) mPayload1.get(BluetoothGatt.class.getSimpleName());
                gatt.discoverServices();
            }

            private class ReadingFirmwareState extends State {
                public ReadingFirmwareState() {
                    super("Reading Firmware");
                    onEnter(new ReadFirmware());
                }

                private class ReadFirmware extends Action {
                    @Override
                    public void run() {
                        handler.postDelayed(new Runnable() {
                            public void run() {
                                BluetoothUtilImpl.this.mGatt.readCharacteristic(BluetoothUtilImpl.this.owGatService.getCharacteristic(UUID.fromString(OWDevice.OnewheelCharacteristicFirmwareRevision)));
                            }
                        }, 500);
                    }
                }
            }

            private class ShowTimeState extends State {

                public static final int TIMEOUT = 20000;

                public ShowTimeState() {
                    super("Showtime");
                    onEnter(new OnEnter());
                }

                public void OnReceivedData() {
                    setStateTimeout(Event.Timeout.ID, TIMEOUT);
                }

                private class OnEnter extends Action {
                    @Override
                    public void run() {
                        BluetoothUtil bluetoothUtil = getBluetoothUtil();
                        bluetoothUtil.whenActuallyConnected();
                        setStateTimeout(Event.Timeout.ID, TIMEOUT);
                    }
                }
            }

            public class GetInkeyState extends State {

                private final InkeyCollator inkeyCollator;
                private int firmwareVersion;
                BluetoothGatt bluetoothGatt;

                public GetInkeyState() {
                    super("Get Inkey");
                    inkeyCollator = new InkeyCollator();
                    onEnter(new RequestInkey());
                }

                public void onSerialRead(BluetoothGattCharacteristic bluetoothGattCharacteristic) {
                    byte[] value = bluetoothGattCharacteristic.getValue();
                    inkeyCollator.append(value);
                    if(inkeyCollator.isFound()) {
                        if(firmwareVersion <= 4141) {
                            handleStateMachineEvent(InkeyFoundV2.ID, Event.newSimplePayload(inkeyCollator));
                        } else {
                            Map<String, Object> payload = new PayloadUtil()
                                    .add(inkeyCollator)
                                    .add(this.bluetoothGatt)
                                    .build();
                            handleStateMachineEvent(Event.InkeyFoundV3.ID, payload);
                        }
                    }
                }

                private class RequestInkey extends Action {
                    @Override
                    public void run() {
                        BluetoothGattService bluetoothGattService = (BluetoothGattService) mPayload.get(BluetoothGattService.class.getSimpleName());
                        bluetoothGatt = (BluetoothGatt) mPayload.get(BluetoothGatt.class.getSimpleName());
                        firmwareVersion = (int) mPayload.get(Event.GeminiFirmware.FIRMWARE_VERSION);
                        requestInkey(bluetoothGattService, bluetoothGatt);
                    }
                }

                public void requestInkey(BluetoothGattService bluetoothGattService, BluetoothGatt bluetoothGatt) {
                    Timber.d("Stability Step 2.1: JUST write the descriptor for the Serial Read characteristic to Enable notifications");
                    BluetoothGattCharacteristic gC = bluetoothGattService.getCharacteristic(UUID.fromString(OWDevice.OnewheelCharacteristicUartSerialRead));
                    bluetoothGatt.setCharacteristicNotification(gC, true);
                    Timber.d("and set notify to true with gatt...");
                    BluetoothGattDescriptor descriptor = gC.getDescriptor(UUID.fromString(OWDevice.OnewheelConfigUUID));
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    bluetoothGatt.writeDescriptor(descriptor);
                }

            }

            private class OnSerialRead extends Action {
                @Override
                public void run() {
                    BluetoothGattCharacteristic bluetoothGattCharacteristic = (BluetoothGattCharacteristic) mPayload.get(BluetoothGattCharacteristic.class.getSimpleName());
                    getInkeyState.onSerialRead(bluetoothGattCharacteristic);
                }
            }

            private class GetOutkeyV2State extends State {
                public GetOutkeyV2State() {
                    super("Get V2 Outkey");
                    onEnter(new GetV2Outkey());
                }

                private class GetV2Outkey extends Action {
                    @Override
                    public void run() {

                    }
                }
            }

            private class GetOutkeyV3State extends State {

                public GetOutkeyV3State() {
                    super("Get V3 Outkey");
                    onEnter(new GetV3Outkey());
                }

                private class GetV3Outkey extends Action {
                    @Override
                    public void run() {
                        PayloadUtil payloadUtil = new PayloadUtil(mPayload);
                        BluetoothGatt bluetoothGatt = payloadUtil.getPayload(BluetoothGatt.class);
                        String address = bluetoothGatt.getDevice().getAddress();
                        byte[] key = new V3Unlocker(address).getKey();
                        payloadUtil.add(key);
                        handleStateMachineEvent(Event.GotOutkey.ID, payloadUtil.build());
                    }
                }
            }

            private class SendingOutkeyState extends State {
                public SendingOutkeyState() {
                    super("Sending Outkey");
                    onEnter(new SendOutkey());
                }

                private class SendOutkey extends Action {
                    @Override
                    public void run() {
                        PayloadUtil payloadUtil = new PayloadUtil(mPayload);
                        BluetoothGatt bluetoothGatt = payloadUtil.getPayload(BluetoothGatt.class);
                        final byte[] key = payloadUtil.getPayload(byte[].class);
                        handler.postDelayed(() ->
                            sendTheKey(owGatService, bluetoothGatt, key),
                            500);
                    }

                    private void sendTheKey(BluetoothGattService owGatService, BluetoothGatt gatt, byte[] key) {
                        BluetoothGattCharacteristic onewheelCharacteristicUartSerialWrite = owGatService.getCharacteristic(UUID.fromString(OWDevice.OnewheelCharacteristicUartSerialWrite));
                        onewheelCharacteristicUartSerialWrite.setValue(key);
                        if (gatt.writeCharacteristic(onewheelCharacteristicUartSerialWrite)) {
                            unsubscribeUartSerialRead(owGatService, gatt);
                        }
                    }

                    private void unsubscribeUartSerialRead(BluetoothGattService owGatService, BluetoothGatt gatt) {
                        BluetoothGattCharacteristic CharacteristicUartSerialRead = owGatService.getCharacteristic(UUID.fromString(OWDevice.OnewheelCharacteristicUartSerialRead));
                        gatt.setCharacteristicNotification(CharacteristicUartSerialRead, false);
                    }

                }
            }

            private class OnReceivedData extends Action {

                @Override
                public void run() {
                    showTimeState.OnReceivedData();
                }
            }
        }


        private class RecoveryState extends State {
            public RecoveryState() {
                super("Recovery");
                onEnter(new Wait());
            }

            private class Wait extends Action {
                @Override
                public void run() {
                    setStateTimeout(Event.Timeout.ID, 2000);
                }
            }
        }
    }

    private BluetoothUtilImpl getBluetoothUtil() {
        return this;
    }

    private class StateAnnouncer implements TextToSpeech.OnInitListener {

        private final TextToSpeech textToSpeech;
        private final Stopwatch lastUtteranceStopwatch;
        private int utterance;
        private int status;
        private String lastUtternace ="";

        public StateAnnouncer(Context context) {
            textToSpeech = new TextToSpeech(context, this);
            textToSpeech.setLanguage(Locale.US);
            lastUtteranceStopwatch = new Stopwatch().reset().start();
        }

        public void announce(String utterance) {
            if(status == SUCCESS) {
                utterance = reduceRepetition(utterance);
                if(utterance != null) {
                    textToSpeech.speak(utterance, TextToSpeech.QUEUE_FLUSH, null, Integer.toString(this.utterance++));

                }
            }
        }

        private String reduceRepetition(String utterance) {
            if (utterance == lastUtternace) {
                if(lastUtteranceStopwatch.elapsedMillis() < 5000) {
                    utterance = null;
                } else {
                    utterance = "Still "+utterance;
                }
            } else {
                lastUtternace = utterance;
            }
            if(utterance != null) {
                lastUtteranceStopwatch.reset().start();
            }
            return utterance;
        }


        @Override
        public void onInit(int status) {
            this.status = status;
        }
    }
}
