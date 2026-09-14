package dev.langchain4j.model.embedding.onnx;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executor;

/**
 * An embedding model that runs within your Java application's process
 * using <a href="https://onnxruntime.ai/">ONNX runtime</a>.
 * <br>
 * Many models (e.g., from <a href="https://huggingface.co/">HuggingFace</a>) can be used,
 * as long as they are in the ONNX format.
 * <br>
 * Information on how to convert models into ONNX format can be found
 * <a href="https://huggingface.co/docs/optimum/exporters/onnx/usage_guides/export_a_model">here</a>.
 * <br>
 * Many models already converted to ONNX format are available <a href="https://huggingface.co/Xenova">here</a>.
 */
public class OnnxEmbeddingModel extends AbstractInProcessEmbeddingModel {

    private final OnnxBertBiEncoder onnxBertBiEncoder;
    private final Encoder encoder;

    /**
     * @param pathToModel     The path to the modelPath file (e.g., "/path/to/model.onnx")
     * @param pathToTokenizer The path to the tokenizer file (e.g., "/path/to/tokenizer.json")
     * @param poolingMode     The pooling model to use. Can be found in the ".../1_Pooling/config.json" file on HuggingFace.
     *                        Here is an <a href="https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/blob/main/1_Pooling/config.json">example</a>.
     *                        {@code "pooling_mode_mean_tokens": true} means that {@link PoolingMode#MEAN} should be used.
     */
    public OnnxEmbeddingModel(Path pathToModel, Path pathToTokenizer, PoolingMode poolingMode) {
        super(null);
        this.onnxBertBiEncoder = new OnnxBertBiEncoder(pathToModel, pathToTokenizer, poolingMode);
        this.encoder = asEncoder(onnxBertBiEncoder);
    }

    /**
     * Creates an ONNX embedding model with the selected encoder implementation.
     *
     * @param pathToModel     The path to the model file.
     * @param pathToTokenizer The path to the tokenizer file.
     * @param poolingMode     The pooling mode to use.
     * @param encoderType     The encoder implementation to use.
     */
    public OnnxEmbeddingModel(
            Path pathToModel, Path pathToTokenizer, PoolingMode poolingMode, EncoderType encoderType) {
        super(null);
        OnnxBertBiEncoder onnxBertBiEncoder = null;
        Encoder encoder = null;
        switch (ensureNotNull(encoderType, "encoderType")) {
            case BERT -> {
                onnxBertBiEncoder = new OnnxBertBiEncoder(pathToModel, pathToTokenizer, poolingMode);
                encoder = asEncoder(onnxBertBiEncoder);
            }
            case BPE -> {
                onnxBertBiEncoder = null;
                encoder = asEncoder(new OnnxBpeBiEncoder(pathToModel, pathToTokenizer, poolingMode));
            }
        }
        this.onnxBertBiEncoder = onnxBertBiEncoder;
        this.encoder = encoder;
    }

    /**
     * @param pathToModel     The path to the modelPath file (e.g., "/path/to/model.onnx")
     * @param pathToTokenizer The path to the tokenizer file (e.g., "/path/to/tokenizer.json")
     * @param poolingMode     The pooling model to use. Can be found in the ".../1_Pooling/config.json" file on HuggingFace.
     *                        Here is an <a href="https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/blob/main/1_Pooling/config.json">example</a>.
     *                        {@code "pooling_mode_mean_tokens": true} means that {@link PoolingMode#MEAN} should be used.
     * @param executor        The executor to use to parallelize the embedding process.
     */
    public OnnxEmbeddingModel(Path pathToModel, Path pathToTokenizer, PoolingMode poolingMode, Executor executor) {
        super(ensureNotNull(executor, "executor"));
        this.onnxBertBiEncoder = loadFromFileSystem(pathToModel, pathToTokenizer, poolingMode);
        this.encoder = asEncoder(onnxBertBiEncoder);
    }

    /**
     * @param pathToModel     The path to the model file (e.g., "/home/me/model.onnx")
     * @param pathToTokenizer The path to the tokenizer file (e.g., "/path/to/tokenizer.json")
     * @param poolingMode     The pooling model to use. Can be found in the ".../1_Pooling/config.json" file on HuggingFace.
     *                        Here is an <a href="https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/blob/main/1_Pooling/config.json">example</a>.
     *                        {@code "pooling_mode_mean_tokens": true} means that {@link PoolingMode#MEAN} should be used.
     */
    public OnnxEmbeddingModel(String pathToModel, String pathToTokenizer, PoolingMode poolingMode) {
        this(Paths.get(pathToModel), Paths.get(pathToTokenizer), poolingMode);
    }

    /**
     * @param pathToModel     The path to the model file (e.g., "/home/me/model.onnx")
     * @param pathToTokenizer The path to the tokenizer file (e.g., "/path/to/tokenizer.json")
     * @param poolingMode     The pooling model to use. Can be found in the ".../1_Pooling/config.json" file on HuggingFace.
     *                        Here is an <a href="https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/blob/main/1_Pooling/config.json">example</a>.
     *                        {@code "pooling_mode_mean_tokens": true} means that {@link PoolingMode#MEAN} should be used.
     * @param executor        The executor to use to parallelize the embedding process.
     */
    public OnnxEmbeddingModel(String pathToModel, String pathToTokenizer, PoolingMode poolingMode, Executor executor) {
        this(Paths.get(pathToModel), Paths.get(pathToTokenizer), poolingMode, executor);
    }

    @Override
    protected OnnxBertBiEncoder model() {
        if (onnxBertBiEncoder != null) {
            return onnxBertBiEncoder;
        }
        throw new UnsupportedOperationException(
                "model() only returns OnnxBertBiEncoder. Use encoder() for generic access.");
    }

    @Override
    protected Encoder encoder() {
        return encoder;
    }

    /**
     * Encoder implementation used by the ONNX embedding model.
     */
    public enum EncoderType {
        /**
         * BERT-style encoder using WordPiece tokenization and token type IDs.
         */
        BERT,

        /**
         * BPE or decoder-style encoder that does not use token type IDs.
         */
        BPE
    }
}
