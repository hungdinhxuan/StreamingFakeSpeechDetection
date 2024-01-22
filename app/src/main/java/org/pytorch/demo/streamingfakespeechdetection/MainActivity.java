package org.pytorch.demo.streamingfakespeechdetection;

        import  android.Manifest;
        import android.annotation.SuppressLint;
        import android.content.Context;
        import android.content.pm.PackageManager;
        import android.media.MediaCodec;
        import android.media.MediaExtractor;
        import android.media.MediaFormat;
        import android.net.Uri;
        import android.content.Intent;
        import android.os.AsyncTask;
        import android.os.Build;
        import android.os.Bundle;
        import android.os.Handler;
        import android.os.Looper;
        import android.os.SystemClock;
        import android.util.Log;
        import android.widget.Button;
        import android.widget.ProgressBar;
        import android.widget.TextView;
        import android.widget.Toast;
        import android.view.View;
        import android.widget.ProgressBar;

        import androidx.activity.result.ActivityResultLauncher;
        import androidx.activity.result.contract.ActivityResultContracts;
        import androidx.annotation.NonNull;
        import androidx.appcompat.app.AppCompatActivity;

        import org.pytorch.IValue;
        import org.pytorch.LiteModuleLoader;
        import org.pytorch.Module;
        import org.pytorch.Tensor;

        import java.io.File;
        import java.io.IOException;
        import java.io.InputStream;
        import java.io.OutputStream;
        import java.nio.ByteBuffer;
        import java.nio.FloatBuffer;
        import java.nio.file.Files;
        import java.util.ArrayList;
        import java.util.concurrent.ExecutionException;
        import java.util.concurrent.ExecutorService;
        import java.util.concurrent.Executors;
        import java.util.concurrent.Future;

public class MainActivity extends AppCompatActivity{
    private static final String TAG = MainActivity.class.getName();
    private static final String SCORE_TAG = "FAKE %: ";
    private static final int PICK_AUDIO_REQUEST_CODE = 1;
    private static final int PERMISSION_REQUEST_CODE = 1;

    private Module aasistModule;
    private TextView mTextView;
    private Button mButton;
    private final String all_result = "";

    private final static int INPUT_SIZE = 16000;
    private Handler handler;
    private ActivityResultLauncher<String> mGetContent;
    private Integer format;


    private ProgressBar mProgressBar;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mProgressBar = findViewById(R.id.progressBar);
        handler = new Handler(Looper.getMainLooper());

        mButton = findViewById(R.id.btnRecognize); // select file
        mTextView = findViewById(R.id.tvResult); // Fake(0.0%)

        if (aasistModule == null) {
            System.out.println("Loading model...");
            aasistModule = LiteModuleLoader.load(assetFilePath(getApplicationContext(), "btsdetect_cnn_1s.ptl"));
            System.out.println("Loaded model aasistModule");
        }

        ActivityResultLauncher<String> mGetContent = registerForActivityResult(
                new ActivityResultContracts.GetContent(), uri -> {
                    // uri를 처리하는 코드를 여기에 작성합니다.
                    if (uri != null) {
                        processAudioFile(uri); // float input, review to see how the model receive input.
                        processAndShowResult(uri);
                    }
                });
        mButton.setOnClickListener(v -> {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                // 권한이 없는 경우, 권한 요청
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissions(new String[]{Manifest.permission.READ_MEDIA_AUDIO}, PERMISSION_REQUEST_CODE);
                }
            } else {
                // 권한이 있는 경우, 파일 선택
                mGetContent.launch("audio/*");
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 권한 허용된 경우, 파일 선택
                mGetContent.launch("audio/*");
            } else {
                // 권한 거부된 경우, 경고 메시지 표시
                Toast.makeText(this, "권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private String assetFilePath(Context context, String assetName) {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = Files.newOutputStream(file.toPath())) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, assetName + ": " + e.getLocalizedMessage());
        }
        return null;
    }

    private void showTranslationResult(String result) {
        mTextView.setText(result);
    }

    // onActivityResult에서 사용자가 선택한 파일의 URI를 받아옵니다.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_AUDIO_REQUEST_CODE && resultCode == RESULT_OK) {
            Uri audioUri = data.getData();
            processAudioFile(audioUri); // float input, review to see how the model receive input.
            processAndShowResult(audioUri);
        }
    }

    // 오디오 파일을 처리하는 메서드
