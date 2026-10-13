package dev.langchain4j.model.embedding.onnx;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Manual end-to-end test for OnnxBpeBiEncoder with the real Qwen3-Embedding model.
 *
 * <p>To run locally:
 * <pre>
 *   # Windows PowerShell:
 *   $env:QWEN3_MODEL_PATH     = "D:\qwen3-models\model.onnx"
 *   $env:QWEN3_TOKENIZER_PATH = "D:\qwen3-models\tokenizer.json"
 *
 *   # Linux / Mac:
 *   export QWEN3_MODEL_PATH=/path/to/qwen3/model.onnx
 *   export QWEN3_TOKENIZER_PATH=/path/to/qwen3/tokenizer.json
 *
 *   ./mvnw -pl embeddings/langchain4j-embeddings test -Dtest=OnnxBpeBiEncoderManualIT
 * </pre>
 *
 * <p>Expected similarities (from Python reference run with the same 3 texts):
 * <pre>
 *   [0] vs [1]  ~0.77   (semantically related - same topic)
 *   [0] vs [2]  ~0.14   (semantically unrelated)
 *   [1] vs [2]  ~0.14   (semantically unrelated)
 * </pre>
 *
 * <p>This test is intentionally NOT part of the regular test suite - it requires a
 * ~600MB-1.2GB model file that we don't want in CI. When the env vars are not set,
 * JUnit silently skips the class.
 */
@EnabledIfEnvironmentVariable(named = "QWEN3_MODEL_PATH", matches = ".+")
@EnabledIfEnvironmentVariable(named = "QWEN3_TOKENIZER_PATH", matches = ".+")
class OnnxBpeBiEncoderManualIT {

    private static final List<String> TEXTS =
            List.of("What is the capital of China?", "The capital of China is Beijing.", "Explain gravity");

    @Test
    void embed_three_texts_and_print_similarities() throws Exception {

        Path modelPath = Paths.get(System.getenv("QWEN3_MODEL_PATH"));
        Path tokenizerPath = Paths.get(System.getenv("QWEN3_TOKENIZER_PATH"));

        // Fail fast with a clear message if files are missing
        if (!Files.exists(modelPath)) {
            throw new IllegalStateException("Model file not found: " + modelPath);
        }
        if (!Files.exists(tokenizerPath)) {
            throw new IllegalStateException("Tokenizer file not found: " + tokenizerPath);
        }

        System.out.println("Loading OnnxBpeBiEncoder...");
        System.out.println("  Model:     " + modelPath);
        System.out.println("  Tokenizer: " + tokenizerPath);
        long t0 = System.currentTimeMillis();

        OnnxBpeBiEncoder encoder = new OnnxBpeBiEncoder(modelPath, tokenizerPath, PoolingMode.LAST_TOKEN);
        System.out.printf("Model loaded in %d ms%n", System.currentTimeMillis() - t0);

        // Embed each text individually.
        float[][] vectors = new float[TEXTS.size()][];
        int[] tokenCounts = new int[TEXTS.size()];

        for (int i = 0; i < TEXTS.size(); i++) {
            String text = TEXTS.get(i);
            long tStart = System.currentTimeMillis();
            EmbeddingAndTokenCount result = encoder.embed(text);
            long tEnd = System.currentTimeMillis();

            vectors[i] = result.embedding;
            tokenCounts[i] = result.tokenCount;

            System.out.println();
            System.out.printf("[%d] %s%n", i, text);
            System.out.printf("    tokenCount: %d%n", result.tokenCount);
            System.out.printf("    dimension : %d%n", result.embedding.length);
            System.out.printf("    L2 norm   : %.6f%n", l2Norm(result.embedding));
            System.out.printf("    latency   : %d ms%n", tEnd - tStart);
            System.out.printf(
                    "    first 5   : [%.4f, %.4f, %.4f, %.4f, %.4f]%n",
                    result.embedding[0],
                    result.embedding[1],
                    result.embedding[2],
                    result.embedding[3],
                    result.embedding[4]);
        }

        // Pairwise cosine similarities.
        System.out.println();
        System.out.println("----------------------------------------------------------");
        System.out.println("  COSINE SIMILARITIES (Java)");
        System.out.println("----------------------------------------------------------");
        System.out.printf("  [0] vs [1]  = %.4f   (expected ~0.77)%n", cosine(vectors[0], vectors[1]));
        System.out.printf("  [0] vs [2]  = %.4f   (expected ~0.14)%n", cosine(vectors[0], vectors[2]));
        System.out.printf("  [1] vs [2]  = %.4f   (expected ~0.14)%n", cosine(vectors[1], vectors[2]));
        System.out.println();
    }

    // Helpers.

    private static double cosine(float[] a, float[] b) {
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot; // already L2-normalized by the encoder, so dot == cosine
    }

    private static double l2Norm(float[] v) {
        double sum = 0.0;
        for (float x : v) {
            sum += x * x;
        }
        return Math.sqrt(sum);
    }
}
