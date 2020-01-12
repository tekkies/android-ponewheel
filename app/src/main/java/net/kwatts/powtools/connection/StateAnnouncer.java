package net.kwatts.powtools.connection;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import com.google.common.base.Stopwatch;

import java.util.Locale;

import static android.speech.tts.TextToSpeech.SUCCESS;

public class StateAnnouncer implements TextToSpeech.OnInitListener {

    private final TextToSpeech textToSpeech;
    private final Stopwatch lastUtteranceStopwatch;
    private int utterance;
    private int status;
    private String lastUtternace ="";

    public StateAnnouncer(Context context) {
        textToSpeech = new TextToSpeech(context, this);
        textToSpeech.setLanguage(Locale.US);
        lastUtteranceStopwatch = new Stopwatch().reset().start();
    }

    public void announce(String utterance) {
        if(status == SUCCESS) {
            utterance = reduceRepetition(utterance);
            if(utterance != null) {
                textToSpeech.speak(utterance, TextToSpeech.QUEUE_FLUSH, null, Integer.toString(this.utterance++));

            }
        }
    }

    private String reduceRepetition(String utterance) {
        if (utterance == lastUtternace) {
            if(lastUtteranceStopwatch.elapsedMillis() < 5000) {
                utterance = null;
            } else {
                utterance = "Still "+utterance;
            }
        } else {
            lastUtternace = utterance;
        }
        if(utterance != null) {
            lastUtteranceStopwatch.reset().start();
        }
        return utterance;
    }


    @Override
    public void onInit(int status) {
        this.status = status;
    }
}
