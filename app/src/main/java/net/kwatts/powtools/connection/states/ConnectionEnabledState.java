package net.kwatts.powtools.connection.states;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import net.kwatts.powtools.connection.BluetoothStateMachine;
import net.kwatts.powtools.util.BluetoothUtilImpl;

import de.artcom.hsm.Action;
import de.artcom.hsm.State;
import de.artcom.hsm.StateMachine;
import de.artcom.hsm.Sub;

public class ConnectionEnabledState extends Sub {
    public static final String ID = "Connection Enabled";

    BroadcastReceiver receiver;
    private Context context;
    private BluetoothStateMachine bluetoothStateMachine;

    public ConnectionEnabledState(BluetoothStateMachine bluetoothStateMachine, State initialState, State... states) {
        super(ID, initialState, states);
        this.bluetoothStateMachine = bluetoothStateMachine;
        context = bluetoothStateMachine.context;
        onEnter(new ListenForBluetoothToggle());
        onExit(new StopListeningForBluetoothToggle());
    }

    private class StopListeningForBluetoothToggle extends Action {
        @Override
        public void run() {
            context.unregisterReceiver(receiver);
        }
    }

    private class ListenForBluetoothToggle extends Action {
        @Override
        public void run() {
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            receiver = setupAdapterListener();
            context.registerReceiver(receiver, filter);

            if (bluetoothStateMachine.getBluetoothAdapter().enable()) {
                bluetoothStateMachine.handleStateMachineEvent(bluetoothStateMachine.events.ENABLE_ADAPTER);
            } else {
                bluetoothStateMachine.handleStateMachineEvent(bluetoothStateMachine.events.DISABLE_ADAPTER);
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
                            bluetoothStateMachine.handleStateMachineEvent(bluetoothStateMachine.events.DISABLE_ADAPTER);
                            break;
                        case BluetoothAdapter.STATE_TURNING_OFF:
                            //setButtonText("Turning Bluetooth off...");
                            break;
                        case BluetoothAdapter.STATE_ON:
                            bluetoothStateMachine.handleStateMachineEvent(bluetoothStateMachine.events.ENABLE_ADAPTER);
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

