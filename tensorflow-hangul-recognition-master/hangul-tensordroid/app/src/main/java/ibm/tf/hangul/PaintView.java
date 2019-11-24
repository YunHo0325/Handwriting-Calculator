package ibm.tf.hangul;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.util.Log;

import java.util.ArrayList;


public class PaintView extends View {

    // Length in pixels of each dimension for the bitmap displayed on the screen.
    // 화면에 표시되는 비트맵의 각 치수의 길이(픽셀)
    public static final int BITMAP_DIMENSION = 128;

    // Length in pixels of each dimension for the bitmap to be fed into the model.
    // 모델에 공급할 비트맵의 각 치수의 길이(픽셀)
    public static final int FEED_DIMENSION = 64;

    private boolean setup, drawHere;

    // 이미지 생성(bitmap) -> 화면에 그리기(canvas)
    private Paint paint;        // 붓(선)
    private Bitmap bitmap;      // 이미지를 표현하기 위해 사용
    private Path path;          // 궤적 정보
    private Canvas canvas;      // 그림판

    private Matrix transformMat = new Matrix();
    private Matrix inverseTransformMat = new Matrix();

    private PointF pointF = new PointF();

    private ArrayList<PaintPath> PaintPathList = new ArrayList<>();
    private View drawTextView;

    int count = 0;

    public PaintView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        path = new Path();

