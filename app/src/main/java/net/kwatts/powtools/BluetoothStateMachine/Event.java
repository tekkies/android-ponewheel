package net.kwatts.powtools.BluetoothStateMachine;

import net.kwatts.powtools.util.InkeyCollator;

import java.util.HashMap;

public class Event {

    public static HashMap<String, Object> newSimplePayload(Object result) {
        HashMap<String, Object> payload = new HashMap<String, Object>(1);
        payload.put(result.getClass().getSimpleName(), result);
        return payload;
    }


    public static class InkeyFoundV2 extends Event {
        public static final String ID = "Inkey Found (V2)";
    }

    public static class InkeyFoundV3 extends Event {
        public static final String ID = "Inkey Found (V3)";
    }

    public static class GeminiFirmware {
        public static final String ID = "Gemini Firmware";
        public static final String FIRMWARE_VERSION = "Firmware Version";
    }

}