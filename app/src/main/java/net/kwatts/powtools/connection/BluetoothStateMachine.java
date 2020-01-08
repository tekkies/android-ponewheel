package net.kwatts.powtools.connection;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;

import net.kwatts.powtools.connection.states.DisabledState;
import net.kwatts.powtools.util.BluetoothUtilImpl;

import java.util.Map;

import de.artcom.hsm.State;

public class  BluetoothStateMachine {

    public final States states;
    public final Events events;
    private final BluetoothUtilImpl bluetoothUtil;
    private Context context;

    public BluetoothStateMachine(Context context, BluetoothUtilImpl bluetoothUtil) {
        this.context = context;
        this.bluetoothUtil = bluetoothUtil;
        states = new States();
        events = new Events();
    }

    public void handleStateMachineEvent(String onewheelFound, Map<String, Object> build) {
        bluetoothUtil.handleStateMachineEvent(onewheelFound, build);
    }

    public class States {
        public DisabledState disabled;
        public State enabled;
        public State gattCongestedState;
    }

    public class Events {
        public static final String DISABLE_ADAPTER = "Disable Adapter";
        public static final String ENABLE_CONNECTION = "Enable Connection";
        public static final String DISABLE_CONNECTION = "Disable connection";
        public static final String GATT_CONGESTED_ERROR = "GATT Congested Error";
        public static final String GATT_CONNECT_OTHER_ERROR = "Gatt Connect Other Error";
        public static final String ONEWHEEL_FOUND = "Device found";
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

