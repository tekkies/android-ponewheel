package net.kwatts.powtools.connection;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;

import com.google.common.base.Stopwatch;

import net.kwatts.powtools.PayloadUtil;
import net.kwatts.powtools.connection.states.ConnectingState;
import net.kwatts.powtools.connection.states.DisabledState;
import net.kwatts.powtools.util.BluetoothUtilImpl;

import java.util.Map;

import de.artcom.hsm.State;

public class  BluetoothStateMachine {

    public final States states;
    public final Events events;
    public final BluetoothUtilImpl bluetoothUtil;
    private Stopwatch stopwatch;
    public Context context;
    private int rssi;
    private TransitionLogger transitionLogger;

    public BluetoothStateMachine(Context context, BluetoothUtilImpl bluetoothUtil) {
        this.context = context;
        this.bluetoothUtil = bluetoothUtil;
        states = new States();
        events = new Events();
    }

    public void handleStateMachineEvent(String onewheelFound, Map<String, Object> build) {
        bluetoothUtil.handleStateMachineEvent(onewheelFound, build);
    }

    public void handleStateMachineEvent(String disableAdapter) {
        handleStateMachineEvent(disableAdapter, new PayloadUtil().build());
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
        if(stopwatch == null) {
            stopwatch = new Stopwatch();
        }
        stopwatch.reset().start();
    }

    public String getRssiString() {
        String rssi=null;
        if(stopwatch != null) {
            long elapsedMillis = stopwatch.elapsedMillis();
            if (elapsedMillis < 2000) {
                rssi = Integer.toString(this.rssi);
            }
        }
        return rssi;
    }

    public void logTransitionToFile(String event, Map<String, Object> payload) {
        if(transitionLogger == null) {
            transitionLogger = new TransitionLogger();
        }
        transitionLogger.log(event, payload);
    }

    public class States {
        public DisabledState disabled;
        public State enabled;
        public State gattCongestedState;
        public ConnectingState connectingState;
        public State gattConnectOtherFail;
        public BluetoothUtilImpl.ConnectionStateMachine.DiscoverSericesStateBuilder.DiscoveringServicesState discoveringServicesState;
        public BluetoothUtilImpl.ConnectionStateMachine.ConnectedState connectedState;
    }

    public class Events {
        public static final String DISABLE_ADAPTER = "Disable Adapter";
        public static final String ENABLE_ADAPTER = "Enable Adapter";
        public static final String ENABLE_CONNECTION = "Enable Connection";
        public static final String DISABLE_CONNECTION = "Disable connection";
        public static final String GATT_CONGESTED_ERROR = "GATT Congested Error";
        public static final String GATT_CONNECT_OTHER_ERROR = "Gatt Connect Other Error";
        public static final String ONEWHEEL_FOUND = "Device found";
        public static final String GATT_CONNECTS = "Gatt Connects";
    }

    public BluetoothAdapter getBluetoothAdapter() {
        BluetoothAdapter adapter = null;
        if(context != null) {
            BluetoothManager systemService = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            if(systemService != null) {
                adapter = systemService.getAdapter();
            }
        }
        return adapter;
    }
}

