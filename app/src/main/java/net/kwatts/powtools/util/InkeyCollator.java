package net.kwatts.powtools.util;

import java.util.ArrayList;

class InkeyCollator {
    public static final int EXPECTED_SIZE = 20;
    private final ArrayList<Byte> inkey;
    private final byte[] signature;

    public InkeyCollator() {
        inkey = new ArrayList<Byte>(EXPECTED_SIZE);
        signature = Util.StringToByteArrayFastest("098e56");
    }

    public void append(byte[] bytes) {
        for (Byte item : bytes) {
            append(item);
        }
    }

    private void append(Byte item) {
        if (!isFound()) {
            inkey.add(item);
            if (inkey.size() == signature.length) {
                if (!bufferContainsSignature()) {
                    inkey.remove(0);
                }
            }
        }
    }

    private boolean bufferContainsSignature() {
        return inkey.get(0) == signature[0]
            && inkey.get(1) == signature[1]
            && inkey.get(2) == signature[2];
    }

    public boolean isFound() {
        return inkey.size() == EXPECTED_SIZE;
    }

    public byte[] get() {
        byte[] result=null;
        if(isFound()) {
            result = toArray(inkey);
        }
        return result;
    }

    private byte[] toArray(ArrayList<Byte> inkey) {
        byte[] bytes = new byte[inkey.size()];
        for(int index=0;index<inkey.size();index++) {
            bytes[index] = inkey.get(index);
        }
        return bytes;
    }
}
