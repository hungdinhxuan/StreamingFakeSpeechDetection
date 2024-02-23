/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <http://unlicense.org/>
*/

package com.yakovlevegor.DroidRec;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseLongArray;
import android.content.Context;
import android.os.Build;
import android.widget.TextView;
import android.widget.Toast;
import android.media.AudioAttributes;
import android.annotation.TargetApi;
import android.media.AudioPlaybackCaptureConfiguration;

import org.pytorch.Module;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.SecurityException;
// 오디오 녹음 및 인코딩
class AudioPlaybackRecorder implements Encoder {
    private static final String TAG = AudioPlaybackRecorder.class.getSimpleName();
    private Module aasistModule;
    private ThreadPoolExecutor executor;
    private Context context;
    private TextView textView;

    // AudioProcessor의 인스턴스를 생성합니다.
    private AudioProcessor audioProcessor = new AudioProcessor(aasistModule, executor, context, textView);

    private final AudioEncoder mEncoder;
    private final HandlerThread mRecordThread;
    private RecordHandler mRecordHandler;

    private MediaProjection mProjection;
    private AudioRecord mPlayback;
    private AudioRecord mMic;

    private int mSampleRate;
    private int mChannelConfig;
    private int mFormat = AudioFormat.ENCODING_PCM_16BIT;
    private static boolean recordMicrophone;
    private static boolean recordAudio;

    private Context mainContext;

    private AtomicBoolean mForceStop = new AtomicBoolean(false);
    private AudioEncoder.Callback mCallback;
    private CallbackDelegate mCallbackDelegate;
    private int mChannelsSampleRate;

    private static int audioBufLimit = 2048;

    private boolean sourceMedia = false;
    private boolean sourceGame = false;
    private boolean sourceUnknown = false;
    private ByteBuffer buffer;

    // 생성자 코드
    AudioPlaybackRecorder(boolean microphone, boolean audio, int audioRate, int channels, MediaProjection playbackProjection, boolean useCustomCodec, String customCodec, Context ctx, boolean recordMedia, boolean recordGame, boolean recordUnknown) {
        mEncoder = new AudioEncoder(audioRate, channels, useCustomCodec, customCodec);
        mSampleRate = audioRate;
        mChannelsSampleRate = mSampleRate * 2;
        mChannelConfig = AudioFormat.CHANNEL_IN_STEREO;
        if (channels == 1) {
            mChannelConfig = AudioFormat.CHANNEL_IN_MONO;
        }
        recordMicrophone = microphone;
        mProjection = playbackProjection;
        recordAudio = audio;
        mRecordThread = new HandlerThread(TAG);
        mainContext = ctx;
        sourceMedia = recordMedia;
        sourceGame = recordGame;
        sourceUnknown = recordUnknown;
    }
    //외부에서 전달받은 callback을 설정. 오디오 녹음 및 재생의 상태 변경 등의 이벤트에 대응하기 위해 사용
    public void setCallback(Callback callback) {
        this.mCallback = (AudioEncoder.Callback) callback;
    }
    // 오디오 인코더의 콜백을 설정하는 메서드. 오디오 인코딩의 상태 변경 등의 이벤트에 대응하기 위해 사용
    public void setCallback(AudioEncoder.Callback callback) {
        this.mCallback = callback;
    }
    // 녹음을 준비하는 메서드
    public void prepare() throws IOException {
        Looper myLooper = Looper.myLooper();
        mCallbackDelegate = new CallbackDelegate(myLooper, mCallback);
        mRecordThread.start();
        mRecordHandler = new RecordHandler(mRecordThread.getLooper());
        mRecordHandler.sendEmptyMessage(MSG_PREPARE);
    }
    // 녹음 중단 메서드. 콜백 대리자와 모든 콜백 메세지 제거
    public void stop() {
        if (mCallbackDelegate != null) {
            mCallbackDelegate.removeCallbacksAndMessages(null);
        }
        mForceStop.set(true);
        if (mRecordHandler != null) mRecordHandler.sendEmptyMessage(MSG_STOP);
    }
    // 녹음 쓰레드 종료
    public void release() {
        if (mRecordHandler != null) mRecordHandler.sendEmptyMessage(MSG_RELEASE);
        mRecordThread.quitSafely();
    }
    // 지정된 인덱스의 출력 버퍼를 해제하는 메서드.
    void releaseOutputBuffer(int index) {
        Message.obtain(mRecordHandler, MSG_RELEASE_OUTPUT, index, 0).sendToTarget();
    }
    // 지정돈 인덱스의 출력버퍼를 가져오는 메서드
    ByteBuffer getOutputBuffer(int index) {
        ByteBuffer buffer = mEncoder.getOutputBuffer(index);
        if (buffer != null) {
            byte[] byteArray = new byte[buffer.remaining()];
            buffer.get(byteArray);
            byte[] firstTenBytes = Arrays.copyOfRange(byteArray, 0, Math.min(10, byteArray.length));
            //Log.d("buffer", "audioBuffer: " + Arrays.toString(firstTenBytes));

            // AudioProcessor에 버퍼의 내용을 전달합니다.
            //audioProcessor.setBuffer(buffer);
        }
        return buffer;
    }


