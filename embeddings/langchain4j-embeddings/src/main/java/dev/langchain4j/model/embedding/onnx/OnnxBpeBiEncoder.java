package dev.langchain4j.model.embedding.onnx;

import static ai.onnxruntime.OnnxTensor.createTensor;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.nio.LongBuffer.wrap;
import static java.util.Collections.singletonMap;

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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * ONNX-based encoder for BPE-tokenized / decoder-family embedding models
 * (e.g., Qwen3-Embedding, E5-Mistral, gte-Qwen).
 *
 * <h2>Design differences from {@link OnnxBertBiEncoder}</h2>
 * <ul>
 *   <li>No dependency on {@code [CLS]} / {@code [SEP]} special tokens</li>
 *   <li>No WordPiece {@code ##} subword boundary logic</li>
 *   <li>Uses {@code attention_mask} for pooling (respects padding)</li>
 *   <li>Supports {@link PoolingMode#LAST_TOKEN} and {@link PoolingMode#MEAN_MASKED}</li>
 *   <li>Does not send {@code token_type_ids} to the model</li>
 *   <li>Long inputs are truncated, not partitioned (Qwen3 supports 32k context,
 *       partitioning makes less sense for decoder models)</li>
 * </ul>
 *
 * <p><b>Reference implementations:</b>
 * <ul>
 *   <li>Qwen3-Embedding official inference code
 *       (<a href="https://huggingface.co/Qwen/Qwen3-Embedding-0.6B">HuggingFace model card</a>)</li>
 *   <li>sentence-transformers {@code Pooling} module
 *       (<a href="https://github.com/UKPLab/sentence-transformers">UKPLab/sentence-transformers</a>)</li>
 * </ul>
 */
public class OnnxBpeBiEncoder {

    /** Default maximum tokens per input. Qwen3 supports up to 32k, but most use cases use fewer than 2048 tokens. */
    public static final int DEFAULT_MAX_LENGTH = 2048;

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final Set<String> expectedInputs;
    private final HuggingFaceTokenizer tokenizer;
    private final PoolingMode poolingMode;
    private final int maxLength;

    public OnnxBpeBiEncoder(Path pathToModel, Path pathToTokenizer, PoolingMode poolingMode) {
        this(pathToModel, pathToTokenizer, poolingMode, DEFAULT_MAX_LENGTH);
    }

    public OnnxBpeBiEncoder(Path pathToModel, Path pathToTokenizer, PoolingMode poolingMode, int maxLength) {
        try {
            this.environment = OrtEnvironment.getEnvironment();
            this.session = environment.createSession(pathToModel.toString());
            this.expectedInputs = session.getInputNames();
            this.tokenizer = HuggingFaceTokenizer.newInstance(pathToTokenizer, singletonMap("padding", "false"));
            this.poolingMode = validatePoolingMode(poolingMode);
            this.maxLength = validateMaxLength(maxLength);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public OnnxBpeBiEncoder(InputStream model, InputStream tokenizer, PoolingMode poolingMode, int maxLength) {
        try {
            this.environment = OrtEnvironment.getEnvironment();
            this.session = environment.createSession(loadModel(model));
            this.expectedInputs = session.getInputNames();
            this.tokenizer = HuggingFaceTokenizer.newInstance(tokenizer, singletonMap("padding", "false"));
            this.poolingMode = validatePoolingMode(poolingMode);
            this.maxLength = validateMaxLength(maxLength);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static PoolingMode validatePoolingMode(PoolingMode mode) {
        ensureNotNull(mode, "poolingMode");
        if (mode == PoolingMode.CLS) {
            throw illegalArgument("PoolingMode.CLS is not meaningful for BPE/decoder models. "
                    + "Use LAST_TOKEN or MEAN_MASKED instead.");
        }
        return mode;
    }

    private static int validateMaxLength(int maxLength) {
        if (maxLength < 1) {
            throw illegalArgument("maxLength must be >= 1, got " + maxLength);
        }
        return maxLength;
    }

    EmbeddingAndTokenCount embed(String text) {

        Encoding encoding = tokenizer.encode(text, true, false);

        long[] inputIds = encoding.getIds();
        long[] attentionMask = encoding.getAttentionMask();

        // Decoder-based embedding models work best with the whole sequence
        // contextualized at once. Truncate if it exceeds maxLength.
        if (inputIds.length > maxLength) {
            inputIds = Arrays.copyOf(inputIds, maxLength);
            attentionMask = Arrays.copyOf(attentionMask, maxLength);
        }

        if (inputIds.length == 0) {
            throw illegalArgument("Cannot embed empty or whitespace-only text");
        }

        float[][] hiddenStates;
        try (Result result = runInference(inputIds, attentionMask)) {
            hiddenStates = extractHiddenStates(result);
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }

        float[] pooled = pool(hiddenStates, attentionMask);
        float[] normalized = normalize(pooled);

        return new EmbeddingAndTokenCount(normalized, inputIds.length);
    }

    int countTokens(String text) {
        return tokenizer.tokenize(text).size();
    }

    private Result runInference(long[] inputIds, long[] attentionMask) throws OrtException {
        long[] shape = {1, inputIds.length};

        try (OnnxTensor inputIdsTensor = createTensor(environment, wrap(inputIds), shape);
                OnnxTensor attentionMaskTensor = createTensor(environment, wrap(attentionMask), shape)) {

            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", inputIdsTensor);
            inputs.put("attention_mask", attentionMaskTensor);
            // Decoder models do not use token_type_ids.

            return session.run(inputs);
        }
    }

    private static float[][] extractHiddenStates(Result result) throws OrtException {
        // Shape: [batch=1, seqLen, hiddenDim]; use batch 0.
        return ((float[][][]) result.get(0).getValue())[0];
    }

    private float[] pool(float[][] hiddenStates, long[] attentionMask) {
        switch (poolingMode) {
            case LAST_TOKEN:
                return lastTokenPool(hiddenStates, attentionMask);
            case MEAN_MASKED:
                return meanMaskedPool(hiddenStates, attentionMask);
            case MEAN:
                return meanPool(hiddenStates); // legacy, unmasked
            default:
                throw illegalArgument("PoolingMode " + poolingMode + " is not supported by OnnxBpeBiEncoder");
        }
    }

    /**
     * Last-token pooling per Qwen3-Embedding official implementation.
     *
     * <p>Handles both padding conventions:
     * <pre>
     *   Left-padded:   [PAD, PAD, ..., token_N]      -> return position N-1
     *   Right-padded:  [token_1, ..., token_N, PAD]  -> return position N-1 (find last mask=1)
     * </pre>
     *
     * <p>For single-input encoding (current langchain4j use case), there is no padding
     * and all {@code attention_mask} values are 1, so this reduces to {@code hiddenStates[last]}.
     * The full logic is kept for correctness with future batch-style callers.
     */
    static float[] lastTokenPool(float[][] hiddenStates, long[] attentionMask) {
        int seqLen = attentionMask.length;

        // Common fast path: last position is a real token (no right-padding)
        if (attentionMask[seqLen - 1] == 1L) {
            return hiddenStates[seqLen - 1];
        }

        // Right-padded: walk backward to the last non-padded position
        for (int i = seqLen - 1; i >= 0; i--) {
            if (attentionMask[i] == 1L) {
                return hiddenStates[i];
            }
        }

        throw new IllegalStateException("No non-padded tokens in attention_mask");
    }

    /**
     * Mean pooling respecting {@code attention_mask} - equivalent to
     * sentence-transformers' {@code pooling_mode_mean_tokens}.
     *
     * <p>Unlike legacy {@link #meanPool(float[][])}, this skips padding positions,
     * giving correct results even when inputs are padded.
     */
    static float[] meanMaskedPool(float[][] hiddenStates, long[] attentionMask) {
        int seqLen = hiddenStates.length;
        int dim = hiddenStates[0].length;

        float[] sum = new float[dim];
        int count = 0;

        for (int i = 0; i < seqLen; i++) {
            if (attentionMask[i] == 1L) {
                float[] vec = hiddenStates[i];
                for (int j = 0; j < dim; j++) {
                    sum[j] += vec[j];
                }
                count++;
            }
        }

        if (count == 0) {
            throw new IllegalStateException("No non-padded tokens to average");
        }

        for (int j = 0; j < dim; j++) {
            sum[j] /= count;
        }
        return sum;
    }

    /** Legacy mean pooling (doesn't consider attention_mask). Keeps BERT-encoder parity. */
    static float[] meanPool(float[][] hiddenStates) {
        int numVectors = hiddenStates.length;
        int dim = hiddenStates[0].length;

        float[] avg = new float[dim];
        for (float[] vec : hiddenStates) {
            for (int j = 0; j < dim; j++) {
                avg[j] += vec[j];
            }
        }
        for (int j = 0; j < dim; j++) {
            avg[j] /= numVectors;
        }
        return avg;
    }

    private static float[] normalize(float[] vector) {
        float sumSquare = 0;
        for (float v : vector) {
            sumSquare += v * v;
        }
        float norm = (float) Math.sqrt(sumSquare);

        float[] result = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            result[i] = vector[i] / norm;
        }
        return result;
    }

    private byte[] loadModel(InputStream modelInputStream) {
        if (modelInputStream == null) {
            throw new IllegalStateException("Embedding model file is not available. "
                    + "This usually happens when running LangChain4j tests from sources. "
                    + "If you are developing LangChain4j locally, run 'mvn generate-resources' "
                    + "from the project root to download the required model files.");
        }
        try (InputStream inputStream = modelInputStream;
                ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] data = new byte[1024];
            int nRead;
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
