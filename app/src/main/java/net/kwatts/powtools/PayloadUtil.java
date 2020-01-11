package net.kwatts.powtools;

import java.util.HashMap;
import java.util.Map;

public class PayloadUtil {
    public static final String HINT = "HINT";
    private Map<String, Object> payload;

    public PayloadUtil() {
        payload = new HashMap<>();
    }

    public PayloadUtil(Map<String, Object> payload) {
        this.payload = new HashMap<>(payload);
    }

    public <T> T getPayload(Class<T> type) {
        String key = type.getSimpleName();
        T result = getPayload(key);
        return result;
    }

    public <T> T getPayload(String key) {
        return (T) payload.get(key);
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
