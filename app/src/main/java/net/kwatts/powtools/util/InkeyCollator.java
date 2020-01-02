package net.kwatts.powtools.util;

import java.util.ArrayList;

class InkeyCollator {
    public static final int EXPECTED_SIZE = 20;
    private final ArrayList<Byte> inkey;
    private final byte[] signature;

    public InkeyCollator() {
        inkey = new ArrayList<Byte>();
        signature = Util.StringToByteArrayFastest("098e56");
    }

    public void append(byte[] bytes) {
        for (Byte item: bytes) {
            append(item);
        }
    }

    private void append(Byte item) {
        if(inkey.size() == 0) {
            if(item == signature[0]) {
                inkey.add(item);
            }
        } else if(inkey.size() == 1) {
            if(item == signature[1]) {
                inkey.add(item);
            } else {
                inkey.clear();
            }
        } else if(inkey.size() == 2) {
            if(item == signature[2]) {
                inkey.add(item);
            } else {
                inkey.clear();
            }
        } else if(inkey.size() < EXPECTED_SIZE) {
            inkey.add(item);
        }
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
        for(int index=0;index<EXPECTED_SIZE;index++) {
            bytes[index] = inkey.get(index);
        }
        return bytes;
    }
}
