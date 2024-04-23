package com.lightbuzz.speechrecognitionandroid;

import android.content.Context;
import android.content.Intent;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.chaquo.python.Python;
import com.chaquo.python.PyObject;
import com.chaquo.python.android.AndroidPlatform;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements RecognitionListener {

    private TextView textViewResults;

    protected Intent intent;
    protected SpeechRecognizer recognizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (! Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        Button button_to_py = findViewById(R.id.button_py);

        button_to_py.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClickBut(v);
            }
        });

        textViewResults = (TextView)findViewById(R.id.textViewResults);

        intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_WEB_SEARCH_ONLY, "false");
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, "3000");

        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(this);
        recognizer.startListening(intent);
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
        textViewResults.setText("Listening...");
    }

    @Override
    public void onBeginningOfSpeech() {

    }

    @Override
    public void onRmsChanged(float rmsdB) {

    }

    @Override
    public void onBufferReceived(byte[] buffer) {

    }

    @Override
    public void onEndOfSpeech() {

    }

    @Override
    public void onError(int error) {
        String message;
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                message = "Audio error";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                message = "Client error";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                message = "Insufficient permissions";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                message = "Network error";
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                message = "Network timeout";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                message = "No match";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                message = "Speech Recognizer is busy";
                break;
            case SpeechRecognizer.ERROR_SERVER:
                message = "Server error";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                message = "No speech input";
                break;
            default:
                message = "Speech Recognizer cannot understand you";
                break;
        }

        textViewResults.setText(message);

        //recognizer.stopListening();
        //recognizer.startListening(intent);
    }

    @Override
    public void onResults(Bundle results) {
        ArrayList<String> words = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

        String text = "";

        for (String word : words) {
            text += word + " ";
        }

        textViewResults.setText(text);

        //recognizer.stopListening();
        //recognizer.startListening(intent);
    }

    @Override
    public void onPartialResults(Bundle partialResults) {

    }

    @Override
    public void onEvent(int eventType, Bundle params) {

    }

    public void onClickBut(View view) {
        Python py = Python.getInstance();
        PyObject pyObject = py.getModule("myscript");
        String result = pyObject.callAttr("test").toString();
        TextView myAwesomeTextView = (TextView)findViewById(R.id.textView);
        myAwesomeTextView.setText(result);
//        Toast.makeText(this, "Button clicked!", Toast.LENGTH_SHORT).show();
    }
}