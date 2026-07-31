package dev.langchain4j.model.embedding.onnx;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession.Result;
import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.embedding.onnx.internal.ImagePreprocessor;
import dev.langchain4j.model.embedding.onnx.internal.OnnxModelLoader;
import dev.langchain4j.model.embedding.onnx.internal.VectorUtils;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * ONNX-based image encoder that runs a vision model (e.g., ViT, CLIP)
 * to produce embedding vectors from images.
 */
@Experimental
public class OnnxImageEncoder implements AutoCloseable {
    private final OnnxModelLoader modelLoader;
    private final ImagePreprocessor preprocessor;
    private final PoolingMode poolingMode;

    public OnnxImageEncoder(Path pathToModel, ImagePreprocessorConfig config, PoolingMode poolingMode) {
        this.modelLoader = new OnnxModelLoader(pathToModel);
        this.preprocessor = new ImagePreprocessor(ensureNotNull(config, "config"));
        this.poolingMode = ensureNotNull(poolingMode, "poolingMode");
    }

    public OnnxImageEncoder(Path pathToModel, ImagePreprocessorConfig config) {
        this(pathToModel, config, PoolingMode.CLS);
    }

    /**
     * Embed a LangChain4j {@link Image} (URL or base64).
     */
    public Embedding embed(Image image) {
        float[][][][] pixelValuesTensor = preprocessor.process(image);
        return Embedding.from(runAndExtract(pixelValuesTensor));
    }

    /**
     * Embed an image from raw ARGB pixels.
     */
    public Embedding embed(int[] pixels, int width, int height) {
        float[][][][] pixelValuesTensor = preprocessor.process(pixels, width, height);
        return Embedding.from(runAndExtract(pixelValuesTensor));
    }

    @Override
    public void close() throws Exception {
        modelLoader.close();
    }

    private float[] runAndExtract(float[][][][] pixelValuesTensor) {
        try (Result result = runInference(pixelValuesTensor)) {
            float[] embedding = extractEmbedding(result);
            return VectorUtils.normalize(embedding);
        } catch (OrtException e) {
            throw new IllegalStateException("ONNX inference failed", e);
        }
    }

    private Result runInference(float[][][][] pixelValuesTensor) throws OrtException {
        OrtEnvironment environment = modelLoader.environment();
        var session = modelLoader.session();
        try (OnnxTensor inputTensor = OnnxTensor.createTensor(environment, pixelValuesTensor)) {
            Map<String, OnnxTensor> modelInputs = new HashMap<>();
            modelInputs.put("pixel_values", inputTensor);
            return session.run(modelInputs);
        }
    }

    private float[] extractEmbedding(Result inferenceResult) throws OrtException {
        Object outputValue = inferenceResult.get(0).getValue();

        if (outputValue instanceof float[][] pooledOutput) {
            // Shape [1, dim] — already pooled, return directly
            return pooledOutput[0];
        } else if (outputValue instanceof float[][][] sequenceOutput) {
            // Shape [1, seq_len, dim] — apply pooling strategy
            return pool(sequenceOutput[0]);
        } else {
            throw new IllegalStateException("Unexpected ONNX output shape. Expected [1, dim] or [1, seq_len, dim], "
                    + "got: " + outputValue.getClass().getSimpleName());
        }
    }

    private float[] pool(float[][] tokenEmbeddings) {
        return switch (poolingMode) {
            case CLS -> tokenEmbeddings[0];
            case MEAN -> meanPool(tokenEmbeddings);
        };
    }

    private static float[] meanPool(float[][] tokenEmbeddings) {
        int sequenceLength = tokenEmbeddings.length;
        int embeddingDimension = tokenEmbeddings[0].length;
        float[] mean = new float[embeddingDimension];

        for (float[] token : tokenEmbeddings) {
            for (int i = 0; i < embeddingDimension; i++) {
                mean[i] += token[i];
            }
        }
        for (int i = 0; i < embeddingDimension; i++) {
            mean[i] /= sequenceLength;
        }
        return mean;
    }
}
