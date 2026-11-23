package dev.langchain4j.model.embedding.onnx;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public abstract class AbstractInProcessEmbeddingModel extends DimensionAwareEmbeddingModel {

    private final Executor executor;

    protected AbstractInProcessEmbeddingModel(Executor executor) {
        this.executor = getOrDefault(executor, this::createDefaultExecutor);
    }

    private Executor createDefaultExecutor() {
        int threadPoolSize = Runtime.getRuntime().availableProcessors();
        ThreadPoolExecutor threadPoolExecutor =
                new ThreadPoolExecutor(threadPoolSize, threadPoolSize, 1, SECONDS, new LinkedBlockingQueue<>());
        threadPoolExecutor.allowCoreThreadTimeOut(true);
        return threadPoolExecutor;
    }

    protected static OnnxBertBiEncoder loadFromJar(
            String modelFileName, String tokenizerFileName, PoolingMode poolingMode) {
        InputStream model = Thread.currentThread().getContextClassLoader().getResourceAsStream(modelFileName);
        InputStream tokenizer = Thread.currentThread().getContextClassLoader().getResourceAsStream(tokenizerFileName);
        return new OnnxBertBiEncoder(model, tokenizer, poolingMode);
    }

    static OnnxBertBiEncoder loadFromFileSystem(Path pathToModel, Path pathToTokenizer, PoolingMode poolingMode) {
        return new OnnxBertBiEncoder(pathToModel, pathToTokenizer, poolingMode);
    }

    protected abstract OnnxBertBiEncoder model();

    protected Encoder encoder() {
        return asEncoder(model());
    }

    static Encoder asEncoder(OnnxBertBiEncoder model) {
        return new OnnxBertBiEncoderAdapter(model);
    }

    static Encoder asEncoder(OnnxBpeBiEncoder model) {
        return new OnnxBpeBiEncoderAdapter(model);
    }

    private static final class OnnxBertBiEncoderAdapter implements Encoder {

        private final OnnxBertBiEncoder model;

        private OnnxBertBiEncoderAdapter(OnnxBertBiEncoder model) {
            this.model = model;
        }

        @Override
        public EmbeddingAndTokenCount embed(String text) {
            return model.embed(text);
        }

        @Override
        public int countTokens(String text) {
            return model.countTokens(text);
        }
    }

    private static final class OnnxBpeBiEncoderAdapter implements Encoder {

        private final OnnxBpeBiEncoder model;

        private OnnxBpeBiEncoderAdapter(OnnxBpeBiEncoder model) {
            this.model = model;
        }

        @Override
        public EmbeddingAndTokenCount embed(String text) {
            return model.embed(text);
        }

        @Override
        public int countTokens(String text) {
            return model.countTokens(text);
        }
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        ensureNotEmpty(segments, "segments");
        if (segments.size() == 1) {
            return embedInTheSameThread(segments.get(0));
        } else {
            return parallelizeEmbedding(segments);
        }
    }

    private Response<List<Embedding>> embedInTheSameThread(TextSegment segment) {
        EmbeddingAndTokenCount embeddingAndTokenCount = encoder().embed(segment.text());
        return Response.from(
                singletonList(Embedding.from(embeddingAndTokenCount.embedding)),
                new TokenUsage(embeddingAndTokenCount.tokenCount));
    }

    private Response<List<Embedding>> parallelizeEmbedding(List<TextSegment> segments) {
        List<CompletableFuture<EmbeddingAndTokenCount>> futures = segments.stream()
                .map(segment -> supplyAsync(() -> encoder().embed(segment.text()), executor))
                .collect(toList());

        int inputTokenCount = 0;
        List<Embedding> embeddings = new ArrayList<>();

        for (CompletableFuture<EmbeddingAndTokenCount> future : futures) {
            try {
                EmbeddingAndTokenCount embeddingAndTokenCount = future.get();
                embeddings.add(Embedding.from(embeddingAndTokenCount.embedding));
                inputTokenCount += embeddingAndTokenCount.tokenCount;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        return Response.from(embeddings, new TokenUsage(inputTokenCount));
    }
}
