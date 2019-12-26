package net.kwatts.powtools.model;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.os.Handler;

import net.kwatts.powtools.util.BluetoothUtil;
import net.kwatts.powtools.util.BluetoothUtilImpl;

import java.util.UUID;

import timber.log.Timber;

public class UnlockerFactory {
    public static IUnlocker GetUnlocker(int firmwareVersion) {
        IUnlocker unlocker=null;
        if(firmwareVersion <= 4033)
        {
            unlocker = new V1Unlocker();
        }
        if(unlocker == null && firmwareVersion <= 4141)
        {
            unlocker = new V2Unlocker();
        }
        if(unlocker == null)
        {
            unlocker = new V3Unlocker();
        }
        return unlocker;
    }

    public static void requestFirmwareVersion(Handler handler, BluetoothGatt bluetoothGatt, BluetoothGattService bluetoothGattService) {
        // Stability updates per https://github.com/ponewheel/android-ponewheel/issues/86#issuecomment-460033659
        // Step 1: In OnServicesDiscovered, JUST read the firmware version.
        Timber.d("Stability Step 1: Only reading the firmware version!");
        //new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
        handler.postDelayed(new Runnable() {
            public void run() {
                bluetoothGatt.readCharacteristic(bluetoothGattService.getCharacteristic(UUID.fromString(OWDevice.OnewheelCharacteristicFirmwareRevision)));
            }
        }, 500);
    }

    public static void onCharacteristicRead(BluetoothUtil bluetoothUtil, BluetoothGattService owGatService, BluetoothGatt gatt, BluetoothGattCharacteristic c) {
        // Stability Step 2: In OnCharacteristicRead, if the value is of the char firmware version, parse it's value.
        // If its >= 4034, JUST write the descriptor for the Serial Read characteristic to Enable notifications,
        // and set notify to true with gatt. Otherwise its Andromeda or lower and we can call the method to
        // read & notify all the characteristics we want. (Although I learned doing this that some android devices
        // have a max of 12 notify characteristics at once for some reason. At least I'm pretty sure.)
        // I also set a class-wide boolean value isGemini to true here so I don't have to keep checking if its Andromeda
        // or Gemini later on.


        String characteristic_uuid = c.getUuid().toString();
        if (characteristic_uuid.equals(OWDevice.OnewheelCharacteristicFirmwareRevision)) {
            Timber.d("We have the firmware revision! Checking version.");
            int firmwareVersion = unsignedShort(c.getValue());
            Session session = Session.Create(firmwareVersion);
            IUnlocker unlocker = session.getUnlocker();
            unlocker.onCharacteristicRead(bluetoothUtil, owGatService, gatt);
        }
    }

    public static int unsignedShort(byte[] var0) {
        // Short.valueOf(ByteBuffer.wrap(v_bytes).getShort()) also works
        int var1;
        if(var0.length < 2) {
            var1 = -1;
        } else {
            var1 = (unsignedByte(var0[0]) << 8) + unsignedByte(var0[1]);
        }

        return var1;
    }

    public static int unsignedByte(byte var0) {
        return var0 & 255;
    }

}
