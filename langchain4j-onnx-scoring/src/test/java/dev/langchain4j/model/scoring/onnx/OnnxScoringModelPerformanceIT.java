package dev.langchain4j.model.scoring.onnx;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.onnxruntime.OrtSession;
import dev.langchain4j.model.scoring.onnx.OnnxScoringBertCrossEncoder.ScoringAndTokenCount;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OnnxScoringModelPerformanceIT {

    private static final String MODELS = "models";

    private static final String LANGCHAIN4J = "langchain4j";

    private static OnnxScoringBertCrossEncoder crossEncoder;

    private static Path modelPath;
    private static Path tokenizerPath;

    @BeforeAll
    static void initModel() throws IOException {
        initModel(
                "msmarco-MiniLM-L6-en-de-v1",
                "https://huggingface.co/cross-encoder/msmarco-MiniLM-L6-en-de-v1/resolve/main/onnx/model_O2.onnx?download=true",
                "https://huggingface.co/cross-encoder/msmarco-MiniLM-L6-en-de-v1/resolve/main/tokenizer.json?download=true",
                "model_O2.onnx",
                "tokenizer.json");
        //    	initModel("bge-reranker-base",
        //    			"https://huggingface.co/BAAI/bge-reranker-base/resolve/main/onnx/model.onnx?download=true",
        //    			"https://huggingface.co/BAAI/bge-reranker-base/resolve/main/tokenizer.json?download=true",
        //    			"model.onnx", "tokenizer.json");

        //    	initModel("ms-marco-MiniLM-L-6-v2",
        //
        //	"https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2/resolve/main/onnx/model_quantized.onnx?download=true",
        //    			"https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2/resolve/main/tokenizer.json?download=true",
        //    			"model_quantized.onnx", "tokenizer.json");
    }

    static void initModel(
            String directory, String modelUrl, String tokenizerUrl, String modelName, String tokenizerName)
            throws IOException {
        Path modelRoot = getCacheDirectory().resolve(directory);
        Files.createDirectories(modelRoot);
        modelPath = getOrDownload(modelRoot, modelName, modelUrl);
        tokenizerPath = getOrDownload(modelRoot, tokenizerName, tokenizerUrl);
        crossEncoder = new OnnxScoringBertCrossEncoder(
                modelPath.toString(), new OrtSession.SessionOptions(), tokenizerPath.toString(), 512, false);
    }

    @Test
    void testPerformance() {
        test(100, 50, 200, "Which river flows through Belgrade?");
        test(30, 50, 50, "Which river flows through Belgrade?");
        test(100, 50, 50, "Which river flows through Belgrade?");
        test(200, 80, 300, "Which river flows through Belgrade?");
    }

    private void test(int count, int minWords, int maxWords, String query) {
        List<String> documents = generateRandomDocuments(count, minWords, maxWords);
        long start = System.currentTimeMillis();
        ScoringAndTokenCount multiBatchResult = crossEncoder.scoreAll(query, documents);
        System.out.println("multibatch result took " + (System.currentTimeMillis() - start));
        start = System.currentTimeMillis();
        ScoringAndTokenCount singleBatchResult = crossEncoder.scoreAllSingleBatch(query, documents);
        System.out.println("single batch legacy result took " + (System.currentTimeMillis() - start));
        assertEquals(singleBatchResult.scores.size(), multiBatchResult.scores.size());
        assertEquals(singleBatchResult.tokenCount, multiBatchResult.tokenCount);
        for (int i = 0; i < multiBatchResult.scores.size(); i++) {
            Double multiBatchScore = multiBatchResult.scores.get(i);
            Double singleBatchScore = singleBatchResult.scores.get(i);
            double diff = Math.abs(multiBatchScore - singleBatchScore);
            if (diff > 1e-5) {
                System.out.println("DOC " + i);
                System.out.println("multiBatchScore " + multiBatchScore);
                System.out.println("singleBatchScore " + singleBatchScore);
                System.out.println("diff " + diff);
            }
        }
    }

    public static List<String> generateRandomDocuments(int count, int minWords, int maxWords) {
        List<String> documents = new ArrayList<>(count);
        Random random = new Random();
        String[] wordPool = {
            "what",
            "is",
            "the",
            "capital",
            "city",
            "of",
            "serbia",
            "belgrade",
            "europe",
            "river",
            "danube",
            "sava",
            "location",
            "country",
            "population",
            "history",
            "framework",
            "performance",
            "optimization",
            "matrix",
            "tensor",
            "onnx",
            "model",
            "inference",
            "java",
            "langchain4j",
            "embedding",
            "query",
            "document",
            "batch",
            "size",
            "padding",
            "latency",
            "speed",
            "reranker",
            "scoring",
            "cross",
            "encoder",
            "transformer",
            "attention",
            "mask",
            "weights",
            "quantization",
            "int8",
            "float32",
            "precision",
            "noise",
            "rag",
            "retrieval",
            "augmented",
            "generation",
            "software",
            "development",
            "architecture",
            "btrfs",
            "filesystem",
            "linux",
            "fedora",
            "terminal",
            "bash",
            "prompt",
            "git",
            "branch",
            "repository",
            "commit",
            "cache",
            "memory",
            "heap",
            "garbage",
            "collector",
            "eclipse",
            "ide",
            "project",
            "maven",
            "gradle",
            "build",
            "compile",
            "dependency",
            "array",
            "index",
            "mapping",
            "jumps",
            "over",
            "runs",
            "executes",
            "processes",
            "loads",
            "parses",
            "tokenizes",
            "calculates",
            "improves",
            "fixes",
            "bypasses",
            "reduces",
            "accelerates",
            "evaluates",
            "and",
            "but",
            "with",
            "without",
            "through",
            "using",
            "highly",
            "efficient",
            "stable"
        };

        for (int i = 0; i < count; i++) {
            int numWords = random.nextInt((maxWords - minWords) + 1) + minWords;
            StringBuilder sb = new StringBuilder();

            for (int w = 0; w < numWords; w++) {
                String randomWord = wordPool[random.nextInt(wordPool.length)];
                sb.append(randomWord);
                if (w < numWords - 1) {
                    sb.append(" ");
                }
            }
            documents.add(sb.toString());
        }
        return documents;
    }

    private static Path getCacheDirectory() {
        String xdgCache = System.getenv("XDG_CACHE_HOME");
        Path cacheDirectory;
        if (xdgCache != null && !xdgCache.trim().isEmpty()) {
            cacheDirectory = Path.of(xdgCache, LANGCHAIN4J, MODELS);
        } else {
            String userHome = System.getProperty("user.home");
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                String localAppData = System.getenv("LOCALAPPDATA");
                if (localAppData != null && !localAppData.isEmpty()) {
                    cacheDirectory = Path.of(localAppData, LANGCHAIN4J, MODELS);
                } else {
                    cacheDirectory = Path.of(userHome, "AppData", "Local", LANGCHAIN4J, MODELS);
                }
            } else if (os.contains("mac")) {
                cacheDirectory = Path.of(userHome, "Library", "Caches", LANGCHAIN4J, MODELS);
            } else {
                cacheDirectory = Path.of(userHome, ".cache", LANGCHAIN4J, MODELS);
            }
        }
        return cacheDirectory;
    }

    public static Path getOrDownload(Path targetRepoDir, String fileName, String downloadUrl) throws IOException {
        Path targetFile = targetRepoDir.resolve(fileName);

        if (Files.exists(targetFile)) {
            return targetFile.toAbsolutePath();
        }
        Path tempFile = targetRepoDir.resolve(fileName + ".part");
        int maxRetries = 5;
        int attempt = 0;
        long expectedSize = -1;
        while (attempt < maxRetries) {
            HttpURLConnection connection = null;
            try {
                long existingBytes = Files.exists(tempFile) ? Files.size(tempFile) : 0;
                String currentUrl = downloadUrl;
                int redirectCount = 0;
                int responseCode = 0;
                while (redirectCount < 5) {
                    URL url = URI.create(currentUrl).toURL();
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(30000);
                    connection.setReadTimeout(30000);
                    connection.setInstanceFollowRedirects(false);
                    if (existingBytes > 0 && redirectCount == 0) {
                        connection.setRequestProperty("Range", "bytes=" + existingBytes + "-");
                    }
                    responseCode = connection.getResponseCode();
                    if (responseCode == java.net.HttpURLConnection.HTTP_MOVED_TEMP
                            || responseCode == java.net.HttpURLConnection.HTTP_MOVED_PERM
                            || responseCode == 303
                            || responseCode == 307
                            || responseCode == 308) {
                        String newUrl = connection.getHeaderField("Location");
                        connection.disconnect();
                        if (newUrl != null && newUrl.startsWith("/")) {
                            currentUrl = "https://huggingface.co" + newUrl;
                        } else {
                            currentUrl = newUrl;
                        }
                        redirectCount++;
                    } else {
                        break;
                    }
                }
                if (existingBytes > 0 && responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    connection.disconnect();
                    URL finalUrl = URI.create(currentUrl).toURL();
                    connection = (HttpURLConnection) finalUrl.openConnection();
                    connection.setConnectTimeout(30000);
                    connection.setReadTimeout(30000);
                    connection.setRequestProperty("Range", "bytes=" + existingBytes + "-");
                    responseCode = connection.getResponseCode();
                }
                if (responseCode != java.net.HttpURLConnection.HTTP_OK
                        && responseCode != java.net.HttpURLConnection.HTTP_PARTIAL) {
                    throw new IOException("HTTP error code: " + responseCode + " for URL: " + currentUrl);
                }
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    expectedSize = connection.getContentLengthLong();
                    existingBytes = 0;
                } else if (responseCode == java.net.HttpURLConnection.HTTP_PARTIAL) {
                    String contentRange = connection.getHeaderField("Content-Range");
                    if (contentRange != null) {
                        expectedSize = Long.parseLong(contentRange.substring(contentRange.lastIndexOf("/") + 1));
                    }
                }
                System.out.println(String.format(
                        "Streaming (Attempt %d/%d). Current progress: %d MB / %d MB...",
                        (attempt + 1), maxRetries, (existingBytes / (1024 * 1024)), (expectedSize / (1024 * 1024))));
                try (InputStream in = new java.io.BufferedInputStream(connection.getInputStream());
                        java.io.OutputStream out = Files.newOutputStream(
                                tempFile,
                                existingBytes > 0
                                        ? java.nio.file.StandardOpenOption.APPEND
                                        : java.nio.file.StandardOpenOption.CREATE)) {
                    byte[] buffer = new byte[16384];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                long actualSize = Files.size(tempFile);
                if (expectedSize > 0 && actualSize == expectedSize) {
                    Files.move(tempFile, targetFile, ATOMIC_MOVE);
                    System.out.println("Download complete and verified successfully!");
                    break;
                } else {
                    System.out.println("Stream ended early. Current size: " + actualSize + " bytes. Retrying...");
                }
            } catch (Exception e) {
                System.err.println("Network error encountered: " + e.getMessage());
                attempt++;
                if (attempt >= maxRetries) {
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException ignored) {
                    }
                    throw new IOException("Failed to download model after " + maxRetries + " attempts", e);
                }
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        return targetFile.toAbsolutePath();
    }
}
