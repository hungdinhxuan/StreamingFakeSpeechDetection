package com.simplemobiletools.voicerecorder.helpers

import android.content.Context
import android.media.MediaRecorder
import com.simplemobiletools.commons.helpers.BaseConfig
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.extensions.getDefaultRecordingsFolder

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    var hideNotification: Boolean
        get() = prefs.getBoolean(HIDE_NOTIFICATION, false)
        set(hideNotification) = prefs.edit().putBoolean(HIDE_NOTIFICATION, hideNotification).apply()

    var saveRecordingsFolder: String
        get() = prefs.getString(SAVE_RECORDINGS, context.getDefaultRecordingsFolder())!!
        set(saveRecordingsFolder) = prefs.edit().putString(SAVE_RECORDINGS, saveRecordingsFolder).apply()

    var extension: Int
        get() = prefs.getInt(EXTENSION, EXTENSION_M4A)
        set(extension) = prefs.edit().putInt(EXTENSION, extension).apply()

    var audioSource: Int
        get() = prefs.getInt(AUDIO_SOURCE, MediaRecorder.AudioSource.CAMCORDER)
        set(audioSource) = prefs.edit().putInt(AUDIO_SOURCE, audioSource).apply()

    fun getAudioSourceText(audio_source: Int) = context.getString(
        when (audio_source) {
            MediaRecorder.AudioSource.DEFAULT -> R.string.audio_source_default
            MediaRecorder.AudioSource.MIC -> R.string.audio_source_microphone
            MediaRecorder.AudioSource.VOICE_RECOGNITION -> R.string.audio_source_voice_recognition
            MediaRecorder.AudioSource.VOICE_COMMUNICATION -> R.string.audio_source_voice_communication
            MediaRecorder.AudioSource.UNPROCESSED -> R.string.audio_source_unprocessed
            MediaRecorder.AudioSource.VOICE_PERFORMANCE -> R.string.audio_source_voice_performance
            else -> R.string.audio_source_camcorder
        }
    )

    var bitrate: Int
        get() = prefs.getInt(BITRATE, DEFAULT_BITRATE)
        set(bitrate) = prefs.edit().putInt(BITRATE, bitrate).apply()

    var recordAfterLaunch: Boolean
        get() = prefs.getBoolean(RECORD_AFTER_LAUNCH, false)
        set(recordAfterLaunch) = prefs.edit().putBoolean(RECORD_AFTER_LAUNCH, recordAfterLaunch).apply()

    fun getExtensionText() = context.getString(
        when (extension) {
            EXTENSION_M4A -> R.string.m4a
            EXTENSION_OGG -> R.string.ogg_opus
            else -> R.string.mp3
        }
    )

    fun getExtension() = context.getString(
        when (extension) {
            EXTENSION_M4A -> R.string.m4a
            EXTENSION_OGG -> R.string.ogg
            else -> R.string.mp3
        }
    )

    fun getOutputFormat() = when (extension) {
        EXTENSION_OGG -> MediaRecorder.OutputFormat.OGG
        else -> MediaRecorder.OutputFormat.MPEG_4
    }

    fun getAudioEncoder() = when (extension) {
        EXTENSION_OGG -> MediaRecorder.AudioEncoder.OPUS
        else -> MediaRecorder.AudioEncoder.AAC
    }

    var useRecycleBin: Boolean
        get() = prefs.getBoolean(USE_RECYCLE_BIN, true)
        set(useRecycleBin) = prefs.edit().putBoolean(USE_RECYCLE_BIN, useRecycleBin).apply()

    var lastRecycleBinCheck: Long
        get() = prefs.getLong(LAST_RECYCLE_BIN_CHECK, 0L)
        set(lastRecycleBinCheck) = prefs.edit().putLong(LAST_RECYCLE_BIN_CHECK, lastRecycleBinCheck).apply()
}
