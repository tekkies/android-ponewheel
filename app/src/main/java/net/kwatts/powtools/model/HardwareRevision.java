package net.kwatts.powtools.model;

import android.support.annotation.NonNull;

public class HardwareRevision {
    private final int revisionNumber;
    private String modelName;

    public HardwareRevision(int revisionNumber) {
        this.revisionNumber = revisionNumber;
        calculateModel();
    }

    private void calculateModel() {
        int modelNo = revisionNumber / 1000;
        switch (modelNo)
        {
            case 4:
                modelName = "XR";
                break;

            case 5:
                modelName = "Pint";
                break;

            default:
                modelName = null;
        }
    }

    @NonNull
    @Override
    public String toString() {
        String asString= Integer.toString(revisionNumber);
        if(modelName != null)
        {
            asString += ":" + modelName;
        }
        return asString;
    }
}
