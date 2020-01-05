package net.kwatts.powtools.BluetoothStateMachine;

import java.util.HashMap;
import java.util.Map;

public class PayloadUtil {
    private Map<String, Object> payload;

    public PayloadUtil() {
        payload = new HashMap<>();
    }

    public PayloadUtil(Map<String, Object> payload) {
        this.payload = new HashMap<>(payload);
    }

    public <T> T getPayload(Class<T> type) {
        String key = type.getSimpleName();
        T result = (T) payload.get(key);
        return result;
    }

    public PayloadUtil add(Object item) {
        String key = item.getClass().getSimpleName();
        return add(key, item);
    }

    public PayloadUtil add(String key, Object item) {
        payload.put(key, item);
        return this;
    }

    public Map<String, Object> build() {
        return payload;
    }
}
