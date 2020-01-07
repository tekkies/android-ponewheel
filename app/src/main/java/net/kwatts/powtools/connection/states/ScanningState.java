package net.kwatts.powtools.connection.states;

import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;

import net.kwatts.powtools.PayloadUtil;
import net.kwatts.powtools.connection.BluetoothStateMachine;
import net.kwatts.powtools.model.OWDevice;
import net.kwatts.powtools.util.BluetoothUtilImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.artcom.hsm.Action;
import de.artcom.hsm.State;
import timber.log.Timber;

public class ScanningState extends State {
    public static final String ID = "Scanning";

    private BluetoothLeScanner mBluetoothLeScanner;
    private Map<String, String> mScanResults = new HashMap<>();
    private BluetoothStateMachine bluetoothStateMachine;

    public ScanningState(BluetoothStateMachine bluetoothStateMachine) {
        super(ID);
        this.bluetoothStateMachine = bluetoothStateMachine;
        onEnter(new StartScan());
        onExit(new StopScan());
    }

    private class StartScan extends Action {
        @Override
        public void run() {
            mScanResults.clear();
            List<ScanFilter> filters_v2 = new ArrayList<>();
            ScanFilter scanFilter = new ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid.fromString(OWDevice.OnewheelServiceUUID))
                    .build();
            filters_v2.add(scanFilter);
            mBluetoothLeScanner = bluetoothStateMachine.getBluetoothAdapter().getBluetoothLeScanner();
            ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            mBluetoothLeScanner.startScan(filters_v2, settings, mScanCallback);
        }

    }

    private class StopScan extends Action {
        @Override
        public void run() {
            mBluetoothLeScanner.stopScan(mScanCallback);
            // added 10/23 to try cleanup
            mBluetoothLeScanner.flushPendingScanResults(mScanCallback);
        }
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            String deviceName = result.getDevice().getName();
            String deviceAddress = result.getDevice().getAddress();

            Timber.i("ScanCallback.onScanResult: " + mScanResults.entrySet());
            if (!mScanResults.containsKey(deviceAddress)) {
                Timber.i("ScanCallback.deviceName:" + deviceName);
                mScanResults.put(deviceAddress, deviceName);

                if (deviceName == null) {
                    Timber.i("Found " + deviceAddress);
                } else {
                    Timber.i("Found " + deviceAddress + " (" + deviceName + ")");
                }

                if (deviceName != null && (deviceName.startsWith("ow") || deviceName.startsWith("Onewheel"))) {

                    bluetoothStateMachine.handleStateMachineEvent(bluetoothStateMachine.events.ONEWHEEL_FOUND, new PayloadUtil().add(result).build());
                    Timber.i("Looks like we found our OW device (" + deviceName + ") discovering services!");
                } else {
                    Timber.d("onScanResult: found another device:" + deviceName + "-" + deviceAddress);
                }

            } else {
                Timber.d("onScanResult: mScanResults already had our key, still connecting to OW services or something is up with the BT stack.");
                //  Timber.d("onScanResult: mScanResults already had our key," + "deviceName=" + deviceName + ",deviceAddress=" + deviceAddress);
                // still connect
                //connectToDevice(result.getDevice());
            }


        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                Timber.i("ScanCallback.onBatchScanResults.each:" + sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Timber.e("ScanCallback.onScanFailed:" + errorCode);
        }
    };


}
