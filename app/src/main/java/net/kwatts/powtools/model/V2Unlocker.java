package net.kwatts.powtools.model;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import net.kwatts.powtools.util.BluetoothUtil;
import net.kwatts.powtools.util.Util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;

import timber.log.Timber;

class V2Unlocker implements IUnlocker {
    @Override
    public boolean isGemini() {
        return true;
    }

    public ByteArrayOutputStream inkey = new ByteArrayOutputStream();
    public boolean sendKey = true;


    @Override
    public void onCharacteristicRead(BluetoothUtil instance, BluetoothGattService owGatService, BluetoothGatt gatt) {
        Timber.d("Stability Step 2.1: JUST write the descriptor for the Serial Read characteristic to Enable notifications");
        BluetoothGattCharacteristic gC = owGatService.getCharacteristic(UUID.fromString(OWDevice.OnewheelCharacteristicUartSerialRead));
        gatt.setCharacteristicNotification(gC, true);
        Timber.d("and set notify to true with gatt...");
        BluetoothGattDescriptor descriptor = gC.getDescriptor(UUID.fromString(OWDevice.OnewheelConfigUUID));
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);
    }

    @Override
    public void onDescriptorWrite(BluetoothGattService owGatService, BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
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

    @Override
    public void onCharacteristicChanged(BluetoothGattCharacteristic c, BluetoothGattService owGatService, BluetoothGatt gatt) {

        BluetoothGatt bluetoothGatt = gatt;
        BluetoothGattCharacteristic bluetoothGattCharacteristic = c;

        if (isGemini() && (c.getUuid().toString().equals(OWDevice.OnewheelCharacteristicUartSerialRead))) {
            try {
                inkey.write(c.getValue());
                Timber.d("Setting up inkey! (%s collected)", inkey.toByteArray().length);
                if (inkey.toByteArray().length >= 20 && sendKey) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("GEMINI Step #2: convert inkey=");
                    sb.append(Arrays.toString(inkey.toByteArray()));
                    Timber.d("GEMINI:" +  sb.toString());
                    ByteArrayOutputStream outkey = new ByteArrayOutputStream();
                    outkey.write(Util.StringToByteArrayFastest("43:52:58"));
                    byte[] arrayToMD5_part1 = Arrays.copyOfRange(inkey.toByteArray(), 3, 19);
                    byte[] arrayToMD5_part2 = Util.StringToByteArrayFastest("D9255F0F23354E19BA739CCDC4A91765");
                    ByteBuffer arrayToMD5 = ByteBuffer.allocate(arrayToMD5_part1.length + arrayToMD5_part2.length);
                    arrayToMD5.put(arrayToMD5_part1);
                    arrayToMD5.put(arrayToMD5_part2);
                    MessageDigest localMessageDigest = MessageDigest.getInstance("MD5");
                    DigestInputStream digestInputStream = new DigestInputStream(new ByteArrayInputStream(arrayToMD5.array()), localMessageDigest);
                    while (digestInputStream.read(new byte[]{101}) != -1) {
                    }
                    digestInputStream.close();
                    outkey.write(localMessageDigest.digest());
                    byte checkByte = 0;
                    for (byte b : outkey.toByteArray()) {
                        checkByte = (byte) (b ^ checkByte);
                    }
                    outkey.write(checkByte);
                    StringBuilder sb2 = new StringBuilder();
                    byte[] bArr = arrayToMD5_part1;
                    sb2.append("GEMINI Step #3: write outkey=");
                    sb2.append(Arrays.toString(outkey.toByteArray()));
                    Timber.d("GEMINI" +  sb2.toString());
                    BluetoothGattCharacteristic lc = owGatService.getCharacteristic(UUID.fromString(OWDevice.OnewheelCharacteristicUartSerialWrite));
                    lc.setValue(outkey.toByteArray());
                    if (!bluetoothGatt.writeCharacteristic(lc)) {
                        BluetoothGattCharacteristic bluetoothGattCharacteristic2 = lc;
                        sendKey = true;
                    } else {
                        sendKey = false;
                        bluetoothGatt.setCharacteristicNotification(bluetoothGattCharacteristic, false);
                    }
                    outkey.reset();
                }
            } catch (Exception e) {
                StringBuilder sb3 = new StringBuilder();
                sb3.append("Exception with GEMINI obfuckstation:");
                sb3.append(e.getMessage());
                Timber.d("GEMINI" + sb3.toString());
            }
        }

    }
}
