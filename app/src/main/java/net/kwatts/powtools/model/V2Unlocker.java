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
        Timber.d("It's Gemini!");
        Timber.d("Stability Step 2.1: JUST write the descriptor for the Serial Read characteristic to Enable notifications");
        BluetoothGattCharacteristic gC = owGatService.getCharacteristic(UUID.fromString(OWDevice.OnewheelCharacteristicUartSerialRead));
        gatt.setCharacteristicNotification(gC, true);
        Timber.d("and set notify to true with gatt...");
        BluetoothGattDescriptor descriptor = gC.getDescriptor(UUID.fromString(OWDevice.OnewheelConfigUUID));
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);
    }
}
