package net.kwatts.powtools.model;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import net.kwatts.powtools.util.BluetoothUtil;

import timber.log.Timber;

class V1Unlocker implements IUnlocker {
    @Override
    public boolean isGemini() {
        return false;
    }

    @Override
    public void onCharacteristicRead(BluetoothUtil bluetoothUtil, BluetoothGattService owGatService, BluetoothGatt gatt) {
        Timber.d("It's before Gemini, likely Andromeda - calling read and notify characteristics");
        bluetoothUtil.whenActuallyConnected();
    }

    @Override
    public void onDescriptorWrite(BluetoothGattService owGatService, BluetoothGatt gatt, BluetoothGattDescriptor descriptor) {

    }
}
