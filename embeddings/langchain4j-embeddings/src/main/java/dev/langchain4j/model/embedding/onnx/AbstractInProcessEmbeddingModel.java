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
import dev.langchain4j.model.embedding.onnx.OnnxBertBiEncoder.EmbeddingAndTokenCount;
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
        // 获取当前线程提供的兼容类加载器。
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        return loadFromJar(contextClassLoader, modelFileName, tokenizerFileName, poolingMode);
    }

    // 使用具体模型类加载器读取内置资源，并保留线程上下文类加载器作为回退。
    protected static OnnxBertBiEncoder loadFromJar(
            Class<?> modelClass, String modelFileName, String tokenizerFileName, PoolingMode poolingMode) {
        // 获取定义具体模型类的类加载器。
        ClassLoader modelClassLoader = modelClass.getClassLoader();
        return loadFromJar(modelClassLoader, modelFileName, tokenizerFileName, poolingMode);
    }

    // 从指定类加载器加载模型和分词器资源。
    private static OnnxBertBiEncoder loadFromJar(
            ClassLoader modelClassLoader, String modelFileName, String tokenizerFileName, PoolingMode poolingMode) {
        // 加载模型资源。
        InputStream model = getResourceAsStream(modelClassLoader, modelFileName);
        // 加载分词器资源。
        InputStream tokenizer = getResourceAsStream(modelClassLoader, tokenizerFileName);
        return new OnnxBertBiEncoder(model, tokenizer, poolingMode);
    }

    // 优先使用具体模型类加载器读取资源，并保留线程上下文类加载器作为回退。
    static InputStream getResourceAsStream(ClassLoader modelClassLoader, String resourceName) {
        // 使用定义具体模型类的类加载器读取模型模块资源。
        InputStream resource = modelClassLoader == null ? null : modelClassLoader.getResourceAsStream(resourceName);
        if (resource != null) {
            return resource;
        }

        // 获取容器或框架提供的线程上下文类加载器。
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        return contextClassLoader == null ? null : contextClassLoader.getResourceAsStream(resourceName);
    }

    static OnnxBertBiEncoder loadFromFileSystem(Path pathToModel, Path pathToTokenizer, PoolingMode poolingMode) {
        return new OnnxBertBiEncoder(pathToModel, pathToTokenizer, poolingMode);
    }

    protected abstract OnnxBertBiEncoder model();

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
        EmbeddingAndTokenCount embeddingAndTokenCount = model().embed(segment.text());
        return Response.from(
                singletonList(Embedding.from(embeddingAndTokenCount.embedding)),
                new TokenUsage(embeddingAndTokenCount.tokenCount - 2) // do not count special tokens [CLS] and [SEP])
                );
    }

    private Response<List<Embedding>> parallelizeEmbedding(List<TextSegment> segments) {
        List<CompletableFuture<EmbeddingAndTokenCount>> futures = segments.stream()
                .map(segment -> supplyAsync(() -> model().embed(segment.text()), executor))
                .collect(toList());

        int inputTokenCount = 0;
        List<Embedding> embeddings = new ArrayList<>();

        for (CompletableFuture<EmbeddingAndTokenCount> future : futures) {
            try {
                EmbeddingAndTokenCount embeddingAndTokenCount = future.get();
                embeddings.add(Embedding.from(embeddingAndTokenCount.embedding));
                inputTokenCount += embeddingAndTokenCount.tokenCount - 2; // do not count special tokens [CLS] and [SEP]
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
