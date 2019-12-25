package net.kwatts.powtools.model;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import net.kwatts.powtools.util.BluetoothUtil;

public interface IUnlocker {
    boolean isGemini();

    void onCharacteristicRead(BluetoothUtil instance, BluetoothGattService owGatService, BluetoothGatt gatt);

    void onDescriptorWrite(BluetoothGattService owGatService, BluetoothGatt gatt, BluetoothGattDescriptor descriptor);

    void onCharacteristicWrite(BluetoothUtil instance, BluetoothGatt gatt, BluetoothGattCharacteristic bluetoothGattCharacteristic);

    void onCharacteristicChanged(BluetoothGattCharacteristic c, BluetoothGattService owGatService, BluetoothGatt gatt);
}
