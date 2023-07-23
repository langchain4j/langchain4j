package dev.langchain4j.model.embedding;

import ai.djl.modality.nlp.DefaultVocabulary;
import ai.djl.modality.nlp.bert.BertFullTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ai.onnxruntime.OnnxTensor.createTensor;
import static java.nio.LongBuffer.wrap;

public class OnnxEmbeddingModel {

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final DefaultVocabulary vocabulary;
    private final BertFullTokenizer tokenizer;

    public OnnxEmbeddingModel(String modelFilePath, String vocabularyFilePath) {
        try {
            this.environment = OrtEnvironment.getEnvironment();
            this.session = environment.createSession(loadModel(modelFilePath));
            this.vocabulary = DefaultVocabulary.builder()
                    .addFromTextFile(getClass().getResource(vocabularyFilePath))
                    .build();
            this.tokenizer = new BertFullTokenizer(vocabulary, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public float[] embed(String text) {
        try (Result result = runModel(text)) {
            return toEmbedding(result);
        } catch (Exception e) {
            throw new RuntimeException(e);

        }
    }

    private Result runModel(String text) throws OrtException {

        List<String> stringTokens = new ArrayList<>();
        stringTokens.add("[CLS]");
        stringTokens.addAll(tokenizer.tokenize(text));
        stringTokens.add("[SEP]");

        // TODO reusable buffers
        long[] tokens = stringTokens.stream()
                .mapToLong(vocabulary::getIndex)
                .toArray();

        long[] attentionMasks = new long[stringTokens.size()];
        for (int i = 0; i < stringTokens.size(); i++) {
            attentionMasks[i] = 1L;
        }

        long[] tokenTypeIds = new long[stringTokens.size()];
        for (int i = 0; i < stringTokens.size(); i++) {
            tokenTypeIds[i] = 0L;
        }

        long[] shape = {1, tokens.length};

        try (
                OnnxTensor tokensTensor = createTensor(environment, wrap(tokens), shape);
                OnnxTensor attentionMasksTensor = createTensor(environment, wrap(attentionMasks), shape);
                OnnxTensor tokenTypeIdsTensor = createTensor(environment, wrap(tokenTypeIds), shape)
        ) {
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", tokensTensor);
            inputs.put("token_type_ids", tokenTypeIdsTensor);
            inputs.put("attention_mask", attentionMasksTensor);

            return session.run(inputs);
        }
    }

    private byte[] loadModel(String modelFilePath) {
        try (
                InputStream inputStream = getClass().getResourceAsStream(modelFilePath);
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

    private static float[] toEmbedding(Result result) throws OrtException {
        float[][] vectors = ((float[][][]) result.get(0).getValue())[0];
        return normalize(meanPool(vectors));
    }

    private static float[] meanPool(float[][] vectors) {

        int numVectors = vectors.length;
        int vectorLength = vectors[0].length;

        float[] averagedVector = new float[vectorLength];

        // TODO ignore [CLS] and [SEP] ?
        for (int i = 0; i < numVectors; i++) {
            for (int j = 0; j < vectorLength; j++) {
                averagedVector[j] += vectors[i][j];
            }
        }

        // TODO ignore [CLS] and [SEP] ?
        for (int j = 0; j < vectorLength; j++) {
            averagedVector[j] /= numVectors;
        }

        return averagedVector;
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
}
