package net.kwatts.powtools.connection.states;

import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;

import net.kwatts.powtools.model.OWDevice;
import net.kwatts.powtools.util.BluetoothUtilImpl;

import java.util.ArrayList;
import java.util.List;

import de.artcom.hsm.Action;
import de.artcom.hsm.State;
import timber.log.Timber;

public class DisabledState extends State {
    public static final String ID = "Connection Disabled";

    public DisabledState() {
        super(ID);
    }

}
