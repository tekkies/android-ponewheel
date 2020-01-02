package net.kwatts.powtools.model;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import net.kwatts.powtools.util.BluetoothUtil;

import java.util.UUID;

import timber.log.Timber;

class V2Unlocker implements IUnlocker {
    @Override
    public boolean isGemini() {
        return true;
    }

    @Override
    public void start(BluetoothUtil instance, BluetoothGattService owGatService, BluetoothGatt gatt) {

    }
}
