package com.yakovlevegor.DroidRec;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.Collections;
public class AlarmReceiver {
    private static final String CHANNEL_ID = "notify";

    public static void createNotification(Context context, String transcript) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 알림 생성
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.icons8_box_important_48)  // 알림 아이콘 설정
                .setContentTitle("Fake Detect!!")  // 알림 제목 설정
                .setContentText(transcript)  // 알림 내용 설정
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)  // 알림 클릭 시 이동할 Intent 설정
                .setAutoCancel(true);

        // 알림 표시
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(0, builder.build());
    }
}



//    @Override
//    public void onReceive(Context context, Intent intent) {
//        if ("com.example.app.ACTION_ALARM".equals(intent.getAction())) {
//            float score = intent.getFloatExtra("score", 0);
//            createNotification(context);
//        }
//    }
//public class AlarmReceiver {
//    private static final String CHANNEL_ID = "notify";
//
//    public static void createNotification(Context context, String transcript) {
//        Intent intent = new Intent(context, MainActivity.class);
//        intent.setAction("com.example.app.ACTION_MY_ACTIVITY");
//        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
//        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
//
//        // Bubble을 위한 메타데이터 생성
//        Icon icon = null;  // Bubble 아이콘 설정
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
//            icon = Icon.createWithResource(context, R.drawable.icons8_box_important_48);
//        }
//        Notification.BubbleMetadata bubbleData = null;
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
//            bubbleData = new Notification.BubbleMetadata
//                    .Builder(pendingIntent, icon)
//                    .setDesiredHeight(600)
//                    .setSuppressNotification(false)
//                    .build();
//        }
//
//        // Create ShortcutInfo
//        ShortcutInfo shortcutInfo = null;
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
//            shortcutInfo = new ShortcutInfo.Builder(context, "shortcutId")
//                    .setShortLabel("Shortcut")
//                    .setIntent(intent)
//                    .build();
//
//            ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
//            if (shortcutManager != null) {
//                shortcutManager.setDynamicShortcuts(Collections.singletonList(shortcutInfo));
//            }
//        }
//
//        // 알림 생성
//        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
//                .setSmallIcon(R.drawable.icons8_box_important_48)
//                .setContentTitle("Fake Detect!!")
//                .setContentText(transcript)
//                .setPriority(NotificationCompat.PRIORITY_HIGH)
//                .setDefaults(NotificationCompat.DEFAULT_ALL)
//                .setContentIntent(pendingIntent)
//                .setAutoCancel(true)
//                .setBubbleMetadata(NotificationCompat.BubbleMetadata.fromPlatform(bubbleData))  // 알림에 Bubble 메타데이터 설정
//                .setShortcutId("shortcutId");  // 알림에 Shortcut ID 설정
//
//        // 알림 표시
//        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
//        notificationManager.notify(0, builder.build());
//    }
//
//}