//    private void processAndShowResult(Uri audioUri) {
//        handler.post(() -> mProgressBar.setVisibility(View.VISIBLE));
//
//        float[] pcmData = processAudioFile(audioUri);
//        if (pcmData != null) {
//            String result = detect(pcmData);
//            showTranslationResult(result);
//            handler.post(() -> mProgressBar.setVisibility(View.GONE));
//        }
//    }
    private void processAndShowResult(Uri audioUri) {
        // 처리 시작 전에 ProgressBar를 보여줍니다.
        handler.post(() -> mProgressBar.setVisibility(View.VISIBLE));

        // 오디오 파일 처리 코드를 백그라운드에서 실행합니다.
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            // 오디오 파일을 처리합니다.
            float[] pcmData = processAudioFile(audioUri);
            String result = detect(pcmData);

            // 처리 작업이 완료되면 ProgressBar를 숨기고, 결과를 보여줍니다.
            handler.post(() -> {
                mProgressBar.setVisibility(View.GONE);
                showTranslationResult(result);  // 결과를 보여주는 메서드. 이 부분은 실제 코드에 맞게 수정해야 합니다.
            });
        });
    }

    private float[] processAudioFile(Uri audioUri) {
        MediaExtractor extractor = new MediaExtractor();
        ArrayList<Float> floatInputBuffer = new ArrayList<>();
        try {
            extractor.setDataSource(this, audioUri, null); // 데이터 소스 설정
            int audioTrackIdx = findAudioTrack(extractor); // 오디오 트랙찾기
            if (audioTrackIdx != -1) {
                extractor.selectTrack(audioTrackIdx);

                MediaFormat format = extractor.getTrackFormat(audioTrackIdx);
                String mime = format.getString(MediaFormat.KEY_MIME);
                MediaCodec codec = MediaCodec.createDecoderByType(mime);
                codec.configure(format, null, null, 0);
                codec.start();

                ByteBuffer[] inputBuffers = codec.getInputBuffers();
                ByteBuffer[] outputBuffers = codec.getOutputBuffers();

                final long kTimeOutUs = 10000;
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                boolean sawInputEOS = false;
                boolean sawOutputEOS = false;

                while (!sawOutputEOS) {
                    if (!sawInputEOS) {
                        int inputBufIndex = codec.dequeueInputBuffer(kTimeOutUs);
                        if (inputBufIndex >= 0) {
                            ByteBuffer dstBuf = inputBuffers[inputBufIndex];
                            int sampleSize = extractor.readSampleData(dstBuf, 0);
                            long presentationTimeUs = 0;
                            if (sampleSize < 0) {
                                sawInputEOS = true;
                                sampleSize = 0;
                            } else {
                                presentationTimeUs = extractor.getSampleTime();
                            }
                            codec.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs, sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                            if (!sawInputEOS) {
                                extractor.advance();
                            }
                        }
                    }

                    int outputBufIndex = codec.dequeueOutputBuffer(info, kTimeOutUs);
                    if (outputBufIndex >= 0) {
                        ByteBuffer buf = outputBuffers[outputBufIndex];
                        final byte[] chunk = new byte[info.size];
                        buf.get(chunk);
                        buf.clear();
                        if (chunk.length > 0) {
                            //chunk에 담긴 16비트 PCM 형식의 데이터를 처리.
                            for (int i = 0; i < chunk.length; i += 2) {
                                // byte를 short로 변환
                                short audioSample = (short)((chunk[i] & 0xff) | (chunk[i+1] << 8));
                                // short를 float로 변환, floatInputBuffer에 추가
                                floatInputBuffer.add((float)audioSample / 32768.0f);
                            }
                        }
                        codec.releaseOutputBuffer(outputBufIndex, false);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEOS = true;
                        }
                    }
                }
                codec.stop();
                codec.release();

                float[] pcmData = new float[floatInputBuffer.size()];
                for (int i = 0; i < floatInputBuffer.size(); i++) {
                    pcmData[i] = floatInputBuffer.get(i);
                }
                return pcmData;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            extractor.release();
        }
        return null;
    }

    // 오디오 트랙을 찾는 메서드
    private int findAudioTrack(MediaExtractor extractor) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            Log.d("DEBUG", "Track format: " + format);
            int sampleRate = 0;
            try {
                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            } catch (NullPointerException e) {
                Log.e("DEBUG", "Sample rate not found in MediaFormat");
            }
            Log.d("DEBUG", "SampleRate: " + sampleRate);

            if (sampleRate == 0) {
                Log.e("DEBUG", "Sample rate not found in MediaFormat");
            }
            Log.d("DEBUG", "SampleRate: " + sampleRate);

            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                return i;
            }
        }
        return -1;
    }

    private String detect(float[] pcmData) {
        FloatBuffer inTensorBuffer = Tensor.allocateFloatBuffer(INPUT_SIZE);
        for (int i = 0; i < INPUT_SIZE; i++) {
            inTensorBuffer.put(pcmData[i]);
        }

        final Tensor inTensor = Tensor.fromBlob(inTensorBuffer, new long[]{1, INPUT_SIZE});
        final long startTime = SystemClock.elapsedRealtime();
        IValue[] outputTuple;

        final float score = aasistModule.forward(IValue.from(inTensor)).toTensor().getDataAsFloatArray()[0];
        Log.d(SCORE_TAG, "score=" + score);

        final long inferenceTime = SystemClock.elapsedRealtime() - startTime;
        Log.d(TAG, "inference time (ms): " + inferenceTime);

        @SuppressLint("DefaultLocale") final String transcript = "FAKE (" + String.format("%.2f", score * 100) + "%)";

        Log.d(SCORE_TAG, "transcript=" + transcript);

        return transcript;
    }

}

