package net.kwatts.powtools.model;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattService;

import net.kwatts.powtools.App;
import net.kwatts.powtools.util.BluetoothUtil;
import net.kwatts.powtools.util.Util;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

import timber.log.Timber;

public class V3Unlocker implements IUnlocker {

    private String address;

    public V3Unlocker(String address) {
        this.address = address.replace(":", "").toLowerCase();
    }

    public V3Unlocker() {

    }

    @Override
    public boolean isGemini() {
        return true;
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


    public byte[] getKey() {
        String key = null;
        HashMap<String, String> map = buildKeyMap();
        if(map.containsKey(address)) {
            key = map.get(address);
        }
        return Util.StringToByteArrayFastest(key);
    }

    @Override
    public void start(BluetoothUtil instance, BluetoothGattService owGatService, BluetoothGatt gatt) {

    }
}
