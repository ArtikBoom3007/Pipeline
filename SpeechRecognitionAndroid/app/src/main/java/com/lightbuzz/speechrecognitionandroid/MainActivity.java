package com.lightbuzz.speechrecognitionandroid;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

import com.chaquo.python.Python;
import com.chaquo.python.PyObject;
import com.chaquo.python.android.AndroidPlatform;

import be.tarsos.dsp.SilenceDetector;

import com.chaquo.python.Python;
import com.chaquo.python.PyObject;
import com.chaquo.python.android.AndroidPlatform;

public class MainActivity extends AppCompatActivity {

    private String toRead = "Once upon a time there was a sweet little girl. Everyone who saw her liked her, but most of all her grandmother, who did not know what to give the child next. Once she gave her a little cap made of red velvet. Because it suited her so well, and she wanted to wear it all the time, she came to be known as Little Red Riding Hood. One day her mother said to her: \"Come Little Red Riding Hood. Here is a piece of cake and a bottle of wine. Take them to your grandmother. She is sick and weak, and they will do her well. Mind your manners and give her my greetings. Behave yourself on the way, and do not leave the path, or you might fall down and break the glass, and then there will be nothing for your sick grandmother.";
    private TextView textViewResults;
    private TextView detectionStatus;
    private SeekBar seekBar;
    private TextView trCurrentValue;
    File sdDir = null;
    private ByteArrayOutputStream audioData;
    private AudioRecord audioRecord;
    private Thread recordingThread;
    private Thread modelThread;
    private boolean isRecording = false;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int SAMPLE_RATE = 44100;
    //{8000, 11025, 16000, 22050, 44100}
    private static final int CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int BIT_PER_SAMPLE = AudioFormat.ENCODING_PCM_16BIT;
    private static final int AUDIO_LENGTH = 5 * SAMPLE_RATE * BIT_PER_SAMPLE;
    private static final int frameSize = 25 * SAMPLE_RATE / 1000; //25ms * 44,1 kHz
    private int threshold;
    private static final int MIN_VALUE = -100;
    private static final int MAX_VALUE = 100;

    private SilenceDetector silenceDetector;
    private int bufferSize;
    private int nameIterator;
    private File directory;
    Toast toast;
    Python py;
    PyObject module;

    private boolean deleteFlag = false;

    static {
       System.loadLibrary("speechrecognitionandroid");
    }

    private native String stringFromJNI();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textViewResults = (TextView)findViewById(R.id.textViewResults);
        detectionStatus = (TextView)findViewById(R.id.detectionStatus);
        seekBar = findViewById(R.id.vadSeekBar);
        trCurrentValue = findViewById(R.id.vadParam);
        TextView tv = (TextView)findViewById(R.id.taleText);
        tv.setText(toRead);
        tv.setMovementMethod(new ScrollingMovementMethod());
        Switch enable = (Switch)findViewById(R.id.switch_delete);
        enable.setChecked(true);

        nameIterator = 0;

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

        String path = sdDir + "/Documents/TempSRA/";
        directory = new File(path);

        initPy();

        initSeek();

        audioData = new ByteArrayOutputStream();

