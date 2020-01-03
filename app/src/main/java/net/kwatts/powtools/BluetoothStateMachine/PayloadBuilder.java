package net.kwatts.powtools.BluetoothStateMachine;

import android.bluetooth.BluetoothGatt;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import static android.icu.lang.UCharacter.GraphemeClusterBreak.T;

public class PayloadBuilder {
    private HashMap<String, Object> payload;

    public PayloadBuilder() {
        payload = new HashMap<>();

    }


    public static <T> T getPayload(Class<T> type, Map<String, Object> payload) {
        String key = type.getSimpleName();
        T result = (T) payload.get(key);
        return result;
    }


    public PayloadBuilder add(Object item) {
        payload.put(item.getClass().getSimpleName(), item);
        return this;
    }

    public HashMap<String, Object> build() {
        return payload;
    }
}
