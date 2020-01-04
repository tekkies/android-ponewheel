package net.kwatts.powtools.BluetoothStateMachine;

import java.util.HashMap;
import java.util.Map;

public class PayloadBuilder {
    private Map<String, Object> payload;

    public PayloadBuilder() {
        payload = new HashMap<>();

    }

    public PayloadBuilder(Map<String, Object> payload) {
        this.payload = payload;
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

    public Map<String, Object> build() {
        return payload;
    }
}
