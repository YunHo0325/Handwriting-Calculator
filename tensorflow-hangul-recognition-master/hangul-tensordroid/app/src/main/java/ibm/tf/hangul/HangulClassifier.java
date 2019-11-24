package ibm.tf.hangul;

import android.content.res.AssetManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;


public class HangulClassifier {

    private TensorFlowInferenceInterface tfInterface;

    private String inputName;
    private String keepProbName;
    private String outputName;
    private int imageDimension;

    private List<String> labels;
    private float[] output;
    private String[] outputNames;

    /**
     * This function will create the TensorFlow inference interface and will populate all
     * the necessary data needed for inference.
     * TensorFlow 추론 인터페이스를 생성하고 인터페이스를 위해 필요한 필수적인 데이터를 덧붙임
     */
    public static HangulClassifier create(AssetManager assetManager,
                                          String modelPath, String labelFile, int inputDimension,
                                          String inputName, String keepProbName,
                                          String outputName) throws IOException {

        HangulClassifier classifier = new HangulClassifier();

        // These refer to the names of the nodes we care about in the model graph.
        // 모델 그래프에서 신경 쓰는 노드들에 대한 이름을 나타냄
        classifier.inputName = inputName;
        classifier.keepProbName = keepProbName;
        classifier.outputName = outputName;

        // Read the labels from the given label file.
        // 주어진 label 파일에서부터 label들을 읽음
        classifier.labels = readLabels(assetManager, labelFile);        // label 리스트

        // Create the TensorFlow interface using the specified model.
        // 명시된 모델을 사용해서 TensorFlow 인터페스를 생성
        classifier.tfInterface = new TensorFlowInferenceInterface(assetManager, modelPath);
        int numClasses = classifier.labels.size();      // label 개수

        // The size (in pixels) of each dimension of the image. Each dimension should be the same
        // since this is a square image.
        // image 가로세로 사이즈(가로=세로)
        classifier.imageDimension = inputDimension;

        // This is a list of output nodes which should be filled by the inference pass.
        // 추론 pass로 채워져야하는 출력 노드 리스트
        classifier.outputNames = new String[] { outputName };

        // This is the output node we care about.
        // 신경써야할 출력 노드
        classifier.outputName = outputName;

        // The float buffer where the output of the softmax/output node will be stored.
        // softmax/출력 노드의 출력이 저장될 플로트 버퍼
        classifier.output = new float[numClasses];

        return classifier;
    }

    /**
     * Use the TensorFlow model and the given pixel data to produce possible classifications.
     * 가능한 분류들을 생산하기 위해 TensorFlow와 주어진 Pixel data를 사용
     */
    public String[] classify(final float[] pixels) {

        // Feed the image data and the keep probability (for the dropout placeholder) to the model.
        // image 데이터와 확률들을 모델에 공급
        tfInterface.feed(inputName, pixels, 1, imageDimension, imageDimension, 1);
        tfInterface.feed(keepProbName, new float[] { 1 });

        // Run inference between the input and output.
        // input과 output 사이에서 추론 실행
        tfInterface.run(outputNames);

        // Fetch the contents of the Tensor denoted by 'outputName', copying the contents
        // into 'output'.
        // 'outputName'으로 표시된 Tensor의 내용을 가져와 내용을 'output'으로 복사
        tfInterface.fetch(outputName, output);

        // Map each Float to it's index. The higher values are the only ones we care about
        // and these are unlikely to have duplicates.
        // 각각의 Float를 index에 맞춰봄. 높은 values를 가진 것이 신경써야 할 하나가 됨
        // TreeMap : 정렬되면서 저장됨(오름차순)
        TreeMap<Float,Integer> map = new TreeMap<>();
        for (int i = 0; i < output.length; i++) {
            map.put( output[i], i );
        }

        // Let's only return the top five labels in order of confidence.
        // 상위 다섯 개의 label return
        Arrays.sort(output);
        String[] topLabels = new String[5];     // 상위 5개 저장할 배열
        //map에서 마지막 것부터 순서대로 5개 가져옴
        for (int i = output.length; i > output.length-5; i--) {
            topLabels[output.length - i] = labels.get(map.get(output[i-1]));
        }
        return topLabels;
    }

    /**
     * Read in all our labels into memory so that we can map Korean characters to confidences
     * listed in the output vectors.
     */
    private static List<String> readLabels(AssetManager am, String fileName) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(am.open(fileName)));

        String line;
        List<String> labels = new ArrayList<>();
        while ((line = reader.readLine()) != null) {
            labels.add(line);
        }
        reader.close();
        return labels;
    }
}