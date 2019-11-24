 package ibm.tf.hangul;

import android.annotation.SuppressLint;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Timer;
import java.util.TimerTask;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.os.Handler;

import java.util.HashMap;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String LABEL_FILE = "label.txt";
    private static final String MODEL_FILE = "optimized_hangul_tensorflow.pb";

    private HangulClassifier classifier;
    private PaintView paintView;
    private Button alt1, alt2, alt3, alt4;
    private LinearLayout altLayout;
    private EditText resultText;
    private TextView translationText;
    private String[] currentTopLabels;
    private TimerTask tt;
    private Timer timer;

    static int counter = 0;     // 자동인식 위한 counter

    /**
     * This is called when the application is first initialized/started. Basic setup logic is
     * performed here.
     *
     * @param savedInstanceState Bundle
     */
    //Lint : 빌드나 컴파일 시 에러 외에 추가로 자잘한 오류검사를 할 수 있는 도구
    //@SuppressLint : 자바 클래스 또는 메서드에 대해서만 Lint 검사를 비활성
    @SuppressLint("ClickableViewAccessibility")     // ClickableViewAccessibility에 대한 검사 금지
    // activity가 종료 된 경우 null이 아닌 savedInstanceState에서 데이터 가져와서 활동을 다시 생성 가능
    // R.java : 리소스 관리자, 안드로이드에서 생성하는 모든 리소스에 자동으로 아이디를 부여
    // 이미지를 넣거나, 레이아웃을 생성하거나, View에 아이디를 부여하는 등의 모든 리소스 작업들이 "R" 클래스 내부에 자동으로 아이디를 생성하게 됨
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);     //activity_main.xml 호출 -> 화면에 띄움

        //findViewById : PaintView(TextView, Button)의 참조를 가져옴, 레이아웃에 설정된 뷰들을 가져옴
        paintView = (PaintView) findViewById(R.id.paintView);

        TextView drawHereText = (TextView) findViewById(R.id.drawHere);
        paintView.setDrawText(drawHereText);                  //"Draw Text"

        Button clearButton = (Button) findViewById(R.id.buttonClear);   //모두 지우는 버튼
        clearButton.setOnClickListener(this);       // 버튼 눌렀을 때 이벤트 - 자세한 동작은 onClick()에

        Button classifyButton = (Button) findViewById(R.id.buttonClassify);     // 분류하는 버튼
        classifyButton.setOnClickListener(this);

        Button backspaceButton = (Button) findViewById(R.id.buttonBackspace);   // 한칸 지우는 버튼
        backspaceButton.setOnClickListener(this);

        Button canvasclear = (Button) findViewById(R.id.buttonredraw);   // 캔버스 지우는 버튼
        canvasclear.setOnClickListener(this);

        Button submitButton = (Button) findViewById(R.id.buttonSubmit); // 계산하는 버튼
        submitButton.setOnClickListener(this);

        altLayout = (LinearLayout) findViewById(R.id.altLayout);    // 확률에 따라 결과 나오는 부분
        altLayout.setVisibility(View.INVISIBLE);        // 레이아웃 나타내기 - 보이지는 않지만 영역은 차지함

        alt1 = (Button) findViewById(R.id.alt1);
        alt1.setOnClickListener(this);
        alt2 = (Button) findViewById(R.id.alt2);
        alt2.setOnClickListener(this);
        alt3 = (Button) findViewById(R.id.alt3);
        alt3.setOnClickListener(this);
        alt4 = (Button) findViewById(R.id.alt4);
        alt4.setOnClickListener(this);

        translationText = (TextView) findViewById(R.id.translationText);    // 계산 결과 나오는 부분
        resultText = (EditText) findViewById(R.id.editText);        // 연산식

        // 그리는 곳 터치했을 시의 돋작들
        paintView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    //눌렀을 때
                    Timer();            // 타이머1 작동
                    paintView.onTouchEvent(event);  // onTouchEvent() 메소드 실행
                    counter++;          // 자동 인식위한 counter++
                    ClassifyTimer();       // 타이머2 작동
                }
                else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    //움직이고 있을 때
                    paintView.onTouchEvent(event);
                }
                else if (event.getAction() == MotionEvent.ACTION_UP) {
                    long startTime = System.currentTimeMillis();        // 손가락 떼어었을 시각
                    float pixels[] = paintView.getPixelData();          // 그림판의 PixelData를 float형 배열로 가져옴
                    currentTopLabels = classifier.classify(pixels);     // 분류 결과로 5개 label 가져옴
                    Log.v("currentTopLabels", "TopLables : "+currentTopLabels[0]);

                    // 자동으로 연산식에 추가
                    if(counter >= 3)
                    {
                        Log.v("counter", "counter : "+counter);
                        Log.v("motion", "Action up");
                        SystemClock.sleep(700);
                        classify();                 // 분류 결과 띄움
                        paintView.reset();          // 그림판 검은색으로 지움
                        paintView.invalidate();     // 그림판 갱신(onDraw() 다시 불러오기 위해서)
                        counter=0;      // counter 초기화
                    }
                    Log.v("counter", "counter : "+counter);
                }

                // 이벤트 처리를 여기서 완료했을 때
                // 다른곳에 이벤트를 넘기지 않도록
                // 리턴값을 true 로 준다
                // 리턴값이 있음
                return true;
            }
        });

        loadModel();        // 모델load( tensorflow interface 생성 )
    }


    /**
     * This method is called when the user clicks a button in the view.
     * 버튼 클릭 했을 때 call
     * @param view
     */
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.buttonClear:      // 초기화 버튼
                clear();                // 모두 초기화
                counter=0;
                break;

            case R.id.buttonClassify:   // 분류 버튼
                classify();             // 분류 결과 띄움
                paintView.reset();
                paintView.invalidate();
                counter=0;
                break;

            case R.id.buttonBackspace:  // 하나 지우는 버튼
                backspace();            // 한 칸 지움
                altLayout.setVisibility(View.INVISIBLE);    // 확률 레이아웃 안 보이게 함
                paintView.reset();
                paintView.invalidate();
                counter=0;
                break;

            case R.id.buttonredraw:      // 캔버스 지우는 버튼
                paintView.reset();
                paintView.invalidate();
                counter=0;
                break;

            case R.id.buttonSubmit:     // 계산하는 버튼
                altLayout.setVisibility(View.INVISIBLE);    // 확률 레이아웃 안 보이게 함
                translate();        // 게산
                break;

            case R.id.alt1:
            case R.id.alt2:
            case R.id.alt3:
            case R.id.alt4:
                useAltLabel(Integer.parseInt(view.getTag().toString()));    // 마지막 문자를 대체 문자를 전환
                break;
        }
    }

    /**타이머*/
    private void ClassifyTimer(){
        tt =new TimerTask() {
            @Override
            public void run () {
                counter++;
            }
        };
        timer =new Timer();
        timer.schedule(tt,900);
    }

    /**타이머*/
    private void Timer(){
        tt =new TimerTask() {
            @Override
            public void run () {
                counter++;
            }
        };
        timer =new Timer();
        timer.schedule(tt,1000);
    }


    /**
     * Delete the last character in the text input field.
     * 가장 마지막 글자 지움
     */
    private void backspace() {
        int len = resultText.getText().length();    // 연산식 길이
        if (len > 0) {
            resultText.getText().delete(len - 1, len);  // 마지막 하나 지움
        }
    }


    /**
     * Clear the text and drawing to return to the beginning state.
     * 맨 처음 상태로 돌아감
     */
    private void clear() {
        paintView.reset();
        paintView.invalidate();
        resultText.setText("");         // 연산식 초기화
        translationText.setText("");    // 계산결과 초기화
        altLayout.setVisibility(View.INVISIBLE);       // 확률 레이아웃 안 보이게 함
    }

    /**
     * Perform the classification, updating UI elements with the results.
     * 분류를 수행하며 결과를 보고 UI 요소들을 업데이트
     */
    private void classify() {
        float pixels[] = paintView.getPixelData();          // PixelData 배열로 저장
        currentTopLabels = classifier.classify(pixels);     // 상위 5개 label 가져옴
        resultText.append(currentTopLabels[0]);             // 가장 value가 높은 것 연산식에 추가시킴
        altLayout.setVisibility(View.VISIBLE);              // 확률 레이아웃 띄움
        // 가장 높은 value 제외한 4개 표시
        alt1.setText(currentTopLabels[1]);
        alt2.setText(currentTopLabels[2]);
        alt3.setText(currentTopLabels[3]);
        alt4.setText(currentTopLabels[4]);
    }

    /**
     * 계산기 알고리즘 수행
     */
    private void translate() {
        String _string = resultText.getText().toString();      // 연산식을 text로 추출해 string으로 저장
        if (_string.isEmpty()) {        // 연산식에 아무 것도 없을 때
            return;
        }

        String infixExp = _string;      // 연산식
        Calc c = new Calc();            // 계산 알고리즘
        if (!Calc.bracketsBalance(infixExp)){
            return;
        }
        String postfixExp = c.postfix(infixExp);
        Double result = c.result(postfixExp);       // 계산 결과
        clear();
        translationText.append("the result = " + result +"\n");     // "result = (계산결과) "
    }

    /**
     * This function will switch out the last classified character with the alternative given the
     * index in the top labels array.
     * 연산식에 마직막으로 추가된 숫자(부호)를 대체 문자로 전환
     */
    private void useAltLabel(int index) {
        backspace();        // 마지막 한 글자 지움
        resultText.append(currentTopLabels[index]);     // 해당 문자를 연산식 마지막에 추가시킴
    }

    @Override
    protected void onResume() {
        paintView.onResume();
        super.onResume();
    }

    @Override
    protected void onPause() {
        paintView.onPause();
        super.onPause();
    }

    /**
     * Load pre-trained model in memory.
     * 미리 훈련된 모델을 메모리에 load
     */
    private void loadModel() {
        new Thread(new Runnable() {
            @Override
            public void run() {     // thread가 실행되면 수행되는 곳
                try {
                    classifier = HangulClassifier.create(getAssets(),
                            MODEL_FILE, LABEL_FILE, PaintView.FEED_DIMENSION,
                            "input", "keep_prob", "output");
                } catch (final Exception e) {
                    // 모델 load 실패시
                    throw new RuntimeException("Error loading pre-trained model.", e);
                }
            }
        }).start();
    }
}