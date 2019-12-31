package net.kwatts.powtools.util;

import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import net.kwatts.powtools.App;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import de.artcom.hsm.StateMachine;
import timber.log.Timber;
import uk.co.tekkies.hsm.plantuml.PlantUmlBuilder;

class DiagramCache {
    private String cacheFolder;
    private StateMachine stateMachine;

    public DiagramCache(String location, StateMachine stateMachine) {
        this.cacheFolder = location;
        this.stateMachine = stateMachine;

    }

    public DiagramCache fill() {
        new CacheFillTask(stateMachine).execute();

        return this;
    }

    private File getFile(String activeStateDiagramUrl) {
        File result=null;
        String fileName = getFileName(activeStateDiagramUrl);
        String filePath = cacheFolder + File.separator + fileName;
        File file = new File(filePath);
        if(!file.exists()) {
            result = download(activeStateDiagramUrl, file);
        } else {
            result = file;
        }
        return result;
    }

    private File download(String activeStateDiagramUrl, File file) {
        File result  = null;
        try {
            InputStream inputStream = new java.net.URL(activeStateDiagramUrl).openStream();
            FileOutputStream out = new FileOutputStream(file);
            copyStream (inputStream, out);
            out.close();
            result = file;
        } catch (Exception e) {
            Log.e("Error", e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    public static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }


    private String getFileName(String activeStateDiagramUrl) {
        return md5(activeStateDiagramUrl)+".png";
    }

    public String md5(String s) {
        try {
            // Create MD5 Hash
            MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            digest.update(s.getBytes());
            byte messageDigest[] = digest.digest();

            // Create Hex String
            StringBuffer hexString = new StringBuffer();
            for (int i = 0; i < messageDigest.length; i++)
                hexString.append(Integer.toHexString(0xFF & messageDigest[i]));
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    public DiagramCache ensurePathExists() {
        File cacheFolder = new File(this.cacheFolder);
        if(!cacheFolder.exists())
        {
            cacheFolder.mkdirs();
        }
        return this;
    }

    private class CacheFillTask extends AsyncTask<String, Void, Boolean> {
        ImageView bmImage;

        public CacheFillTask(StateMachine stateMachine) {
            this.bmImage = bmImage;
        }

        protected Boolean doInBackground(String... urls) {
            Boolean success = true;
            List<String> activeStateDiagramUrls = new PlantUmlBuilder(stateMachine).getActiveStateDiagramUrls();
            for (String activeStateDiagramUrl : activeStateDiagramUrls) {
                if(getFile(activeStateDiagramUrl) == null)
                {
                    success = false;
                }
            }
            return success;
        }


        protected void onPostExecute(Boolean result) {
            String message = result ? "Cache filled" : "Cache fill FAILED";
            Toast.makeText(App.INSTANCE.getApplicationContext(),  message, Toast.LENGTH_LONG).show();

        }
    }

}
