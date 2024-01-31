//package com.yakovlevegor.DroidRec;
//
//import static android.content.ContentValues.TAG;
//
//import static com.yakovlevegor.DroidRec.MainActivity.SCORE_TAG;
//
//import android.annotation.SuppressLint;
//import android.media.AudioFormat;
//import android.media.AudioRecord;
//import android.media.MediaRecorder;
//import android.os.Handler;
//import android.os.SystemClock;
//import android.util.Log;
//
//import org.pytorch.IValue;
//import org.pytorch.Module;
//import org.pytorch.Tensor;
//
//import java.nio.FloatBuffer;
//import java.util.concurrent.ThreadPoolExecutor;
//import java.util.concurrent.TimeUnit;
//
//public class AudioProcessor {
//    private ThreadPoolExecutor executor;
//    private Handler mainHandler;
//    private static final int SAMPLE_RATE = 16000; // 샘플링 레이트
//    private static final int INPUT_SIZE = 64000;
//    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
//    private AudioRecord audioRecord;
//    private Thread processingThread;
//    private final Module aasistModule;
//    public AudioProcessor(Module aasistModule, ThreadPoolExecutor executor) {
//        this.aasistModule = aasistModule;
//        this.executor = executor;
//    }
//    public void setExecutor(ThreadPoolExecutor executor) {
//        this.executor = executor;
//    }
//    public void run() {
//        executor.execute(new Runnable() {
//            @Override
//            public void run() {
//                //final int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
//                audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE);
//                audioRecord.startRecording();
//
//                while (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
//                    short[] buffer = new short[BUFFER_SIZE];
//                    int read = audioRecord.read(buffer, 0, BUFFER_SIZE);
//                    for (int i = 0; i < read; i += SAMPLE_RATE) {
//                        double[] doubleBuffer = new double[SAMPLE_RATE]; // SAMPLE_RATE 크기의 새로운 버퍼
//
//                        // 버퍼의 일부를 새로운 버퍼에 복사
//                        System.arraycopy(buffer, i, doubleBuffer, 0, INPUT_SIZE);
//
//                        // 새로운 버퍼에 대한 예측을 수행
//                        String result = detect(doubleBuffer);
//                    }
//                }
//                Log.d(TAG, "Processing thread started");
//            }
//        });
//    }
//    private String detect(double[] inputBuffer) {
//        Log.d(TAG, "run method started");
//        // 배열의 길이를 가져옵니다.
//        int bufferSize = inputBuffer.length;
//        // TensorFlow Lite 모델에 전달할 수 있는 형식으로 데이터를 변환합니다.
//        FloatBuffer inTensorBuffer = Tensor.allocateFloatBuffer(bufferSize);
//        for (double value : inputBuffer) {
//            inTensorBuffer.put((float) value);
//        }
//
//        // 변환된 데이터를 사용하여 Tensor 인스턴스를 생성합니다.
//        final Tensor inTensor = Tensor.fromBlob(inTensorBuffer, new long[]{1, bufferSize}); // 1은 배치 크기를 나타냅니다.
//        final long startTime = SystemClock.elapsedRealtime();
//
//        // AI 모델을 실행하고 결과를 얻습니다.
//        final float score = aasistModule.forward(IValue.from(inTensor)).toTensor().getDataAsFloatArray()[0];
//        Log.d(SCORE_TAG, "score=" + score);
//
//        final long inferenceTime = SystemClock.elapsedRealtime() - startTime;
//        Log.d(TAG, "inference time (ms): " + inferenceTime);
//
//        @SuppressLint("DefaultLocale") final String transcript = "FAKE (" + String.format("%.2g", score * 100) + "%)";
//        Log.d(SCORE_TAG, "transcript=" + transcript);
//        return transcript;
//    }
//        public void pause() {
//        // 녹음을 중지합니다.
//        if (audioRecord != null) {
//            audioRecord.stop();
//        }
//    }
//    public void stop() {
//        // AudioRecord를 중지하고 해제합니다.
//        if (audioRecord != null) {
//            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
//                audioRecord.stop();
//            }
//            audioRecord.release();
//            audioRecord = null;
//        }
//
//        // Executor를 종료합니다.
//        if (executor != null) {
//            executor.shutdown();
//            try {
//                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
//                    executor.shutdownNow();
//                }
//            } catch (InterruptedException e) {
//                executor.shutdownNow();
//            }
//            executor = null;
//        }
//    }
//}

