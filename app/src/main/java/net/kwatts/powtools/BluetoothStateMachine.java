package net.kwatts.powtools;

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
    }

    public class Events {

    }
}

