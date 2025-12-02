package dev.langchain4j.model.embedding.onnx;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;

import static ai.onnxruntime.OnnxTensor.createTensor;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.nio.LongBuffer.wrap;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;

public class OnnxBertBiEncoder {

    private static final int MAX_SEQUENCE_LENGTH = 510; // 512 - 2 (special tokens [CLS] and [SEP])

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final Set<String> expectedInputs;
    private final HuggingFaceTokenizer tokenizer;
    private final PoolingMode poolingMode;

    public OnnxBertBiEncoder(Path pathToModel, Path pathToTokenizer, PoolingMode poolingMode) {
        try {
            this.environment = OrtEnvironment.getEnvironment();
            this.session = environment.createSession(pathToModel.toString());
            this.expectedInputs = session.getInputNames();
            this.tokenizer = HuggingFaceTokenizer.newInstance(pathToTokenizer, singletonMap("padding", "false"));
            this.poolingMode = ensureNotNull(poolingMode, "poolingMode");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public OnnxBertBiEncoder(InputStream model, InputStream tokenizer, PoolingMode poolingMode) {
        try {
            this.environment = OrtEnvironment.getEnvironment();
            this.session = environment.createSession(loadModel(model));
            this.expectedInputs = session.getInputNames();
            this.tokenizer = HuggingFaceTokenizer.newInstance(tokenizer, singletonMap("padding", "false"));
            this.poolingMode = ensureNotNull(poolingMode, "poolingMode");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public OnnxBertBiEncoder(OrtEnvironment environment, OrtSession session, InputStream tokenizer, PoolingMode poolingMode) {
        try {
            this.environment = environment;
            this.session = session;
            this.expectedInputs = session.getInputNames();
            this.tokenizer = HuggingFaceTokenizer.newInstance(tokenizer, singletonMap("padding", "false"));
            this.poolingMode = ensureNotNull(poolingMode, "poolingMode");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static class EmbeddingAndTokenCount {

        float[] embedding;
        int tokenCount;

        EmbeddingAndTokenCount(float[] embedding, int tokenCount) {
            this.embedding = embedding;
            this.tokenCount = tokenCount;
        }
    }

    EmbeddingAndTokenCount embed(String text) {

        List<String> tokens = tokenizer.tokenize(text);
        List<List<String>> partitions = partition(tokens, MAX_SEQUENCE_LENGTH);

        List<float[]> embeddings = new ArrayList<>();
        for (List<String> partition : partitions) {
            try (Result result = encode(partition)) {
                float[] embedding = toEmbedding(result);
                embeddings.add(embedding);
            } catch (OrtException e) {
                throw new RuntimeException(e);
            }
        }

        List<Integer> weights = partitions.stream()
                .map(List::size)
                .collect(toList());

        float[] embedding = normalize(weightedAverage(embeddings, weights));

        return new EmbeddingAndTokenCount(embedding, tokens.size());
    }

    static List<List<String>> partition(List<String> tokens, int partitionSize) {
        List<List<String>> partitions = new ArrayList<>();
        int from = 1; // Skip the first (CLS) token

        while (from < tokens.size() - 1) { // Skip the last (SEP) token
            int to = from + partitionSize;

            if (to >= tokens.size() - 1) {
                to = tokens.size() - 1;
            } else {
                // ensure we don't split word across partitions
                while (tokens.get(to).startsWith("##")) {
                    to--;
                }
            }

            partitions.add(tokens.subList(from, to));

            from = to;
        }

        return partitions;
    }

    private Result encode(List<String> tokens) throws OrtException {

        Encoding encoding = tokenizer.encode(toText(tokens), true, false);

        long[] inputIds = encoding.getIds();
        long[] attentionMask = encoding.getAttentionMask();
        long[] tokenTypeIds = encoding.getTypeIds();

        long[] shape = {1, inputIds.length};

        try (
                OnnxTensor inputIdsTensor = createTensor(environment, wrap(inputIds), shape);
                OnnxTensor attentionMaskTensor = createTensor(environment, wrap(attentionMask), shape);
                OnnxTensor tokenTypeIdsTensor = createTensor(environment, wrap(tokenTypeIds), shape)
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

    private String toText(List<String> tokens) {

        String text = tokenizer.buildSentence(tokens);

        List<String> tokenized = tokenizer.tokenize(text);
        List<String> tokenizedWithoutSpecialTokens = new LinkedList<>(tokenized);
        tokenizedWithoutSpecialTokens.remove(0);
        tokenizedWithoutSpecialTokens.remove(tokenizedWithoutSpecialTokens.size() - 1);

        if (tokenizedWithoutSpecialTokens.equals(tokens)) {
            return text;
        } else {
            return String.join("", tokens);
        }
    }

    private float[] toEmbedding(Result result) throws OrtException {
        float[][] vectors = ((float[][][]) result.get(0).getValue())[0];
        return pool(vectors);
    }

    private float[] pool(float[][] vectors) {
        switch (poolingMode) {
            case CLS:
                return clsPool(vectors);
            case MEAN:
                return meanPool(vectors);
            default:
                throw illegalArgument("Unknown pooling mode: " + poolingMode);
        }
    }

    private static float[] clsPool(float[][] vectors) {
        return vectors[0];
    }

    private static float[] meanPool(float[][] vectors) {

        int numVectors = vectors.length;
        int vectorLength = vectors[0].length;

        float[] averagedVector = new float[vectorLength];

        for (float[] vector : vectors) {
            for (int j = 0; j < vectorLength; j++) {
                averagedVector[j] += vector[j];
            }
        }

        for (int j = 0; j < vectorLength; j++) {
            averagedVector[j] /= numVectors;
        }

        return averagedVector;
    }

    private float[] weightedAverage(List<float[]> embeddings, List<Integer> weights) {
        if (embeddings.size() == 1) {
            return embeddings.get(0);
        }

        int dimensions = embeddings.get(0).length;

        float[] averagedEmbedding = new float[dimensions];
        int totalWeight = 0;

        for (int i = 0; i < embeddings.size(); i++) {
            int weight = weights.get(i);
            totalWeight += weight;

            for (int j = 0; j < dimensions; j++) {
                averagedEmbedding[j] += embeddings.get(i)[j] * weight;
            }
        }

        for (int j = 0; j < dimensions; j++) {
            averagedEmbedding[j] /= totalWeight;
        }

        return averagedEmbedding;
    }

    private static float[] normalize(float[] vector) {

        float sumSquare = 0;
        for (float v : vector) {
            sumSquare += v * v;
        }
        float norm = (float) Math.sqrt(sumSquare);

        float[] normalizedVector = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalizedVector[i] = vector[i] / norm;
        }

        return normalizedVector;
    }

    int countTokens(String text) {
        return tokenizer.tokenize(text).size();
    }

    private byte[] loadModel(InputStream modelInputStream) {
        try (
                InputStream inputStream = modelInputStream;
                ByteArrayOutputStream buffer = new ByteArrayOutputStream()
        ) {
            int nRead;
            byte[] data = new byte[1024];

            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }

            buffer.flush();
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