    private static class CallbackDelegate extends Handler {
        private AudioEncoder.Callback mCallback;
        // 생성자
        CallbackDelegate(Looper l, AudioEncoder.Callback callback) {
            super(l);
            this.mCallback = callback;
        }

        // 인코딩 과정에서 오류 시 처리
        void onError(Encoder encoder, Exception exception) {
            Message.obtain(this, new Runnable() {
                public void run() {
                    if (mCallback != null) {
                        mCallback.onError(encoder, exception);
                    }
                }
            }).sendToTarget();
        }
        // 출력 형식이 변경되면 처리하는 메서드
        void onOutputFormatChanged(AudioEncoder encoder, MediaFormat format) {
             Message.obtain(this, new Runnable() {
                public void run() {
                    if (mCallback != null) {
                        mCallback.onOutputFormatChanged(encoder, format);
                    }
                }
            }).sendToTarget();
        }
        // 출력 버퍼가 사용 가능해지면 이를 처리하는 메서드
        void onOutputBufferAvailable(AudioEncoder encoder, int index, MediaCodec.BufferInfo info) {
              Message.obtain(this, new Runnable() {
                public void run() {
                    if (mCallback != null) {
                        mCallback.onOutputBufferAvailable(encoder, index, info);
                    }
                }
            }).sendToTarget();
        }

    }

    private static final int MSG_PREPARE = 0;
    private static final int MSG_FEED_INPUT = 1;
    private static final int MSG_DRAIN_OUTPUT = 2;
    private static final int MSG_RELEASE_OUTPUT = 3;
    private static final int MSG_STOP = 4;
    private static final int MSG_RELEASE = 5;

    // 오디오 녹음 및 재생과 관련된 메세지를 처리하는 클래스
    private class RecordHandler extends Handler {
        private LinkedList<MediaCodec.BufferInfo> mCachedInfos = new LinkedList<>();
        private LinkedList<Integer> mMuxingOutputBufferIndices = new LinkedList<>();
        private int mPollRate = 2048_000 / mSampleRate;

