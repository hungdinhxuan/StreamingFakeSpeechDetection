package com.yakovlevegor.DroidRec;
// fake audio detection code
import static android.content.ContentValues.TAG;

import static com.yakovlevegor.DroidRec.MainActivity.SCORE_TAG;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.nio.FloatBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AudioProcessor {
    private ThreadPoolExecutor executor;
    private Handler mainHandler;
    private String all_result = "";
    private static final int SAMPLE_RATE = 16000;
    private final static int CHUNK_TO_READ = 5;
    private final static int CHUNK_SIZE = 12800;
    private static final int INPUT_SIZE = 64000;
    //private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
    private AudioRecord audioRecord;
    private Thread processingThread;
    private final Module aasistModule;
    private final Context context;
    private volatile boolean isRunning = true;

    public AudioProcessor(Module aasistModule, ThreadPoolExecutor executor, Context context) {
        this.context = context;
        this.aasistModule = aasistModule;
        this.executor = executor;
    }

    public void setExecutor(ThreadPoolExecutor executor) {
        this.executor = executor;
    }

    public void run() {
        if (executor == null) {
            executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                @SuppressLint("MissingPermission") final AudioRecord record = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize);
                if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "Audio Record can't initialize!");
                    return;
                }

                record.startRecording();

                int chunkToRead = CHUNK_TO_READ;
                int recordingOffset = 0;
                short[] recordingBuffer = new short[CHUNK_TO_READ * CHUNK_SIZE];
                double[] floatInputBuffer = new double[CHUNK_TO_READ * CHUNK_SIZE];


                while (isRunning && record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    short[] ListenBufffer = new short[bufferSize];
                    record.read(ListenBufffer, 0, ListenBufffer.length);

                    long shortsRead = 0;
                    short[] audioBuffer = new short[bufferSize / 2];


                    while (shortsRead < chunkToRead * CHUNK_SIZE) {
                        // for every segment of 5 chunks of data, we perform transcription
                        // each successive segment’s first chunk is exactly the preceding segment’s last chunk
                        int numberOfShort = record.read(audioBuffer, 0, audioBuffer.length);
                        shortsRead += numberOfShort;
                        int x = (int) (numberOfShort - (shortsRead - chunkToRead * CHUNK_SIZE));
                        if (shortsRead > chunkToRead * CHUNK_SIZE)
                            System.arraycopy(audioBuffer, 0, recordingBuffer, recordingOffset, (int) (numberOfShort - (shortsRead - chunkToRead * CHUNK_SIZE)));
                        else
                            System.arraycopy(audioBuffer, 0, recordingBuffer, recordingOffset, numberOfShort);

                        recordingOffset += numberOfShort;
                    }

                    for (int i = 0; i < CHUNK_TO_READ * CHUNK_SIZE; ++i) {
                        floatInputBuffer[i] = recordingBuffer[i] / (float) Short.MAX_VALUE;
                    }

                    final String result = detect(floatInputBuffer);
                    if (result.length() > 0)
                        all_result = result;

                    chunkToRead = CHUNK_TO_READ - 1;
                    recordingOffset = CHUNK_SIZE;
                        System.arraycopy(recordingBuffer, chunkToRead * CHUNK_SIZE, recordingBuffer, 0, CHUNK_SIZE);

                }

                record.stop();
                record.release();
            }
        });
    }


    private String detect(double[] inputBuffer) {
        Log.d(TAG, "run method started");
        // 배열의 길이를 가져옵니다.
        FloatBuffer inTensorBuffer = Tensor.allocateFloatBuffer(INPUT_SIZE);
        Log.d(TAG, "inputBuffer: " + inputBuffer);
        for (int i = 0; i < inputBuffer.length - 1; i++) {
            inTensorBuffer.put((float) inputBuffer[i]);
        }

        final Tensor inTensor = Tensor.fromBlob(inTensorBuffer, new long[]{INPUT_SIZE});
        final long startTime = SystemClock.elapsedRealtime();
        IValue[] outputTuple;

        final float score = aasistModule.forward(IValue.from(inTensor)).toTensor().getDataAsFloatArray()[0];
        Log.d(SCORE_TAG, "score=" + score);
            if (score >= 0.01) {
                Intent intent = new Intent("com.example.app.ACTION_ALARM");
                intent.putExtra("score", score);
                context.sendBroadcast(intent);
                AlarmReceiver.createNotification(context);
            }

        final long inferenceTime = SystemClock.elapsedRealtime() - startTime;
        Log.d(TAG, "inference time (ms): " + inferenceTime);

        @SuppressLint("DefaultLocale") final String transcript = "FAKE (" + String.format("%.4g", score * 100) + "%)";

        Log.d(SCORE_TAG, "transcript=" + transcript);

        return transcript;
    }

    public void pause() {
        isRunning = false;
        // 녹음이 진행 중일 때만 녹음을 중지합니다.
        if (audioRecord != null && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord.stop();
        }
    }

    public void stop() {
        isRunning = false;
        // AudioRecord가 녹음 중일 때만 중지하고 해제합니다.
        if (audioRecord != null) {
            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop();
            }
            audioRecord.release();
            audioRecord = null;
        }

        // Executor가 실행 중일 때만 종료합니다.
        if (executor != null && !executor.isShutdown() && executor.getActiveCount() == 0) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
            executor = null;
        }
    }

    public void resume() {
        isRunning = true;
        if (executor == null) {
            executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);
        }
        this.run();

    }
}