        paint = new Paint();
        paint.setAntiAlias(true);               // 경계면을 부드럽게
        paint.setColor(Color.WHITE);            // 선 색 : 하얀색
        paint.setStyle(Paint.Style.STROKE);     // 채우기 없이 테두리만 그려짐
        paint.setStrokeJoin(Paint.Join.ROUND);  // 선의 끝 모양이 둥근 모양
        paint.setStrokeCap(Paint.Cap.ROUND);    // 선의 끝나는 지점의 장식이 둥근 모양
        paint.setStrokeWidth(6);                // 선 굵기
    }

    /**
     * Remove all strokes and clear the canvas.
     * 비트맵(캔버스) 초기화
     */
    public void reset() {
        path.reset();
        if (bitmap != null) {
            bitmap.eraseColor(Color.BLACK);     // 바탕화면 : 검은색
        }
        count = 0;
    }

    public void setDrawText(View view) {        // 그릴 준비
        drawTextView = view;
        drawHere = true;
    }

    /**
     * This function will create the transform matrix for scaling up and centering the bitmap
     * that will represent our character image inside the view. The inverse matrix is also created
     * for mapping touch coordinates to coordinates within the bitmap.
     * 그린 image bitmap을 확대하고 중심에 배치하기 위해 변환 매트릭스 생성
     * 터치 좌표를 bitmap 내의 좌표로 매핑하기 위해 역행 매트릭스도 생성
     */
    private void setupScaleMatrices() {     //크기 행렬
        // View size.
        float width = getWidth();           // 화면 가로
        float height = getHeight();         // 화면 세로
        float scaleW = width / BITMAP_DIMENSION;
        float scaleH = height / BITMAP_DIMENSION;

        float scale = scaleW;
        // 작은 쪽에 크기 맞춤
        if (scale > scaleH) {
            scale = scaleH;
        }

        // Translation to center bitmap in view after it is scaled up.
        // 크기 조정 후 bitmap을 가운데로 두게 변환한다
        float centerX = BITMAP_DIMENSION * scale / 2;
        float centerY = BITMAP_DIMENSION * scale / 2;
        float dx = width / 2 - centerX;
        float dy = height / 2 - centerY;

        transformMat.setScale(scale, scale);    // 크기 지정
        transformMat.postTranslate(dx, dy);     // 이미지 이동
        transformMat.invert(inverseTransformMat); // 역행 매트릭스
        setup = true;
    }

    /**
     * This gets the coordinates in the bitmap based on the the coordinates of where the
     * user touched.
     * 사용자가 터치한 좌표를 기준으로 비트맵의 좌표를 얻음
     */
    public void getBitmapCoords(float x, float y, PointF out) {
        float points[] = new float[2];      // 좌표 배열
        points[0] = x;
        points[1] = y;
        inverseTransformMat.mapPoints(points);      // 찍은 좌표를 배열(행렬)로 변환
        out.x = points[0];      // 찍은 좌표
        out.y = points[1];
    }

    public void onResume() {
        createBitmap();
    }

    public void onPause() {
        releaseBitmap();
    }

    private void createBitmap() {
        if (bitmap != null) {
            bitmap.recycle();       // 비트맵 제거
        }
        bitmap = Bitmap.createBitmap(BITMAP_DIMENSION, BITMAP_DIMENSION, Bitmap.Config.ARGB_8888); // 비트맵 생성
        canvas = new Canvas(bitmap);    // 캔버스 생성
        reset();        // 비트맵 초기화
    }

    private void releaseBitmap() {
        if (bitmap != null) {
            bitmap.recycle();       // 제거
            bitmap = null;
            canvas = null;
        }
        reset();        // 비트맵 초기화
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // If this is the first time the user has touched the drawable canvas, hide the text that
        // tells the user where to draw.
        if (drawHere) {
            drawTextView.setVisibility(View.INVISIBLE);
            drawHere = false;
        }
        PaintPath paintPath = new PaintPath();
        canvas.drawPath(path, paint);       // 주어진 경로대로 그림

        // 터치 중일 때
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            getBitmapCoords(event.getX(), event.getY(), pointF);      // 좌표 가져오기
            path.moveTo(pointF.x, pointF.y);                //포커스 이동
            path.lineTo(pointF.x, pointF.y);                //선만드는거
        }
        // 움직일 때
        else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            getBitmapCoords(event.getX(), event.getY(), pointF);        // 좌표 가져오기
            if(count==0) {      // 처음 상태
                path.moveTo(pointF.x, pointF.y);
                count++;
            }
            path.lineTo(pointF.x, pointF.y);
            paintPath.setPath(path);            // paintPath.path = path
            paint.setColor(Color.WHITE);        // 선 색 : 흰색
            paintPath.setPaint(paint);          // paintPath.paint = paint
            PaintPathList.add(paintPath);       // list에 좌표 저장
        }
        invalidate();
        return true;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!setup) {       // 준비 안 되었을 때
            setupScaleMatrices();   // 매트릭스 생성
        }
        if (bitmap == null) {
            return;
        }

        c.drawBitmap(bitmap, transformMat, paint);  //Bitmap을 주어진 좌표(left, top)를 좌측 상단에 맞추어 paint로 그림
        if (PaintPathList.size() > 0) {
            canvas.drawPath(
                    PaintPathList.get(PaintPathList.size() - 1).getPath(),
                    PaintPathList.get(PaintPathList.size() - 1).getPaint());
        }
    }

    /**
     * This function will convert the bitmap pixels to usable input to our TensorFlow model.
     */
    public float[] getPixelData() {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, FEED_DIMENSION,
                FEED_DIMENSION, false);

        int width = FEED_DIMENSION;
        int height = FEED_DIMENSION;

        int[] pixels = new int[width * height];
        resizedBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        float[] returnPixels = new float[pixels.length];

        // Here we want to convert each pixel to a floating point number between 0.0 and 1.0 with
        // 1.0 being white and 0.0 being black.
        for (int i = 0; i < pixels.length; ++i) {
            int pix = pixels[i];
            int b = pix & 0xff;
            returnPixels[i] = (float) (b/255.0);
        }
        return returnPixels;
    }

    /**
     * This is an object to encapsulate the path and paint information related to drawing on the
     * bitmap/canvas.
     * 비트맵/canvas의 도면과 관련된 경로 및 도색 정보를 캡슐화하는 객체
     */
    public class PaintPath {

        Path path;
        Paint paint;

        public Path getPath() {
            return path;
        }

        public void setPath(Path path) {
            this.path = path;
        }

        public Paint getPaint() {
            return paint;
        }

        public void setPaint(Paint paint) {
            this.paint = paint;
        }
    }
}