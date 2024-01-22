//package org.pytorch.demo.streamingfakespeechdetection;
//
//
//import android.content.Context;
//import android.graphics.Canvas;
//import android.graphics.Color;
//import android.graphics.Paint;
//import android.util.AttributeSet;
//import android.view.View;
//import java.util.ArrayList;
//import java.util.List;
//
//public class VisualizerView extends View {
//
//    private static final int BAR_WIDTH = 10;
//    private static final int VALUE_TO_HEIGHT_SCALE = 10;
//    private static final int SPACE_BETWEEN_BARS = 5;
//
//    private short[] audioData;
//    private Paint paint;
//    private short[] prevAudioData;
//    private List<Integer> graphData;
//    private long timestamp; // 추가된 부분
//
//    public VisualizerView(Context context, AttributeSet attrs) {
//        super(context, attrs);
//        init();
//    }
//
//
//    private void init() {
//        paint = new Paint();
//        paint.setColor(Color.BLUE);
//        paint.setStrokeWidth(1);
//        paint.setStyle(Paint.Style.FILL);
//        paint.setAntiAlias(true);
//        graphData = new ArrayList<>();
//        timestamp = System.currentTimeMillis(); // 초기 타임 스템프 설정
//    }
//
//
//    public void updateVisualizer(short[] audioData) {
//        // Clone the audioData array to avoid modifying the original array
//        short[] clonedData = audioData.clone();
//        // Scale and add each value to the graphData list
//        for (int i = 0; i < clonedData.length; i++) {
//            int scaledValue = clonedData[i] / VALUE_TO_HEIGHT_SCALE;
//            //graphData.add(clonedData[i]);
//            graphData.add(scaledValue);
//            // Remove the oldest data if the graphData list exceeds the screen width
//            if (graphData.size() >= getWidth()) {
//                graphData.remove(0);
//            }
//        }
//        System.out.println(graphData);
//        // Invalidate the view to trigger onDraw and update the visualization
//        invalidate();
//    }
//
//
//    @Override
//    protected void onDraw(Canvas canvas) {
//        super.onDraw(canvas);
//
//        if (graphData == null) {
//            return;
//        }
//        int graphHeight = getHeight() / 2;
//        canvas.drawLine(0, graphHeight, getWidth(), graphHeight, paint);
//        if (!graphData.isEmpty()) {
//            for (int i = 0; i < graphData.size() - 1; i++) {
//                canvas.drawLine(
//                        getWidth() - graphData.size() + i,
//                        graphHeight - graphData.get(i),
//                        getWidth() - graphData.size() + i,
//                        graphHeight + graphData.get(i),
//                        paint);
//            }
//
//
//
//            // 타임 스템프 표시
//            long currentTime = System.currentTimeMillis();
//            long elapsedTime = currentTime - timestamp;
//            float elapsedSeconds = elapsedTime / 1000f; // 밀리초(ms)를 초로 변환
//
//            String timestampText = "Elapsed Time: " + elapsedSeconds + " seconds";
//
//            // 원하는 위치에 텍스트를 표시할 수 있도록 조정
//            float textX = 20;
//            float textY = 50;
//            paint.setTextSize(40);
//            canvas.drawText(timestampText, textX, textY, paint);
//
//        }
//
//
//
//
//    }
//}
//
//
