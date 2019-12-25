package net.kwatts.powtools.model;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.os.Handler;
import android.os.Looper;

import net.kwatts.powtools.App;
import net.kwatts.powtools.util.BluetoothUtil;
import net.kwatts.powtools.util.Util;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

import timber.log.Timber;

class V3Unlocker implements IUnlocker {

    @Override
    public boolean isGemini() {
        return true;
    }

    @Override
    public void onCharacteristicRead(BluetoothUtil instance, BluetoothGattService owGatService, BluetoothGatt gatt) {
        String key = lookupKey(gatt);
        if(IsKeySensible(key)) {
            sendTheKeyDelayed(owGatService, gatt, key);
        } else {
            Timber.w("Key '%s' does not look right", key);
        }
    }

    private String lookupKey(BluetoothGatt gatt) {
        String key = "";
        String address = gatt.getDevice().getAddress().replace(":", "").toLowerCase();
        HashMap<String, String> map = buildKeyMap();
        if(map.containsKey(address)) {
            key = map.get(address);
        } else {
            Timber.w("Unable to find key for address %s", address);
        }
        return key;
    }

    @NotNull
    private HashMap<String, String> buildKeyMap() {
        String v3UnlockKeys = App.INSTANCE.getSharedPreferences().getV3UnlockKeys();
        HashMap<String,String> map = new HashMap<String, String>();
        String pairs[] = v3UnlockKeys.split(",");
        for(int i=0;i<pairs.length;i++){
            String pair[] = pairs[i].split("=");
            String keyAddress = pair[0].replace(":", "").toLowerCase().trim();
            String keyValue = pair[1].trim();
            Timber.d("Key loaded: %s=%s", keyAddress, keyValue);
            map.put(keyAddress, keyValue);
        }
        return map;
    }

    private void sendTheKeyDelayed(BluetoothGattService owGatService, BluetoothGatt gatt, String key) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            public void run() {
                sendTheKey(owGatService, gatt, key);
            }
        }, 500);
    }

    private boolean IsKeySensible(String key) {
        return key.length() == 40;
    }

    private void sendTheKey(BluetoothGattService owGatService, BluetoothGatt gatt, String key) {
        BluetoothGattCharacteristic lc = owGatService.getCharacteristic(UUID.fromString(OWDevice.OnewheelCharacteristicUartSerialWrite));
        Timber.i( "Send HardCoded key----------------------------------------------------");
        ByteArrayOutputStream outkey = new ByteArrayOutputStream();
        try {
            outkey.write(Util.StringToByteArrayFastest(key));
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
