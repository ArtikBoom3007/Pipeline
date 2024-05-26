package com.lightbuzz.speechrecognitionandroid;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import com.chaquo.python.Python;
import com.chaquo.python.PyObject;
import com.chaquo.python.android.AndroidPlatform;

public class MainActivity extends AppCompatActivity {


    private TextView textViewResults;
    File sdDir = null;
    private ByteArrayOutputStream audioData;
    private AudioRecord audioRecord;
    private Thread recordingThread;
    private boolean isRecording = false;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int SAMPLE_RATE = 44100;
    //{8000, 11025, 16000, 22050, 44100}
    private static final int CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int BIT_PER_SAMPLE = AudioFormat.ENCODING_PCM_16BIT;
    private int bufferSize;

    static {
       System.loadLibrary("speechrecognitionandroid");
    }

    private native String stringFromJNI();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textViewResults = (TextView)findViewById(R.id.textViewResults);
        Button startRecordingButton = findViewById(R.id.startRecordingButton);
        Button playRecordingButton = findViewById(R.id.playRecordingButton);

        Button hello_c = findViewById(R.id.button_c);
        Button hello_py = findViewById(R.id.button_py);
        TextView textHello = (TextView)findViewById(R.id.textHello);

        hello_c.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                textHello.setText(stringFromJNI());
            }
        });

        if (! Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }

        hello_py.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Python py = Python.getInstance();
                PyObject pyObject = py.getModule("myscript");
                String result = pyObject.callAttr("test").toString();
                TextView myAwesomeTextView = (TextView)findViewById(R.id.textHello);
                myAwesomeTextView.setText(result);
            }
        });

        // Проверка и запрос разрешения на запись аудио
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                REQUEST_RECORD_AUDIO_PERMISSION);

        String sdState = android.os.Environment.getExternalStorageState();
        if (sdState.equals(android.os.Environment.MEDIA_MOUNTED)) {
            sdDir = android.os.Environment.getExternalStorageDirectory();
        }

        startRecordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRecording) {
                    isRecording = true;
                    startRecording();
                    startRecordingButton.setText("Stop recording");
                } else {
                    isRecording = false;
                    stopRecording();
                    startRecordingButton.setText("Start recording");
                }
            }
        });
    }

    // Метод для начала записи голоса
    public void startRecording() {
        // Проверка разрешения на запись аудио
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        } else {

            audioData = new ByteArrayOutputStream();
            bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, BIT_PER_SAMPLE);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_IN, BIT_PER_SAMPLE, bufferSize);

            audioRecord.startRecording();

            // Поток для записи аудиоданных в массив
            recordingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (isRecording) {
                        byte[] buffer = new byte[bufferSize];
                        int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                        if (bytesRead > 0) {
                            audioData.write(buffer, 0, bytesRead);
                        }
                        try {
                            Thread.sleep(30);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }
            });
            recordingThread.start();
        }
    }

    // Метод для остановки записи и запуска распознавания
    public void stopRecording() {
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
            recordingThread = null;
            writeInWav();
        }
    }

    // Метод для воспроизведения записанного звука
    public void playRecording(View view) {
        if (audioData != null) {
            textViewResults.setText("Playing...");
            byte[] audioBytes = audioData.toByteArray();
            int minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, BIT_PER_SAMPLE);
            AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, CHANNEL_OUT, BIT_PER_SAMPLE, minBufferSize, AudioTrack.MODE_STREAM);
            audioTrack.play();
            audioTrack.write(audioBytes, 0, audioBytes.length);
            textViewResults.setText("Audio ended...");
        }
    }

    public void writeInWav() {
        File temp_audio = new File(sdDir,"/Documents/audio" + ".wav");
        FileOutputStream os = null;
        try {
            os = new FileOutputStream(temp_audio);
            writeWavHeader(os, 1, audioData.size());

            os.write(audioData.toByteArray(), 0, audioData.size());
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            boolean permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (!permissionToRecordAccepted) {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }



    private void writeWavHeader(FileOutputStream outputStream, int channels, int totalAudioLen) throws IOException {
        byte[] header = new byte[44];
        long byteRate = (long)SAMPLE_RATE * channels * BIT_PER_SAMPLE;
        long totalDataLen = totalAudioLen + 36;

        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';  // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) ((long)SAMPLE_RATE & 0xff);
        header[25] = (byte) (((long)SAMPLE_RATE >> 8) & 0xff);
        header[26] = (byte) (((long)SAMPLE_RATE >> 16) & 0xff);
        header[27] = (byte) (((long)SAMPLE_RATE >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * BIT_PER_SAMPLE);  // block align
        header[33] = 0;
        header[34] = (byte) BIT_PER_SAMPLE * 8;  // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        outputStream.write(header, 0, 44);
    }
}