//package com.yakovlevegor.DroidRec;
//
//import android.annotation.SuppressLint;
//import android.content.Context;
//import android.content.Intent;
//import android.media.AudioFormat;
//import android.media.AudioRecord;
//import android.media.MediaRecorder;
//import android.os.Handler;
//import android.os.SystemClock;
//import android.util.Log;
//import android.widget.Button;
//import android.widget.TextView;
//import static android.content.ContentValues.TAG;
//
//
//import org.pytorch.IValue;
//import org.pytorch.Module;
//import org.pytorch.Tensor;
//
//import java.nio.FloatBuffer;
//import java.util.concurrent.ThreadPoolExecutor;
//import java.util.concurrent.TimeUnit;
//
//public class AudioProcessor implements Runnable {
//    private static final String SCORE_TAG = "FAKE %: ";
//    private Module aasistModule;
//    private TextView mTextView;
//    private Button mButton;
//    private boolean mListening;
//    private String all_result = "";
//    private AudioRecord audioRecord;
//    private ThreadPoolExecutor executor;
//    private Context context;
//    private final static int REQUEST_RECORD_AUDIO = 13;
//    private final static int SAMPLE_RATE = 16000;
//    private final static int CHUNK_TO_READ = 5;
//    private final static int CHUNK_SIZE = 8000;
//    private final static int INPUT_SIZE = 64000;
//
//    private Handler handler;
//
//    public AudioProcessor(Context context, Module aasistModule, ThreadPoolExecutor executor) {
//        this.aasistModule = aasistModule;
//        this.executor = executor;
//        this.context = context;
//    }
//    public void setExecutor(ThreadPoolExecutor executor) {
//        this.executor = executor;
//    }
//    public void run() {
//        executor.execute(new Runnable() {
//            @Override
//            public void run() {
//                final int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
//                audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
//                audioRecord.startRecording();
//                int chunkToRead = CHUNK_TO_READ;
//                int recordingOffset = 0;
//                short[] recordingBuffer = new short[CHUNK_TO_READ*CHUNK_SIZE];
//                double[] floatInputBuffer = new double[CHUNK_TO_READ * CHUNK_SIZE];
//
//                while (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
//                    short[] buffer = new short[bufferSize];
//                    int read = audioRecord.read(buffer, 0, bufferSize);
//                    if (read > 0) {
//                        long shortsRead = 0;
//                        short[] audioBuffer = new short[bufferSize / 2];
//
//                        while (shortsRead < chunkToRead * CHUNK_SIZE) {
//                            int numberOfShort = audioRecord.read(audioBuffer, 0, audioBuffer.length);
//                            shortsRead += numberOfShort;
//
//                            int copyLength = 0;
//                            if (shortsRead > chunkToRead * CHUNK_SIZE) {
//                                copyLength = (int) (numberOfShort - (shortsRead - chunkToRead * CHUNK_SIZE));
//                            } else {
//                                copyLength = numberOfShort;
//                            }
//
//                            // 복사할 길이가 원본 배열의 길이와 목표 배열의 남은 공간을 초과하지 않도록 보장합니다.
//                            copyLength = Math.min(copyLength, audioBuffer.length);
//                            copyLength = Math.min(copyLength, recordingBuffer.length - recordingOffset);
//
//                            System.arraycopy(audioBuffer, 0, recordingBuffer, recordingOffset, copyLength);
//                            recordingOffset += copyLength;
//                        }
//
//
//                        for (int i = 0; i < CHUNK_TO_READ * CHUNK_SIZE; ++i) {
//                            floatInputBuffer[i] = recordingBuffer[i] / (float)Short.MAX_VALUE;
//                        }
//
//                        String result = detect(floatInputBuffer);
//                    }
//                }
//                Log.d(TAG, "Processing thread started");
//            }
//        });
//    }
//
//
//    private String detect(double[] inputBuffer) {
//        FloatBuffer inTensorBuffer = Tensor.allocateFloatBuffer(INPUT_SIZE);
//        for (int i = 0; i < inputBuffer.length - 1; i++) {
//            inTensorBuffer.put((float) inputBuffer[i]);
//        }
//
//        final Tensor inTensor = Tensor.fromBlob(inTensorBuffer, new long[]{INPUT_SIZE});
//        final long startTime = SystemClock.elapsedRealtime();
//        IValue[] outputTuple;
//
//        final float score = aasistModule.forward(IValue.from(inTensor)).toTensor().getDataAsFloatArray()[0];
//        Log.d(SCORE_TAG, "score=" + score);
//        if (score >= 0.01) {
//            Intent intent = new Intent("com.example.app.ACTION_ALARM");
//            intent.putExtra("score", score);
//            context.sendBroadcast(intent);  // context는 현재 클래스의 Context입니다.
//            AlarmReceiver.createNotification(context);
//        }
//
//        final long inferenceTime = SystemClock.elapsedRealtime() - startTime;
//        Log.d(TAG, "inference time (ms): " + inferenceTime);
//
//        @SuppressLint("DefaultLocale") final String transcript = "FAKE (" + String.format("%.2g", score * 100) + "%)";
//
//        Log.d(SCORE_TAG, "transcript=" + transcript);
//
//        return transcript;
//    }
//        public void pause() {
//        // 녹음을 중지합니다.
//        if (audioRecord != null) {
//            audioRecord.stop();
//        }
//    }
//    public void stop() {
//        // AudioRecord를 중지하고 해제합니다.
//        if (audioRecord != null) {
//            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
//                audioRecord.stop();
//            }
//            audioRecord.release();
//            audioRecord = null;
//        }
//
//        // Executor를 종료합니다.
//        if (executor != null) {
//            executor.shutdown();
//            try {
//                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
//                    executor.shutdownNow();
//                }
//            } catch (InterruptedException e) {
//                executor.shutdownNow();
//            }
//            executor = null;
//        }
//    }
//}

package com.yakovlevegor.DroidRec;

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
    private static final int SAMPLE_RATE = 16000;
    private final static int CHUNK_TO_READ = 10;
    private final static int CHUNK_SIZE = 6400;
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
                context.sendBroadcast(intent);  // context는 현재 클래스의 Context입니다.
                AlarmReceiver.createNotification(context);
            }

            final long inferenceTime = SystemClock.elapsedRealtime() - startTime;
            Log.d(TAG, "inference time (ms): " + inferenceTime);

            @SuppressLint("DefaultLocale") final String transcript = "FAKE (" + String.format("%.2g", score * 100) + "%)";

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

}