        enable.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view) {
                if (enable.isChecked()) //checking if  switch is checked
                {
                    enable.setChecked(true);
                    deleteFlag = false;
                } else {
                    enable.setChecked(false);
                    deleteFlag = true;
                }
            }
        });
    }

    public void onDestroy() {

        super.onDestroy();
        if (deleteFlag) {
            deleteRecursive(directory);
        }
    }

    public void startRecordingOnClick(View view) {
        Button startRecordingButton = findViewById(R.id.startRecordingButton);
        if (!isRecording) {
            isRecording = true;
            textViewResults.setText("Listening...");
            startRecording();
            startRecordingButton.setText("Stop recording");
        } else {
            isRecording = false;
            stopRecording();
            startRecordingButton.setText("Start recording");
        }
    }

    public void initPy() {

        if (! Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }

        py = Python.getInstance();

        module = py.getModule("classifier");

        String modelPath = copyAssetToCache(this, "svm_classifier_model.pkl");
        String modelCPath = copyAssetToCache(this, "svm_classifier_model_only_mfcc.pkl");
        String scalerPath = copyAssetToCache(this, "scaler.pkl");
        module.callAttr("load_model", modelPath, modelCPath, scalerPath);
    }

    private void initSeek() {
        seekBar.setMax(MAX_VALUE - MIN_VALUE);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                threshold = progress + MIN_VALUE;
                trCurrentValue.setText(String.valueOf(threshold));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Do something if needed when user starts to touch the SeekBar
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Do something if needed when user stops to touch the SeekBar
            }
        });

        // Set the initial threshold value if needed
        threshold = 40;
        trCurrentValue.setText(String.valueOf(threshold));
        seekBar.setProgress(threshold - MIN_VALUE);
    }

    public void makeDecision() {
        File[] audiofiles = directory.listFiles();
        float sum = 0;

        for (int i = 0; i < audiofiles.length; i++) {
            PyObject result = module.callAttr("classify_audio", audiofiles[i].getAbsolutePath());
            sum += result.toJava(float.class);
        }

        if (sum / audiofiles.length > 0.5) {
            detectionStatus.setText("Possibility of Parkinson's disease");
        }
        else {
            detectionStatus.setText("Parkinson's disease not detected");
        }

        // Now post a toast message to the UI thread
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "Prediction complete", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static String copyAssetToCache(Context context, String assetName) {
        AssetManager assetManager = context.getAssets();
        File file = new File(context.getCacheDir(), assetName);
        try (InputStream is = assetManager.open(assetName);
             FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return file.getAbsolutePath();
    }

    // Метод для начала записи голоса
    public void startRecording() {
        // Проверка разрешения на запись аудио
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        } else {

            audioData.reset();
            bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, BIT_PER_SAMPLE);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_IN, BIT_PER_SAMPLE, bufferSize);

            // Create a SilenceDetector with a threshold of -50 dB
            silenceDetector = new SilenceDetector(threshold, false);
            // Create an AudioDispatcher to process the audio data

            audioRecord.startRecording();

            // Поток для записи аудиоданных в массив
            recordingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (isRecording) {
                        byte[] buffer = new byte[bufferSize];
                        int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                        boolean isSil = silenceDetector.isSilence(bytesToFloats(buffer));

                        if (bytesRead > 0 && !isSil) {
                            audioData.write(buffer, 0, bytesRead);
                        }
                        if (audioData.size() == AUDIO_LENGTH) {
                            writeInWav();
                            audioData.reset();
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

            modelThread = new Thread(new Runnable() {
                @Override
                public void run() {
                   makeDecision();
                }
            });
            modelThread.start();
        }
    }

    private static float[] bytesToFloats(byte[] bytes) {
        float[] floats = new float[bytes.length / 2];
        for(int i=0; i < bytes.length; i+=2) {
            floats[i/2] = bytes[i] | (bytes[i+1] << 8);
        }
        return floats;
    }

    // Метод для воспроизведения записанного звука
    public void playRecording(View view) {
        if (audioData.size() != 0) {

            textViewResults.setText("Playing...");
            byte[] audioBytes = audioData.toByteArray();
            int minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, BIT_PER_SAMPLE);
            AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, CHANNEL_OUT, BIT_PER_SAMPLE, minBufferSize, AudioTrack.MODE_STREAM);
            audioTrack.play();
            audioTrack.write(audioBytes, 0, audioBytes.length);
            textViewResults.setText("Audio ended...");
        }
        else {
            int duration = Toast.LENGTH_SHORT;
            toast = Toast.makeText(this /* MyActivity */, "audio buffer is empty", duration);
            toast.show();
        }
    }

    private static int findMaxAmplitude(short[] buffer) {
        short max = Short.MIN_VALUE;
        for (int i = 0; i < buffer.length; ++i) {
            short value = buffer[i];
            max = (short)Math.max(max, Math.abs(value));
        }
        return max;
    }

    byte[] normalize_audio(byte[] audio) {
        short[] buffer = new short[audio.length/2];
        ByteBuffer.wrap(audio).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(buffer);
        short[] output = new short[buffer.length];
        int maxAmplitude = findMaxAmplitude(buffer);
        for (int index = 0; index < buffer.length; index++) {
            output[index] = normalize_sample(buffer[index], maxAmplitude);
        }
        byte[] res = new byte[output.length * 2];
        ByteBuffer.wrap(res).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(output);
        return res;
    }

    private short normalize_sample(short value, int rawMax) {
        short targetMax = 32767;
        double maxReduce = 1 - targetMax / (double) rawMax;
        int abs = Math.abs(value);
        double factor = (maxReduce * abs / (double) rawMax);

        return (short)Math.round(value * targetMax / rawMax);
    }

    public void writeInWav() {
        if (!directory.exists()) {
            boolean isDirectoryCreated = directory.mkdirs();
            if (isDirectoryCreated) {
                Log.d("DirectoryCreated", "Directory created successfully");
            } else {
                Log.e("DirectoryCreationError", "Failed to create directory");
            }
        } else {
            Log.d("DirectoryExists", "Directory already exists");
        }

        String name = "audio" + nameIterator;
        nameIterator++;

        File temp_audio = new File(directory,name + ".wav");
        FileOutputStream os = null;
        try {
            os = new FileOutputStream(temp_audio);
            writeWavHeader(os, 1, audioData.size());

            byte[] norm_audio = normalize_audio(audioData.toByteArray());


            os.write(norm_audio, 0, norm_audio.length);
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

    public void deleteRecursive(File fileOrDirectory) {

        if (fileOrDirectory.isDirectory()) {
            for (File child : Objects.requireNonNull(fileOrDirectory.listFiles())) {
                deleteRecursive(child);
            }
        }

        fileOrDirectory.delete();
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