package com.simplemobiletools.voicerecorder.activities


import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class AudioProcessor(
    private val aasistModule: Module,
    private var executor: ThreadPoolExecutor?,
    private val context: Context,
    //private val mTextView: TextView
) {
    private val handler = Handler(Looper.getMainLooper())
    private val mainHandler: Handler? = null
    private var all_result = ""
    private val TAG = MainActivity::class.java.name

    //private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
    private var audioRecord: AudioRecord? = null
    private val processingThread: Thread? = null

    @Volatile
    private var isRunning = true
    private val scores = ArrayList<Float>()
    var segmentIndex = 0
    private var buffer: ByteBuffer? = null

    private fun allZero(array: ShortArray): Boolean {
        for (value in array) {
            if (value.toInt() != 0) {
                return false
            }
        }
        Log.d(ContentValues.TAG, "audiobuffer is all zero.")
        return true
    }

    fun setBuffer(buffer: ByteBuffer?) {
        this.buffer = buffer
        processBuffer()
    }

    private fun processBuffer() {
        if (buffer != null) {
            // ByteBuffer에서 short 배열로 변환합니다.
            val shortBuf = buffer!!.asShortBuffer()
            val shortArray = ShortArray(shortBuf.remaining())
            shortBuf[shortArray]

            // 변환된 short 배열을 detect() 메서드에 전달합니다.
            val doubleArray = DoubleArray(shortArray.size)
            for (i in shortArray.indices) {
                doubleArray[i] = shortArray[i] / Short.MAX_VALUE.toDouble()
            }
            val result = detect(doubleArray)
            Log.d("DetectionResult", "result: $result")
        }
    }

    fun setExecutor(executor: ThreadPoolExecutor?) {
        this.executor = executor
    }

    fun run() {
        if (executor == null) {
            executor = Executors.newFixedThreadPool(4) as ThreadPoolExecutor
        }
        executor!!.execute(Runnable {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            @SuppressLint("MissingPermission") val record = AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(ContentValues.TAG, "Audio Record can't initialize!")
                return@Runnable
            }
            record.startRecording()
            var chunkToRead = CHUNK_TO_READ
            var recordingOffset = 0
            val recordingBuffer = ShortArray(CHUNK_TO_READ * CHUNK_SIZE)
            val floatInputBuffer = DoubleArray(CHUNK_TO_READ * CHUNK_SIZE)
            while (isRunning && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val ListenBufffer = ShortArray(bufferSize)
                record.read(ListenBufffer, 0, ListenBufffer.size)
                var shortsRead: Long = 0
                val audioBuffer = ShortArray(bufferSize / 2)
                while (shortsRead < chunkToRead * CHUNK_SIZE) {
                    // for every segment of 5 chunks of data, we perform transcription
                    // each successive segment’s first chunk is exactly the preceding segment’s last chunk
                    val numberOfShort = record.read(audioBuffer, 0, audioBuffer.size)
                    //Log.d(TAG, "audioBuffer: " + Arrays.toString(audioBuffer));
                    shortsRead += numberOfShort.toLong()
                    val x = (numberOfShort - (shortsRead - chunkToRead * CHUNK_SIZE)).toInt()
                    if (shortsRead > chunkToRead * CHUNK_SIZE) System.arraycopy(
                        audioBuffer,
                        0,
                        recordingBuffer,
                        recordingOffset,
                        (numberOfShort - (shortsRead - chunkToRead * CHUNK_SIZE)).toInt()
                    ) else System.arraycopy(audioBuffer, 0, recordingBuffer, recordingOffset, numberOfShort)
                    recordingOffset += numberOfShort
                }
                val pcmFile = File(context.getExternalFilesDir(null), "input.pcm")
                try {
                    FileOutputStream(pcmFile).use { fos ->
                        val buffer = ByteBuffer.allocate(recordingBuffer.size * 2)
                        buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(recordingBuffer)
                        fos.write(buffer.array())
                    }
                } catch (e: IOException) {
                    Log.e(ContentValues.TAG, "Error writing PCM file", e)
                }
                for (i in 0 until CHUNK_TO_READ * CHUNK_SIZE) {
                    floatInputBuffer[i] = (recordingBuffer[i] / Short.MAX_VALUE.toFloat()).toDouble()
                }
                //Log.d(TAG, "floatInputBuffer: " + Arrays.toString(floatInputBuffer));
                segmentIndex++
                var result = ""
                if (!allZero(audioBuffer)) {
                    Log.d(ContentValues.TAG, "audio buffer is not all zero")
                    result = detect(floatInputBuffer)
                    if (result.length > 0) all_result = result
                }
                // PCM 파일을 WAV 파일로 변환
                val fileName = "EnvironmentalSound_mic&screenrecording_trial_5" + "_" + segmentIndex + "_" + result + ".wav"
                val wavFile = File(context.getExternalFilesDir(null), fileName)
                try {
                    WavEncoder.encodePcmToWav(pcmFile, wavFile, SAMPLE_RATE, 1, 16)
                } catch (e: IOException) {
                    Log.e(ContentValues.TAG, "Error encoding PCM to WAV", e)
                }
                chunkToRead = CHUNK_TO_READ - 1
                recordingOffset = CHUNK_SIZE
                System.arraycopy(recordingBuffer, chunkToRead * CHUNK_SIZE, recordingBuffer, 0, CHUNK_SIZE)

                //Log.d(TAG, "recordingBuffer: " + Arrays.toString(recordingBuffer));
            }
            record.stop()
            showTranslationResult("")
            record.release()
        })
    }

    private fun detect(inputBuffer: DoubleArray): String {
        Log.d(ContentValues.TAG, "run method started")
        // 배열의 길이를 가져옵니다.
        val inTensorBuffer = Tensor.allocateFloatBuffer(INPUT_SIZE)
        Log.d(ContentValues.TAG, "inputBuffer: $inputBuffer")
        for (i in 0 until inputBuffer.size - 1) {
            inTensorBuffer.put(inputBuffer[i].toFloat())
        }
        val inTensor = Tensor.fromBlob(inTensorBuffer, longArrayOf(INPUT_SIZE.toLong()))
        val startTime = SystemClock.elapsedRealtime()
        //IValue[] outputTuple;
        val score = aasistModule.forward(IValue.from(inTensor)).toTensor().dataAsFloatArray[0]
        Log.d(TAG, "score=$score")
        scores.add(score)
        val inferenceTime = SystemClock.elapsedRealtime() - startTime
        Log.d(ContentValues.TAG, "inference time (ms): $inferenceTime")
        @SuppressLint("DefaultLocale") val transcript = "FAKE " + String.format("%.3g", score * 100) + "%"
        if (transcript.length >= 0) {
            val intent = Intent("com.example.app.ACTION_ALARM")
            intent.putExtra("score", score)
            context.sendBroadcast(intent)
            AlarmReceiver.createNotification(context, transcript)
        }
        Log.d(TAG, "transcript=$transcript")
        showTranslationResult(transcript)

        // score 개수 세기
        var countGreater = 0 // 0.5 이상인 score 개수
        var countLess = 0 // 0.5 이하인 score 개수
        for (s in scores) {
            if (s >= 0.5) {
                countGreater++
            } else {
                countLess++
            }
        }
        Log.d("SCORE_COUNT", "0.5 이상: $countGreater, 0.5 이하: $countLess")
        return transcript
    }

    private fun showTranslationResult(result: String) {
        //handler.post { mTextView.text = result }
    }

    fun pause() {
        isRunning = false
        // 녹음이 진행 중일 때만 녹음을 중지합니다.
        if (audioRecord != null && audioRecord!!.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord!!.stop()
        }
    }

    fun stop() {
        isRunning = false
        // AudioRecord가 녹음 중일 때만 중지하고 해제합니다.
        if (audioRecord != null) {
            if (audioRecord!!.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord!!.stop()
            }
            audioRecord!!.release()
            audioRecord = null
        }

        // Executor가 실행 중일 때만 종료합니다.
        if (executor != null && !executor!!.isShutdown && executor!!.activeCount == 0) {
            executor!!.shutdown()
            try {
                if (!executor!!.awaitTermination(1, TimeUnit.SECONDS)) {
                    executor!!.shutdownNow()
                }
            } catch (e: InterruptedException) {
                executor!!.shutdownNow()
            }
            executor = null
        }
    }

    fun resume() {
        isRunning = true
        if (executor == null) {
            executor = Executors.newFixedThreadPool(4) as ThreadPoolExecutor
        }
        this.run()
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_TO_READ = 5
        private const val CHUNK_SIZE = 12800
        private const val INPUT_SIZE = 64000
    }
}
