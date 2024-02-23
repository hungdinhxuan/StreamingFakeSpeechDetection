package com.simplemobiletools.voicerecorder.activities

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException


internal object WavEncoder {
    private const val HEADER_SIZE = 44

    // WAV 파일에 헤더 정보를 추가하여 저장하는 메서드
    @Throws(IOException::class)
    fun encodePcmToWav(pcmFile: File?, wavFile: File?, sampleRate: Int, channels: Int, bitPerSample: Int) {
        val inputStream = FileInputStream(pcmFile)
        val outputStream = FileOutputStream(wavFile)
        val totalAudioLen = inputStream.channel.size()
        val totalDataLen = totalAudioLen + HEADER_SIZE
        val longSampleRate = sampleRate.toLong()
        val byteRate = (bitPerSample * sampleRate * channels / 8).toLong()
        val header = ByteArray(HEADER_SIZE)
        header[0] = 'R'.code.toByte() // RIFF/WAVE header
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xffL).toByte()
        header[5] = (totalDataLen shr 8 and 0xffL).toByte()
        header[6] = (totalDataLen shr 16 and 0xffL).toByte()
        header[7] = (totalDataLen shr 24 and 0xffL).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1 for PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xffL).toByte()
        header[25] = (longSampleRate shr 8 and 0xffL).toByte()
        header[26] = (longSampleRate shr 16 and 0xffL).toByte()
        header[27] = (longSampleRate shr 24 and 0xffL).toByte()
        header[28] = (byteRate and 0xffL).toByte()
        header[29] = (byteRate shr 8 and 0xffL).toByte()
        header[30] = (byteRate shr 16 and 0xffL).toByte()
        header[31] = (byteRate shr 24 and 0xffL).toByte()
        header[32] = (2 * 16 / 8).toByte() // block align
        header[33] = 0
        header[34] = bitPerSample.toByte() // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xffL).toByte()
        header[41] = (totalAudioLen shr 8 and 0xffL).toByte()
        header[42] = (totalAudioLen shr 16 and 0xffL).toByte()
        header[43] = (totalAudioLen shr 24 and 0xffL).toByte()
        outputStream.write(header, 0, HEADER_SIZE)
        val data = ByteArray(1024)
        var bytesRead: Int
        while (inputStream.read(data).also { bytesRead = it } != -1) {
            outputStream.write(data, 0, bytesRead)
        }
        inputStream.close()
        outputStream.close()
    }
}
