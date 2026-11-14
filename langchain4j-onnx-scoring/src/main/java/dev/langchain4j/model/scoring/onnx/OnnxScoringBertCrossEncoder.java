package dev.langchain4j.model.scoring.onnx;

import static ai.onnxruntime.OnnxTensor.createTensor;

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

class OnnxScoringBertCrossEncoder implements AutoCloseable {

    private static final int TOKEN_LENGTH_TOLERANCE = 2;

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final Set<String> expectedInputs;
    private final HuggingFaceTokenizer tokenizer;
    private final boolean normalize;
    private boolean closed;
    private int tokenLengthTolerance;

    public OnnxScoringBertCrossEncoder(
            String modelPath,
            OrtSession.SessionOptions options,
            String pathToTokenizer,
            int modelMaxLength,
            boolean normalize,
            int tokenLengthTolerance) {
        OrtSession initializedSession = null;
        HuggingFaceTokenizer initializedTokenizer = null;
        try (options) { // properly release parent session at the end of this block to prevent leaks
            this.environment = OrtEnvironment.getEnvironment();
            initializedSession = this.environment.createSession(modelPath, options);
            this.expectedInputs = initializedSession.getInputNames();
            Map<String, String> tokenizerOptions = new HashMap<String, String>() {
                {
                    put("padding", "true");
                    put(
                            "truncation",
                            "LONGEST_FIRST"); // Default maximum length limit, LONGEST-FIRST prioritizes truncating the
                    // longest part
                    put("modelMaxLength", String.valueOf(modelMaxLength - 2));
                }
            };
            this.normalize = normalize;
            initializedTokenizer = HuggingFaceTokenizer.newInstance(Paths.get(pathToTokenizer), tokenizerOptions);
        } catch (Exception | Error e) {
            closeOnFailure(initializedSession, e);
            closeOnFailure(initializedTokenizer, e);
            if (e instanceof Error error) {
                // may originate from either native library (ONNX Runtime or DJL tokenizer), so it is not relabelled
                throw error;
            }
            throw new RuntimeException(e);
        }
        this.session = initializedSession;
        this.tokenizer = initializedTokenizer;
        this.tokenLengthTolerance = tokenLengthTolerance;
    }

    public OnnxScoringBertCrossEncoder(
            String modelPath,
            OrtSession.SessionOptions options,
            String pathToTokenizer,
            int modelMaxLength,
            boolean normalize) {
        this(modelPath, options, pathToTokenizer, modelMaxLength, normalize, TOKEN_LENGTH_TOLERANCE);
    }