        RecordHandler(Looper l) {
            super(l);
        }
        // 전달받은 메세지를 처리하는 역할
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_PREPARE) {
                AudioRecord r;
                AudioRecord m;
                if (recordAudio == true) {
                    Log.d(TAG, "error1");
                    r = createAudioRecord(mSampleRate, mChannelConfig, AudioFormat.ENCODING_PCM_16BIT, mProjection);
                    if (r == null) {
                        Log.d(TAG, "error2");
                        mCallbackDelegate.onError(AudioPlaybackRecorder.this, new IllegalArgumentException());
                    } else {
                        Log.d(TAG, "error3");
                        r.startRecording();
                        mPlayback = r;
                    }
                }
                if (recordMicrophone == true) {
                    Log.d(TAG, "error4");
                    m = createMicRecord(mSampleRate, mChannelConfig, mFormat);
                    if (m == null) {
                        Log.d(TAG, "error5");
                        mCallbackDelegate.onError(AudioPlaybackRecorder.this, new IllegalArgumentException());
                        Log.d(TAG, "Failed to micrecord");
                    } else {
                        Log.d(TAG, "error6");
                        m.startRecording();
                        mMic = m;
                    }
                }
                try {
                    Log.d(TAG, "error7");
                    mEncoder.prepare();
                } catch (Exception e) {
                    Log.d(TAG, "error8");
                    mCallbackDelegate.onError(AudioPlaybackRecorder.this, e);
                }
            } else if (msg.what == MSG_DRAIN_OUTPUT) {
                offerOutput();
                pollInputIfNeed();
            } else if (msg.what == MSG_RELEASE_OUTPUT) {
                mEncoder.releaseOutputBuffer(msg.arg1);
                mMuxingOutputBufferIndices.poll();
                pollInputIfNeed();
            } else if (msg.what == MSG_STOP) {
                Log.d(TAG, "error11");
                if (recordAudio == true && mPlayback != null) {
                    Log.d(TAG, "error12");
                    mPlayback.stop();
                }
                if (recordMicrophone == true && mMic != null) {
                    Log.d(TAG, "error13");
                    mMic.stop();
                }
                Log.d(TAG, "error14");
                mEncoder.stop();
            } else if (msg.what == MSG_RELEASE) {
                Log.d(TAG, "error15");
                if (mPlayback != null) {
                    mPlayback.release();
                    Log.d(TAG, "error16");
                    mPlayback = null;
                }
                if (recordMicrophone == true) {
                    mMic.release();
                    Log.d(TAG, "error17");
                    mMic = null;
                }
                mEncoder.release();
            }

