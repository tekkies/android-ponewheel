package net.kwatts.powtools.BluetoothStateMachine;

import net.kwatts.powtools.util.InkeyCollator;

import java.util.HashMap;

public class Event {

    private static HashMap<String, Object> newSimplePayload(Object result) {
        HashMap<String, Object> payload = new HashMap<String, Object>(1);
        payload.put(result.getClass().getSimpleName(), result);
        return payload;
    }


    public static class InkeyFound extends Event {
        public static final String ID = "Inkey Found";

        public static HashMap<String, Object> newPayload(InkeyCollator inkeyCollator) {
            return Event.newSimplePayload(inkeyCollator);
        }



    }
}