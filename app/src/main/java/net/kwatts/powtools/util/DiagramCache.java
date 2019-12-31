package net.kwatts.powtools.util;

import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import net.kwatts.powtools.App;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.artcom.hsm.State;
import de.artcom.hsm.StateMachine;
import timber.log.Timber;
import uk.co.tekkies.hsm.plantuml.PlantUmlBuilder;
import uk.co.tekkies.hsm.plantuml.PlantUmlUrlEncoder;

public class DiagramCache {
    private String cacheFolder;
    private StateMachine stateMachine;
    private Map<String, String> stateIdToFilePath;

    public DiagramCache(String location, StateMachine stateMachine) {
        this.cacheFolder = location;
        this.stateMachine = stateMachine;
        stateIdToFilePath = new HashMap<String, String>();
    }

    public DiagramCache fill() {
        new CacheFillTask(stateMachine).execute();
        return this;
    }

    private File getFileForUrl(String activeStateDiagramUrl) {
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

    public InputStream getActiveStateDiagram() {
        InputStream diagramStream=null;
        List<State> allActiveStates = stateMachine.getAllActiveStates();
        State currentActiveState = allActiveStates.get(allActiveStates.size() - 1);
        String diagramFilePath = getDiagramFilePath(currentActiveState);
        if(diagramFilePath != null) {
            try {
                diagramStream = new FileInputStream(diagramFilePath);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        return diagramStream;
    }

    private class CacheFillTask extends AsyncTask<String, Void, Boolean> {
        public CacheFillTask(StateMachine stateMachine) {
        }
        protected Boolean doInBackground(String... urls) {
            Boolean success = true;
            List<State> leafStates = getLeafStates();
            for (State leafState: leafStates) {
                Timber.v(leafState.getId());
                String diagramFilePath = getDiagramFilePath(leafState);
                if(diagramFilePath == null)
                {
                    success = false;
                }
            }
            return success;
        }

        protected void onPostExecute(Boolean success) {
            if(!success) {
                Toast.makeText(App.INSTANCE.getApplicationContext(), "Diagram cache failed to download.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private String getDiagramFilePath(State state) {
        String diagramFilePath = this.stateIdToFilePath.get(state.getId());
        if(diagramFilePath == null) {
            String plantUml = new PlantUmlBuilder(stateMachine).highlight(state).build();
            String url = new PlantUmlUrlEncoder().getUrl(plantUml);
            File file = getFileForUrl(url);
            if(file != null)
            {
                diagramFilePath = file.getAbsolutePath();
                stateIdToFilePath.put(state.getId(), diagramFilePath);

            }
        }
        return diagramFilePath;
    }

    private List<State> getLeafStates() {
        List<State> allStates = stateMachine.getDescendantStates();
        List<State> leafStates = new ArrayList<State>();
        for (State state:allStates) {
            if(state.getDescendantStates().size() == 0) {
                leafStates.add(state);
            }
        }
        return leafStates;
    }
}
