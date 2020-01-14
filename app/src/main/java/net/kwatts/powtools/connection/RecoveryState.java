package net.kwatts.powtools.connection;

import net.kwatts.powtools.Event;
import net.kwatts.powtools.util.BluetoothUtilImpl;

import de.artcom.hsm.Action;
import de.artcom.hsm.State;

public class RecoveryState extends State {
    private BluetoothUtilImpl bluetoothUtil;

    public RecoveryState(BluetoothUtilImpl bluetoothUtil) {
        super("Recovery");
        this.bluetoothUtil = bluetoothUtil;
        onEnter(new Wait());
    }

    private class Wait extends Action {
        @Override
        public void run() {
            bluetoothUtil.setStateTimeout(Event.Timeout.ID, 2000);
        }
    }
}
