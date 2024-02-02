package com.yakovlevegor.DroidRec;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class AlarmReceiver {
    private static final String CHANNEL_ID = "notify";

    public static void createNotification(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            // 알림 생성
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.icons8_box_important_48)  // 알림 아이콘 설정
                .setContentTitle("Fake Detect!!")  // 알림 제목 설정
                .setContentText("Your audio is fake.")  // 알림 내용 설정
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)  // 알림 클릭 시 이동할 Intent 설정
                .setAutoCancel(true);

        // 알림 표시
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(0, builder.build());
        }

//    @Override
//    public void onReceive(Context context, Intent intent) {
//        if ("com.example.app.ACTION_ALARM".equals(intent.getAction())) {
//            float score = intent.getFloatExtra("score", 0);
//            createNotification(context);
//        }
//    }
}
