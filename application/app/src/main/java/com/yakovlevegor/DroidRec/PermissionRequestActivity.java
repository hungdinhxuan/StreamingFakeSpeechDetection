//package com.yakovlevegor.DroidRec;
//
//import static android.content.ContentValues.TAG;
//
//import android.app.Activity;
//import android.content.Context;
//import android.content.Intent;
//import android.media.projection.MediaProjection;
//import android.media.projection.MediaProjectionManager;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.activity.result.ActivityResultLauncher;
//import androidx.activity.result.contract.ActivityResultContracts;
//import android.os.Bundle;
//import android.util.Log;
//
//
//public class PermissionRequestActivity extends AppCompatActivity {
//    private static final int REQUEST_CODE = 1000;
//    private MediaProjectionManager projectionManager;
//    private ScreenRecorder screenRecorder;
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//
//        // MediaProjectionManager 객체를 얻습니다.
//        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
//
//        // 화면 캡처를 허용하는 인텐트를 얻습니다.
//        Intent intent = projectionManager.createScreenCaptureIntent();
//
//        // 결과를 받을 ActivityResultLauncher를 생성하고 등록합니다.
//        ActivityResultLauncher<Intent> launcher = registerForActivityResult(
//                new ActivityResultContracts.StartActivityForResult(),
//                result -> {
//                    if (result.getResultCode() == Activity.RESULT_OK) {
//                        // MediaProjection을 시작합니다.
//                        MediaProjection mediaProjection = projectionManager.getMediaProjection(result.getResultCode(), result.getData());
//
//                        screenRecorder.screenRecordingStart();
//                        Log.d(TAG, "permission드디어 가져왔따");
//                    } else {
//                        Log.d(TAG, "permission 못가져왔따 ...");
//                    }
//
//                    // 권한 요청이 끝나면 액티비티를 종료합니다.
//                    finish();
//                }
//        );
//
//        // 인텐트를 사용하여 사용자에게 권한 요청을 합니다.
//        launcher.launch(intent);
//
//    }
//}
