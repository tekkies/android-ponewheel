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
    public void onCharacteristicRead(BluetoothUtil instance, BluetoothGattService owGatService, BluetoothGatt gatt) {
        Timber.d("It's Gemini!");
        Timber.d("Stability Step 2.1: JUST write the descriptor for the Serial Read characteristic to Enable notifications");
        BluetoothGattCharacteristic gC = owGatService.getCharacteristic(UUID.fromString(OWDevice.OnewheelCharacteristicUartSerialRead));
        gatt.setCharacteristicNotification(gC, true);
        Timber.d("and set notify to true with gatt...");
        BluetoothGattDescriptor descriptor = gC.getDescriptor(UUID.fromString(OWDevice.OnewheelConfigUUID));
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);
    }

    @Override
    public void onDescriptorWrite(BluetoothGattService owGatService, BluetoothGatt gatt, BluetoothGattDescriptor descriptor) {
        if (isGemini() && descriptor.getCharacteristic().getUuid().toString().equals(OWDevice.OnewheelCharacteristicUartSerialRead)) {
            Timber.d("Stability Step 3: if isGemini and the characteristic descriptor that was written was Serial Write" +
                    "then trigger the 20 byte input key over multiple serial ble notification stream by writing the firmware version onto itself");
            gatt.writeCharacteristic(owGatService.getCharacteristic(UUID.fromString(OWDevice.OnewheelCharacteristicFirmwareRevision)));
        }
    }

    @Override
    public void onCharacteristicWrite(BluetoothUtil bluetoothUtil, BluetoothGatt gatt, BluetoothGattCharacteristic bluetoothGattCharacteristic) {
        // Step 5: In OnCharacteristicWrite, if isGemini & characteristic is Serial Write, NOW setNotify
        // and read all the characteristics you want. its also only now that I start the
        // repeated handshake clock thing but I don't think it really matters, this all happens pretty quick.
        if (isGemini() && (bluetoothGattCharacteristic.getUuid().toString().equals(OWDevice.OnewheelCharacteristicUartSerialWrite))) {
            Timber.d("Step 5: Gemini and serial write, kicking off all the read and notifies...");
            bluetoothUtil.whenActuallyConnected();
        }
    }
}
