package net.kwatts.powtools.connection.states;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.le.ScanResult;

import net.kwatts.powtools.PayloadUtil;
import net.kwatts.powtools.connection.BluetoothStateMachine;
import net.kwatts.powtools.util.Util;

import de.artcom.hsm.Action;
import de.artcom.hsm.State;
import timber.log.Timber;

public class ConnectingState extends State {
    private BluetoothStateMachine bluetoothStateMachine;

    public ConnectingState(BluetoothStateMachine bluetoothStateMachine) {
        super("Connecting");
        this.bluetoothStateMachine = bluetoothStateMachine;
        onEnter(new TryToConnect());
    }


    private class TryToConnect extends Action {
        @Override
        public void run() {
            PayloadUtil payloadUtil = new PayloadUtil(mPayload);
            ScanResult result = payloadUtil.getPayload(ScanResult.class);
            BluetoothGattCallback bluetoothGattCallback = bluetoothStateMachine.states.connectedState.bluetoothGattCallback;
            BluetoothDevice device = result.getDevice();
            Timber.d("Address: %s, Name: %s", Util.coalesce(device.getAddress(), "NULL"), Util.coalesce(device.getName(), "NULL"));
            device.connectGatt(bluetoothStateMachine.context, false, bluetoothGattCallback);
        }
    }
}
