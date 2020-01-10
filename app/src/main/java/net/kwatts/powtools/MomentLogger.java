package net.kwatts.powtools;

import android.os.Environment;
import android.text.TextUtils;

import net.kwatts.powtools.database.entities.Attribute;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static net.kwatts.powtools.model.OWDevice.KEY_BATTERY_CELLS;

class MomentLogger {

    private final SimpleDateFormat dateOnlyFomat;
    private final SimpleDateFormat dateAndTimeFormat;

    public MomentLogger() {
        dateOnlyFomat = new SimpleDateFormat("yyyy-MM-dd");
        dateAndTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    }

    public void log(Date moment, List<Attribute> attributes) {
        File tripLogFolder = prepareTripLogFolder();
        String fileName = dateOnlyFomat.format(moment)+".csv";
        File tripLogFile = new File(tripLogFolder.toString() + File.separator + fileName);
        boolean needsHeader = !tripLogFile.exists() || tripLogFile.length() == 0;

        try(PrintWriter printWriter = new PrintWriter(new BufferedWriter(new FileWriter(tripLogFile.toString(), true)))) {
            if(needsHeader) {
                writeHeader(printWriter, attributes);
            }
            writeMoment(moment, printWriter, attributes);
        }catch (IOException e) {
            System.err.println(e);
        }


    }

    private void writeMoment(Date moment, PrintWriter printWriter, List<Attribute> attributes) {
        ArrayList<String> values = new ArrayList<>();
        values.add(dateAndTimeFormat.format(moment));
        for (Attribute attribute:attributes) {
            String value = attribute.getValue();
            if(attribute.getKey()==KEY_BATTERY_CELLS && value != null) {
                value = "\""+value.replace(System.lineSeparator(),",")+"\"";
            }
            values.add(value);
        }
        String row = TextUtils.join(",", values);
        printWriter.println(row);
    }

    private void writeHeader(PrintWriter printWriter, List<Attribute> attributes) {
        ArrayList<String> keys = new ArrayList<>();
        keys.add("when");
        for (Attribute attribute:attributes) {
            keys.add(attribute.getKey());
        }
        String header = TextUtils.join(",", keys);
        printWriter.println(header);
    }

    private File prepareTripLogFolder() {
        File externalStorageDirectory = Environment.getExternalStorageDirectory();
        File ponewheelDirectory = new File(externalStorageDirectory.toString() + File.separator + "ponewheel" + File.separator + "trip-log");
        if(!ponewheelDirectory.exists()){
            ponewheelDirectory.mkdirs();
        }
        return ponewheelDirectory;
    }

}
