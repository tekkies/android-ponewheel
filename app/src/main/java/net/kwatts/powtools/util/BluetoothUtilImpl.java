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
import android.widget.Toast;
import android.databinding.ObservableField;

import net.kwatts.powtools.App;
import net.kwatts.powtools.BuildConfig;
import net.kwatts.powtools.MainActivity;
import net.kwatts.powtools.model.IUnlocker;
import net.kwatts.powtools.model.OWDevice;
import net.kwatts.powtools.model.Session;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.security.MessageDigest;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.security.DigestInputStream;

import de.artcom.hsm.Action;
import de.artcom.hsm.State;
import de.artcom.hsm.StateMachine;
import de.artcom.hsm.Sub;
import de.artcom.hsm.TransitionKind;
import timber.log.Timber;
import uk.co.tekkies.hsm.plantuml.PlantUmlBuilder;
import uk.co.tekkies.hsm.plantuml.PlantUmlUrlEncoder;

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

    //TODO: decouple this crap from the UI/MainActivity
    @Override
    public void init(MainActivity mainActivity, OWDevice mOWDevice) {


        this.mainActivity = mainActivity;
        this.mContext = mainActivity.getApplicationContext();
        this.mOWDevice = mOWDevice;

        this.mBluetoothAdapter = ((BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();

        connectionEnabledStateMachineBuilder = new ConnectionEnabledStateMachineBuilder(this);
        stateMachine = connectionEnabledStateMachineBuilder.build();

        String cacheDir = mainActivity.getCacheDir().getAbsolutePath() + File.separator + "stateDiagram";
        diagramCache = new DiagramCache(cacheDir, stateMachine)
                .ensurePathExists()
                .fill(this);

        updateStateDiagram();
        Timber.i("Initial state: %s", stateMachine.getAllActiveStates());

        //final BluetoothManager manager = (BluetoothManager) mainActivity.getSystemService(Context.BLUETOOTH_SERVICE);
        //assert manager != null;
        //mBluetoothAdapter = manager.getAdapter();
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


        List<State> allActiveStates = stateMachine.getAllActiveStates();
        State currentState = allActiveStates.get(allActiveStates.size() - 1);
        String sourcePath = diagramCache.getDiagramFilePath(currentState);
        File sdFolder = Environment.getExternalStorageDirectory();
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

    private void handleStateMachineEvent(String event, HashMap<String, Object> payload) {
        stateMachine.handleEvent(event, payload);
        logTransition(event);
        updateStateDiagram();
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
        handleStateMachineEvent(ConnectionEnabledStateMachineBuilder.DISABLE_CONNECTION);
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
        handleStateMachineEvent(ConnectionEnabledStateMachineBuilder.ENABLE_CONNECTION);
        mainActivity.invalidateOptionsMenu();
    }


    @Override
    public void disconnect() {
        handleStateMachineEvent(ConnectionEnabledStateMachineBuilder.DISABLE_CONNECTION);
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
        return stateMachine.getAllActiveStates().contains(connectionEnabledStateMachineBuilder.enabled);
    }

    private class ConnectionEnabledStateMachineBuilder {

        public static final String ENABLE_CONNECTION = "Enable connection";
        public static final String DISABLE_CONNECTION = "Disable connection";

        public State enabled;
        public DisabledState disabled;


        public ConnectionEnabledStateMachineBuilder(BluetoothUtilImpl bluetoothUtil) {

        }

        private StateMachine build() {

            disabled = new DisabledState();


            enabled = new AdapterEnabledStateMachineBuilder().build();

            disabled.addHandler(ENABLE_CONNECTION, enabled, TransitionKind.External);
            enabled.addHandler(DISABLE_CONNECTION, disabled, TransitionKind.External);

            StateMachine stateMachine = new StateMachine(disabled, enabled);
            stateMachine.init();
            return stateMachine;
        }


        private class DisabledState extends State {
            public static final String ID = "Connection Disabled";

            public DisabledState() {
                super(ID);
            }

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
            public static final String NOT_ONEWHEEL = "Not Onewheel";
            public static final String TBC = "TBC";

            public State build() {

                InitState init = new InitState();
                State adapterDisabled = new State(ADAPTER_DISABLED);
                ConnectionStateMachine connectionStateMachine = new ConnectionStateMachine();
                State adapterEnabledState = new Sub(ADAPTER_ENABLED, connectionStateMachine.createConnectionStateMachine());
                State notOnewheel = new State("Not Onewheel");


                adapterEnabledState.addHandler(ADAPTER_DISABLED, adapterDisabled, TransitionKind.External);
                adapterEnabledState.addHandler(NOT_ONEWHEEL, notOnewheel, TransitionKind.External);

                adapterDisabled.addHandler(ADAPTER_ENABLED, adapterEnabledState, TransitionKind.External);

                init.addHandler(ADAPTER_ENABLED, adapterEnabledState, TransitionKind.External);
                init.addHandler(ADAPTER_DISABLED, adapterDisabled, TransitionKind.External);

                return new ConnectionEnabledState(new StateMachine(init, adapterDisabled, adapterEnabledState, notOnewheel));
            }

            private class InitState extends State {
                public static final String ID = "Init";

                public InitState() {
                    super(ID);
                }

            }
        }
    }

    class ConnectionStateMachine {


        public static final String ONEWHEEL_FOUND = "Onewheel found";
        private ScanningState scanningState;
        private State tbcState;


        public StateMachine createConnectionStateMachine() {
            scanningState = new ScanningState();

            State discoverServices = new DiscoverSericesStateBuilder().build();
            scanningState.addHandler(ONEWHEEL_FOUND, discoverServices, TransitionKind.External);

            tbcState = new State(ConnectionEnabledStateMachineBuilder.AdapterEnabledStateMachineBuilder.TBC);
            discoverServices.addHandler(ConnectionEnabledStateMachineBuilder.AdapterEnabledStateMachineBuilder.TBC, tbcState, TransitionKind.External);

            return new StateMachine(scanningState, discoverServices, tbcState);
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

                        handleStateMachineEvent(DiscoverSericesStateBuilder.GATT_CONNECT_FAIL);


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
                        handleStateMachineEvent(ConnectionEnabledStateMachineBuilder.AdapterEnabledStateMachineBuilder.NOT_ONEWHEEL);
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
                    //AJWOZ scanLeDevice(false);

                    // Stability updates per https://github.com/ponewheel/android-ponewheel/issues/86#issuecomment-460033659
                    // Step 1: In OnServicesDiscovered, JUST read the firmware version.
                    Timber.d("Stability Step 1: Only reading the firmware version!");
                    //new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            BluetoothUtilImpl.this.mGatt.readCharacteristic(BluetoothUtilImpl.this.owGatService.getCharacteristic(UUID.fromString(OWDevice.OnewheelCharacteristicFirmwareRevision)));
                        }
                    }, 500);

                }

                @Override
                public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic c, int status) {
                    String characteristic_uuid = c.getUuid().toString();
                    Timber.d("BluetoothGattCallback.onCharacteristicRead: CharacteristicUuid=" +
                            characteristic_uuid +
                            ",status=" + status +
                            ",isGemini=" + isGemini());
                    if (characteristicReadQueue.size() > 0) {
                        characteristicReadQueue.remove();
                    }

                    // Stability Step 2: In OnCharacteristicRead, if the value is of the char firmware version, parse it's value.
                    // If its >= 4034, JUST write the descriptor for the Serial Read characteristic to Enable notifications,
                    // and set notify to true with gatt. Otherwise its Andromeda or lower and we can call the method to
                    // read & notify all the characteristics we want. (Although I learned doing this that some android devices
                    // have a max of 12 notify characteristics at once for some reason. At least I'm pretty sure.)
                    // I also set a class-wide boolean value isGemini to true here so I don't have to keep checking if its Andromeda
                    // or Gemini later on.
                    if (characteristic_uuid.equals(OWDevice.OnewheelCharacteristicFirmwareRevision)) {
                        Timber.d("We have the firmware revision! Checking version.");
                        int firmwareVersion = unsignedShort(c.getValue());
                        Session session = Session.Create(firmwareVersion);
                        IUnlocker unlocker = session.getUnlocker();
                        unlocker.start(getInstance(), owGatService, gatt);
                    } else if (characteristic_uuid.equals(OWDevice.OnewheelCharacteristicRidingMode)) {
                        Timber.d("Got ride mode from the main UI thread:" + c.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1));
                    }

                    if (BuildConfig.DEBUG) {
                        byte[] v_bytes = c.getValue();
                        StringBuilder sb = new StringBuilder();
                        for (byte b : c.getValue()) {
                            sb.append(String.format("%02x", b));
                        }
                        Timber.d("HEX %02x: " + sb);
                        Timber.d("Arrays.toString() value: " + Arrays.toString(v_bytes));
                        Timber.d("String value: " + c.getStringValue(0));
                        Timber.d("Unsigned short: " + unsignedShort(v_bytes));
                        Timber.d("getIntValue(FORMAT_UINT8,0) " + c.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0));
                        Timber.d("getIntValue(FORMAT_UINT8,1) " + c.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1));
                    }

                    mOWDevice.processUUID(c);

                    mOWDevice.setBatteryRemaining(mainActivity);

                    // Callback to make sure the queue is drained

                    if (characteristicReadQueue.size() > 0) {
                        gatt.readCharacteristic(characteristicReadQueue.element());
                    }

                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic c) {
                    //Timber.d( "BluetoothGattCallback.onCharacteristicChanged: CharacteristicUuid=" + c.getUuid().toString());

                    // https://github.com/ponewheel/android-ponewheel/issues/86
                    //if (isGemini && (c.getUuid().toString().equals(OWDevice.OnewheelCharacteristicUartSerialRead))) {
                    // Step 4: In OnCharacteristicChanged, if isGemini and characteristic is serial read,
                    // do the gemini hash crap and stuff and setNotify for serial read to false.
                    //    Timber.d("Stability Step 4: Gemini unlock & setting setNotify for serial read to false");
                    //    unlockKeyGemini(gatt,c.getValue());

                    //}
                    BluetoothGatt bluetoothGatt = gatt;
                    BluetoothGattCharacteristic bluetoothGattCharacteristic = c;

                    if (isGemini() && (c.getUuid().toString().equals(OWDevice.OnewheelCharacteristicUartSerialRead))) {
                        try {
                            Timber.d("Setting up inkey!");
                            inkey.write(c.getValue());
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
                        }
                    }


                    mOWDevice.processUUID(bluetoothGattCharacteristic);

                    mOWDevice.setBatteryRemaining(mainActivity);
                }


                @Override
                public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic c, int status) {
                    Timber.i("onCharacteristicWrite: " + status + ", CharacteristicUuid=" + c.getUuid().toString());
                    // Step 5: In OnCharacteristicWrite, if isGemini & characteristic is Serial Write, NOW setNotify
                    // and read all the characteristics you want. its also only now that I start the
                    // repeated handshake clock thing but I don't think it really matters, this all happens pretty quick.
                    if (isGemini() && (c.getUuid().toString().equals(OWDevice.OnewheelCharacteristicUartSerialWrite))) {
                        Timber.d("Step 5: Gemini and serial write, kicking off all the read and notifies...");
                        whenActuallyConnected();
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

                    // Step 3: In OnDescriptorWrite, if isGemini and the characteristic descriptor that was
                    // written was Serial Write, then trigger the byte stream by writing the firmware version
                    // onto itself.
            /*
            if (isGemini && (descriptor.equals(OWDevice.OnewheelCharacteristicUartSerialWrite))) {
                Timber.d("Step 3: Is Gemini, writing the descriptor onto itself");
                gatt.writeDescriptor(descriptor);
            }
            */
                }


            };

        }

        private class DiscoverSericesStateBuilder {

            public static final String GATT_CONNECTED = "Gatt Connected";
            public static final String GATT_CONNECT_FAIL = "Gatt Connect Fail";

            public State build() {

                ConnectingState connectingState = new ConnectingState();
                DiscoveringServicesState discoveringServicesState = new DiscoveringServicesState();
                ServicesDiscoveredState servicesDiscoveredState = new ServicesDiscoveredState();
                State gattConnectFailed = new State("Gatt connect failed");

                connectingState.addHandler(GATT_CONNECTED, discoveringServicesState, TransitionKind.External);
                connectingState.addHandler(GATT_CONNECT_FAIL, gattConnectFailed, TransitionKind.External);

                DiscoverSericesState discoverSericesState = new DiscoverSericesState(
                        connectingState,
                        discoveringServicesState,
                        servicesDiscoveredState,
                        gattConnectFailed);


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

            private class ServicesDiscoveredState extends State {
                public ServicesDiscoveredState() {
                    super("Sevices Discovered");
                }
            }

            private void discoverGattServices(Map<String, Object> mPayload1) {
                BluetoothGatt gatt = (BluetoothGatt) mPayload1.get(BluetoothGatt.class.getSimpleName());
                gatt.discoverServices();
            }
        }
    }

}