            if (msg.what == MSG_PREPARE || msg.what == MSG_FEED_INPUT) {
                if (!mForceStop.get()) {
                    int index = pollInput();
                    if (index >= 0) {
                        feedAudioEncoder(index);
                        if (!mForceStop.get()) sendEmptyMessage(MSG_DRAIN_OUTPUT);
                    } else {
                        sendEmptyMessageDelayed(MSG_FEED_INPUT, mPollRate);
                    }
                }
            }

        }

        private void offerOutput() {
            while (!mForceStop.get()) {
                MediaCodec.BufferInfo info = mCachedInfos.poll();
                if (info == null) {
                    info = new MediaCodec.BufferInfo();
                }
                int index = mEncoder.mEncoder.dequeueOutputBuffer(info, 1);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    mCallbackDelegate.onOutputFormatChanged(mEncoder, mEncoder.mEncoder.getOutputFormat());
                }
                if (index < 0) {
                    info.set(0, 0, 0, 0);
                    mCachedInfos.offer(info);
                    break;
                }
                mMuxingOutputBufferIndices.offer(index);
                mCallbackDelegate.onOutputBufferAvailable(mEncoder, index, info);

            }
        }

        private int pollInput() {
            return mEncoder.mEncoder.dequeueInputBuffer(0);
        }

        private void pollInputIfNeed() {
            if (mMuxingOutputBufferIndices.size() <= 1 && !mForceStop.get()) {
                removeMessages(MSG_FEED_INPUT);
                sendEmptyMessageDelayed(MSG_FEED_INPUT, 0);
            }
        }
    }

    private void feedAudioEncoder(int index) {
        if (index < 0 || mForceStop.get()) return;
        AudioRecord r = mPlayback;
        if (recordAudio == false && recordMicrophone == true) {
            r = mMic;
        }
        final boolean eos = r.getRecordingState() == AudioRecord.RECORDSTATE_STOPPED;
        final ByteBuffer frame = mEncoder.getInputBuffer(index);
        int offset = frame.position();
        int limit = audioBufLimit;
        int read = 0;
        int readmic = 0;

        if (!eos) {
            final byte[] frameplayback = new byte[limit];
            read = r.read(frameplayback, 0, limit);
            if (recordMicrophone == true && recordAudio == true) {
                final byte[] framemic = new byte[limit];
                final AudioRecord m = mMic;
                readmic = m.read(framemic, 0, limit);
                int readcorrect = readmic;
                if (read < readmic) {
                    readcorrect = read;
                }
                for (int i = 0; i < readcorrect; i++) {
                    frameplayback[i] = (byte)(frameplayback[i] + framemic[i]);
                }
            }
            frame.put(frameplayback);
            if (read < 0) {
                read = 0;
            }
        }

        long pstTs = calculateFrameTimestamp(read << 3);
        int flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;

        if (eos) {
            flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
        }

        mEncoder.queueInputBuffer(index, offset, read, pstTs, flags);
    }


    private static final int LAST_FRAME_ID = -1;
    private SparseLongArray mFramesUsCache = new SparseLongArray(2);

    private long calculateFrameTimestamp(int totalBits) {
        int samples = totalBits >> 4;
        long frameUs = mFramesUsCache.get(samples, -1);
        if (frameUs == -1) {
            frameUs = samples * 1000_000 / mChannelsSampleRate;
            mFramesUsCache.put(samples, frameUs);
        }
        long timeUs = SystemClock.elapsedRealtimeNanos() / 1000;
        timeUs -= frameUs;
        long currentUs;
        long lastFrameUs = mFramesUsCache.get(LAST_FRAME_ID, -1);
        if (lastFrameUs == -1) {
            currentUs = timeUs;
        } else {
            currentUs = lastFrameUs;
        }
        if (timeUs - currentUs >= (frameUs << 1)) {
            currentUs = timeUs;
        }
        mFramesUsCache.put(LAST_FRAME_ID, currentUs + frameUs);
        return currentUs;
    }

    // 오디오 녹음에 필요한 설정을 구성하고 이를 바탕으로 AudioRecord 객체를 생성하는 역할을 한다.
    @TargetApi(Build.VERSION_CODES.Q)
    private AudioRecord createAudioRecord(int sampleRateInHz, int channelConfig, int audioFormat, MediaProjection captureProjection) {
        AudioFormat captureAudioFormat = new AudioFormat.Builder() // 오디오 녹음에 사용할 객체 생성
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRateInHz)
            .setChannelMask(channelConfig)
            .build();

        AudioPlaybackCaptureConfiguration.Builder configBuilder = new AudioPlaybackCaptureConfiguration.Builder(captureProjection);

        if (sourceMedia == false && sourceGame == false && sourceUnknown == false) {
            configBuilder = configBuilder.addMatchingUsage(AudioAttributes.USAGE_MEDIA);
        }

        if (sourceMedia == true) {
            configBuilder = configBuilder.addMatchingUsage(AudioAttributes.USAGE_MEDIA);
        }

        if (sourceGame == true) {
            configBuilder = configBuilder.addMatchingUsage(AudioAttributes.USAGE_GAME);
        }

        if (sourceUnknown == true) {
            configBuilder = configBuilder.addMatchingUsage(AudioAttributes.USAGE_UNKNOWN);
        }

        AudioPlaybackCaptureConfiguration config = configBuilder.build();

        AudioRecord record = null;

        try {
            record = new AudioRecord.Builder()
                .setAudioFormat(captureAudioFormat)
                .setBufferSizeInBytes(audioBufLimit)
                .setAudioPlaybackCaptureConfig(config)
                .build();
        } catch (Exception e) {
            Toast.makeText(mainContext, R.string.error_playback_not_allowed, Toast.LENGTH_SHORT).show();
            return null;
        }

        if (record.getState() == AudioRecord.STATE_UNINITIALIZED) {
            return null;
        }
        return record;
    }
    // 마이크를 통한 녹음
    private static AudioRecord createMicRecord(int sampleRateInHz, int channelConfig, int audioFormat) {
        AudioRecord record = null;

        try {
            record = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRateInHz, channelConfig, AudioFormat.ENCODING_PCM_16BIT, audioBufLimit);
        } catch (SecurityException e) {
            return null;
        }

        if (record.getState() == AudioRecord.STATE_UNINITIALIZED) {
            return null;
        }
        return record;
    }

}