    private static void closeOnFailure(AutoCloseable resource, Throwable failure) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception | Error closeFailure) {
                failure.addSuppressed(closeFailure);
            }
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

    @Override
    public void close() {
        if (closed) {
            return;
        }

        Exception firstException = null;

        try {
            if (session != null) session.close();
        } catch (Exception e) {
            firstException = e;
        }

        try {
            if (tokenizer != null) tokenizer.close();
        } catch (Exception e) {
            if (firstException != null) {
                firstException.addSuppressed(e);
            } else {
                firstException = e;
            }
        }

        closed = true;

        if (firstException != null) {
            throw new RuntimeException(firstException);
        }
    }

    ScoringAndTokenCount scoreAll(String query, List<String> documents) {
        if (documents == null || documents.isEmpty()) {
            return new ScoringAndTokenCount(Collections.emptyList(), 0);
        }
        int numDocs = documents.size();
        int[] tokenLengths = new int[numDocs];
        Integer[] indices = new Integer[numDocs];
        for (int i = 0; i < numDocs; i++) {
            tokenLengths[i] = this.tokenizer.tokenize(documents.get(i)).size();
            indices[i] = i;
        }
        Arrays.sort(indices, Comparator.comparingInt(idx -> tokenLengths[idx]));
        int totalTokenCount = 0;
        Double[] finalScoresArray = new Double[numDocs];
        int i = 0;
        while (i < numDocs) {
            int currentLength = tokenLengths[indices[i]];
            int end = i + 1;
            while (end < numDocs && tokenLengths[indices[end]] - currentLength <= tokenLengthTolerance) {
                end++;
            }
            PairList<String, String> pairs = new PairList<>();
            for (int j = i; j < end; j++) {
                pairs.add(query, documents.get(indices[j]));
            }
            Encoding[] encodings = this.tokenizer.batchEncode(pairs);
            totalTokenCount += this.countTokens(encodings);
            List<Double> batchScores;
            try (Result result = this.encode(encodings)) {
                batchScores = this.toScore(result);
            } catch (OrtException e) {
                throw new RuntimeException(e);
            }
            for (int j = 0; j < batchScores.size(); j++) {
                finalScoresArray[indices[i + j]] = batchScores.get(j);
            }
            i = end;
            if (i < numDocs) {
                currentLength = tokenLengths[indices[i]];
            }
        }
        return new ScoringAndTokenCount(Arrays.asList(finalScoresArray), totalTokenCount);
    }

    ScoringAndTokenCount scoreAllSingleBatch(String query, List<String> documents) {
        List<Double> scores;
        PairList<String, String> pairs = new PairList<>();
        for (String document : documents) {
            pairs.add(query, document);
        }
        Encoding[] encodings = this.tokenizer.batchEncode(pairs);
        int tokenCount = countTokens(encodings);
        try (Result result = this.encode(encodings)) {
            scores = this.toScore(result);
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }
        return new ScoringAndTokenCount(scores, tokenCount);
    }

    private int countTokens(Encoding[] encodings) {
        if (encodings == null || encodings.length == 0) {
            return 0;
        }
        int tokenCount = 0;
        for (Encoding encoding : encodings) {
            long[] specialTokenMask = encoding.getSpecialTokenMask();
            if (specialTokenMask != null && specialTokenMask.length > 0) {
                for (long mask : specialTokenMask) {
                    // mask == 0 denotes regular tokens; mask == 1 denotes special tokens ([CLS], [SEP])
                    if (mask == 0) {
                        tokenCount++;
                    }
                }
            } else {
                tokenCount += Math.max(0, encoding.getIds().length - 3);
            }
        }
        return tokenCount;
    }

    private Result encode(Encoding[] encodings) throws OrtException {
        long[][] inputIds = new long[encodings.length][];
        long[][] attentionMask = new long[encodings.length][];
        long[][] tokenTypeIds = new long[encodings.length][];

        for (int i = 0; i < encodings.length; i++) {
            inputIds[i] = encodings[i].getIds();
            attentionMask[i] = encodings[i].getAttentionMask();
            tokenTypeIds[i] = encodings[i].getTypeIds();
        }

        try (OnnxTensor inputIdsTensor = createTensor(environment, inputIds);
                OnnxTensor attentionMaskTensor = createTensor(environment, attentionMask);
                OnnxTensor tokenTypeIdsTensor = createTensor(this.environment, tokenTypeIds)) {
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
        float[] logits = extractLogits(result.get(0).getValue());
        List<Double> scores = new ArrayList<>();
        for (float logit : logits) {
            if (normalize) {
                scores.add(sigmoid(logit));
            } else {
                scores.add((double) logit);
            }
        }
        return scores;
    }

    /**
     * Extracts a single logit per scored item from the raw ONNX output.
     *
     * <p>Cross-encoder rerankers expose their logits with a leading batch dimension. Depending on how
     * the model was exported to ONNX (e.g. via Optimum, with or without an extra squeeze), the value
     * returned by {@link ai.onnxruntime.OnnxTensor#getValue()} can be a 2D array
     * ({@code float[][]}, shape {@code [batch, k]}) or a 3D array ({@code float[][][]}, shape
     * {@code [batch, 1, 1]}, as produced for example by {@code BAAI/bge-reranker-base}). Both shapes
     * are accepted: the first scalar of each item is used as its logit, preserving the historical
     * behaviour for 2D outputs while avoiding a {@link ClassCastException} for 3D outputs.
     *
     * @param value the raw value returned by {@link ai.onnxruntime.OnnxTensor#getValue()}
     * @return one logit per scored item, preserving the batch order
     */
    static float[] extractLogits(Object value) {
        if (value instanceof float[][] matrix) {
            float[] logits = new float[matrix.length];
            for (int i = 0; i < matrix.length; i++) {
                logits[i] = matrix[i][0];
            }
            return logits;
        }
        if (value instanceof float[][][] matrix) {
            float[] logits = new float[matrix.length];
            for (int i = 0; i < matrix.length; i++) {
                logits[i] = matrix[i][0][0];
            }
            return logits;
        }
        throw new IllegalStateException(
                "Unsupported ONNX scoring output shape: " + value.getClass().getName());
    }

    private double sigmoid(float x) {
        return 1 / (1 + Math.exp(-x));
    }
}
