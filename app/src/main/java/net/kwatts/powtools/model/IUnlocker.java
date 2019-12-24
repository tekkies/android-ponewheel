package net.kwatts.powtools.model;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattService;

import net.kwatts.powtools.util.BluetoothUtil;

public interface IUnlocker {
    boolean isGemini();

    void start(BluetoothUtil instance, BluetoothGattService owGatService, BluetoothGatt gatt);
}
