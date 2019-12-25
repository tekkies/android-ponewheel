package net.kwatts.powtools.model;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattService;
import android.os.Handler;

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
}
