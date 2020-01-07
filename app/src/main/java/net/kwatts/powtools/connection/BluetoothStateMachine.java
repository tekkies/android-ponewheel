package net.kwatts.powtools.connection;

import net.kwatts.powtools.connection.states.DisabledState;
import net.kwatts.powtools.util.BluetoothUtilImpl;

import de.artcom.hsm.State;

public class  BluetoothStateMachine {

    public final States states;
    public final Events events;

    public BluetoothStateMachine() {
        states = new States();
        events = new Events();
    }

    public class States {
        public DisabledState disabled;
        public State enabled;
        public State gattCongestedState;
    }

    public class Events {
        public static final String ENABLE_CONNECTION = "Enable Connection";
        public static final String DISABLE_CONNECTION = "Disable connection";
        public static final String GATT_CONGESTED_ERROR = "GATT Congested Error";
        public static final String GATT_CONNECT_OTHER_ERROR = "Gatt Connect Other Error";
    }
}

