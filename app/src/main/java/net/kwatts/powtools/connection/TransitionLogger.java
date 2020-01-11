package net.kwatts.powtools.connection;

import android.os.Environment;

import net.kwatts.powtools.PayloadUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import timber.log.Timber;

class TransitionLogger {

    private final SimpleDateFormat dateOnlyFomat;
    private final SimpleDateFormat dateAndTimeFormat;

    public TransitionLogger() {
        dateOnlyFomat = new SimpleDateFormat("yyyy-MM-dd");
        dateAndTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    }

    public void log(String event, Map<String, Object> payload) {
        File tripLogFolder = prepareTripLogFolder();
        Date date = new Date();
        String fileName = dateOnlyFomat.format(date)+".csv";
        File tripLogFile = new File(tripLogFolder.toString() + File.separator + fileName);
        boolean needsHeader = !tripLogFile.exists() || tripLogFile.length() == 0;
        String payloadHint = new PayloadUtil(payload).getPayload(PayloadUtil.HINT);
        try(PrintWriter printWriter = new PrintWriter(new BufferedWriter(new FileWriter(tripLogFile.toString(), true)))) {
            String timestamp = this.dateAndTimeFormat.format(date);
            String message = String.format("%s %s %s", timestamp, event, payloadHint == null ? "" : payloadHint);
            Timber.v(message);
            printWriter.println(message);
        }catch (IOException e) {
            System.err.println(e);
        }



    }

    private File prepareTripLogFolder() {
        File externalStorageDirectory = Environment.getExternalStorageDirectory();
        File ponewheelDirectory = new File(externalStorageDirectory.toString() + File.separator + "ponewheel" + File.separator + "state-log");
        if(!ponewheelDirectory.exists()){
            ponewheelDirectory.mkdirs();
        }
        return ponewheelDirectory;
    }

}
