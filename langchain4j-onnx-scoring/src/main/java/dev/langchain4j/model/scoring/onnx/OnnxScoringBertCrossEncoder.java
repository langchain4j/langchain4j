package dev.langchain4j.model.scoring.onnx;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.util.PairList;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;

import java.nio.file.Paths;
import java.util.*;

import static ai.onnxruntime.OnnxTensor.createTensor;

class OnnxScoringBertCrossEncoder {

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final Set<String> expectedInputs;
    private final HuggingFaceTokenizer tokenizer;
    private final boolean normalize;

    public OnnxScoringBertCrossEncoder(String modelPath, OrtSession.SessionOptions options, String pathToTokenizer, int modelMaxLength, boolean normalize) {
        try {
            this.environment = OrtEnvironment.getEnvironment();
            this.session = this.environment.createSession(modelPath, options);
            this.expectedInputs = session.getInputNames();
            Map<String, String> tokenizerOptions = new HashMap<String, String>() {{
                put("padding", "true");
                put("truncation", "LONGEST_FIRST"); // Default maximum length limit, LONGEST-FIRST prioritizes truncating the longest part
                put("modelMaxLength", String.valueOf(modelMaxLength - 2));
            }};
            this.normalize = normalize;
            this.tokenizer = HuggingFaceTokenizer.newInstance(Paths.get(pathToTokenizer), tokenizerOptions);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static class ScoringAndTokenCount {

        List<Double> scores;
        int tokenCount;

        ScoringAndTokenCount(List<Double> scores, int tokenCount) {
            this.scores = scores;
            this.tokenCount = tokenCount;
        }
    }

    ScoringAndTokenCount scoreAll(String query, List<String> documents) {
        List<Double> scores;
        int tokenCount = 0;
        int queryTokenCount = tokenizer.tokenize(query).size() - 2;
        PairList<String, String> pairs = new PairList<>();
        for (String document : documents) {
            pairs.add(query, document);
            tokenCount += queryTokenCount + tokenizer.tokenize(document).size() - 2; // do not count special tokens [CLS] and [SEP]
        }
        try (Result result = this.encode(pairs)) {
            scores = this.toScore(result);
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }
        return new ScoringAndTokenCount(scores, tokenCount);
    }

    private Result encode(PairList<String, String> pairs) throws OrtException {
        Encoding[] encodings = this.tokenizer.batchEncode(pairs);
        long[][] inputIds = new long[encodings.length][];
        long[][] attentionMask = new long[encodings.length][];
        long[][] tokenTypeIds = new long[encodings.length][];

        for (int i = 0; i < encodings.length; i++) {
            inputIds[i] = encodings[i].getIds();
            attentionMask[i] = encodings[i].getAttentionMask();
            tokenTypeIds[i] = encodings[i].getTypeIds();
        }

        try (
                OnnxTensor inputIdsTensor = createTensor(environment, inputIds);
                OnnxTensor attentionMaskTensor = createTensor(environment, attentionMask);
                OnnxTensor tokenTypeIdsTensor = createTensor(this.environment, tokenTypeIds);
        ) {
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", inputIdsTensor);
            inputs.put("attention_mask", attentionMaskTensor);

            if (expectedInputs.contains("token_type_ids")) {
                inputs.put("token_type_ids", tokenTypeIdsTensor);
            }

            return session.run(inputs);
        }
    }

    private List<Double> toScore(OrtSession.Result result) throws OrtException {
        float[][] output = (float[][]) result.get(0).getValue();
        List<Double> scores = new ArrayList<>();
        for (float[] floats : output) {
            if (normalize) {
                scores.add(sigmoid(floats[0]));
            } else {
                scores.add((double)floats[0]);
            }
        }
        return scores;
    }

    private double sigmoid(float x) {
        return 1 / (1 + Math.exp(-x));
    }

}
