package com.simplemobiletools.voicerecorder.activities

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.simplemobiletools.voicerecorder.R


object AlarmReceiver {
    private const val CHANNEL_ID = "notify"
    fun createNotification(context: Context?, transcript: String?) {
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // 알림 생성
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(context!!, CHANNEL_ID)
            //.setSmallIcon(R.drawable.icons8_box_important_48) // 알림 아이콘 설정
            .setContentTitle("Fake Detect!!") // 알림 제목 설정
            .setContentText(transcript) // 알림 내용 설정
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent) // 알림 클릭 시 이동할 Intent 설정
            .setAutoCancel(true)

        // 알림 표시
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(0, builder.build())
    }
}
