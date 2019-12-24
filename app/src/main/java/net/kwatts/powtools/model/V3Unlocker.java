package net.kwatts.powtools.model;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.os.Handler;
import android.os.Looper;

import net.kwatts.powtools.util.BluetoothUtil;
import net.kwatts.powtools.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import timber.log.Timber;

class V3Unlocker implements IUnlocker {

    public static final String myUnlockCode = "my-unlock-code";

    @Override
    public boolean isGemini() {
        return true;
    }

    @Override
    public void start(BluetoothUtil instance, BluetoothGattService owGatService, BluetoothGatt gatt) {
        if(IsKeySensible()) {
            sendTheKeyDelayed(owGatService, gatt);
        } else {
            Timber.w("Key '%s' does not look right", myUnlockCode);
        }

    }

    private void sendTheKeyDelayed(BluetoothGattService owGatService, BluetoothGatt gatt) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            public void run() {
                sendTheKey(owGatService, gatt);
            }
        }, 500);
    }

    private boolean IsKeySensible() {
        return myUnlockCode.length() ==40;
    }

    private void sendTheKey(BluetoothGattService owGatService, BluetoothGatt gatt) {
        BluetoothGattCharacteristic lc = owGatService.getCharacteristic(UUID.fromString(OWDevice.OnewheelCharacteristicUartSerialWrite));
        Timber.i( "Send HardCoded key----------------------------------------------------");
        ByteArrayOutputStream outkey = new ByteArrayOutputStream();
        try {
            outkey.write(Util.StringToByteArrayFastest(myUnlockCode));
        } catch (IOException e) {
            e.printStackTrace();
        }
        lc.setValue(outkey.toByteArray());
        if (!gatt.writeCharacteristic(lc)) {
            BluetoothGattCharacteristic bluetoothGattCharacteristic2 = lc;
            //sendKey = true;
        } else {
            //sendKey = false;
            //gatt.setCharacteristicNotification(bluetoothGattCharacteristic, false);
        }
        outkey.reset();
    }
}
