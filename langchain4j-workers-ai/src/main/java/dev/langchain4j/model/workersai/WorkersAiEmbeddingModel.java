package dev.langchain4j.model.workersai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.workersai.client.AbstractWorkersAIModel;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * WorkerAI Embedding model.
 * <a href="https://developers.cloudflare.com/api/operations/workers-ai-post-run-model">...</a>
 */
@Slf4j
public class WorkersAiEmbeddingModel extends AbstractWorkersAIModel implements EmbeddingModel {

    /**
     * Constructor with Builder.
     *
     * @param builder
     *      builder.
     */
    public WorkersAiEmbeddingModel(Builder builder) {
        super(builder);
    }

    /**
     * Simple constructor.
     *
     * @param accountIdentifier
     *      account identifier.
     * @param modelName
     *      model name.
     * @param token
     *      api token from .
     */
    @SuppressWarnings("unused")
    public WorkersAiEmbeddingModel(String accountIdentifier, String modelName, String token) {
        super(accountIdentifier, modelName, token);
    }

    /** {@inheritDoc} */
    @Override
    public Response<Embedding> embed(String text) {
        try {
            dev.langchain4j.model.workersai.client.WorkersAiEmbeddingRequest req = new dev.langchain4j.model.workersai.client.WorkersAiEmbeddingRequest();
            req.getText().add(text);

            retrofit2.Response<dev.langchain4j.model.workersai.client.WorkersAiEmbeddingResponse> retrofitResponse = workerAiClient
                    .embed(req, accountIdentifier, modelName)
                    .execute();

            processErrors(retrofitResponse.body(), retrofitResponse.errorBody());
            if (retrofitResponse.body() == null) {
                throw new RuntimeException("Unexpected response: " + retrofitResponse);
            }
            dev.langchain4j.model.workersai.client.WorkersAiEmbeddingResponse.EmbeddingResult res = retrofitResponse.body().getResult();
            // Single Vector expected
            if (res.getShape().get(0) != 1) {
                throw new RuntimeException("Unexpected shape: " + res.getShape());
            }
            List<Float> embeddings = res.getData().get(0);
            float[] floatArray = new float[embeddings.size()];
            for (int i = 0; i < embeddings.size(); i++) {
                floatArray[i] = embeddings.get(i); // Unboxing Float to float
            }
            return new Response<>(new Embedding(floatArray), null, FinishReason.STOP);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        // no metadata in worker ai
        return embed(textSegment.text());
    }


    /** {@inheritDoc} */
    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Future<List<Embedding>>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        try {
            final int chunkSize = 100;
            for (int i = 0; i < textSegments.size(); i += chunkSize) {
                List<TextSegment> chunk = textSegments.subList(i, Math.min(textSegments.size(), i + chunkSize));
                Future<List<Embedding>> future = executor.submit(() -> processChunk(chunk, accountIdentifier, modelName));
                futures.add(future);
            }
            // Wait for all futures to complete and collect results
            List<Embedding> result = new ArrayList<>();
            for (Future<List<Embedding>> future : futures) {
                result.addAll(future.get());
            }
            return new Response<>(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }
    }

    /**
     * Process chunk of text segments.
     *
     * @param chunk
     *      chunk of text segments.
     * @param accountIdentifier
     *      account identifier.
     * @param modelName
     *      model name.
     * @return
     *      list of embeddings.
     * @throws IOException
     *      error occurred during invocation.
     */
    private List<Embedding> processChunk(List<TextSegment> chunk, String accountIdentifier, String modelName)
    throws IOException {
        dev.langchain4j.model.workersai.client.WorkersAiEmbeddingRequest req = new dev.langchain4j.model.workersai.client.WorkersAiEmbeddingRequest();
        for (TextSegment textSegment : chunk) {
            req.getText().add(textSegment.text());
        }
        retrofit2.Response<dev.langchain4j.model.workersai.client.WorkersAiEmbeddingResponse> retrofitResponse = workerAiClient
                .embed(req, accountIdentifier, modelName)
                .execute();
        processErrors(retrofitResponse.body(), retrofitResponse.errorBody());
        if (retrofitResponse.body() == null) {
            throw new RuntimeException("Unexpected response: " + retrofitResponse);
        }
        dev.langchain4j.model.workersai.client.WorkersAiEmbeddingResponse.EmbeddingResult res = retrofitResponse.body().getResult();

        List<List<Float>> embeddings = res.getData();
        List<Embedding> embeddingsList = new ArrayList<>();
        for (List<Float> embedding : embeddings) {
            float[] floatArray = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                floatArray[i] = embedding.get(i); // Unboxing Float to float
            }
            embeddingsList.add(new Embedding(floatArray));
        }
        return embeddingsList;
    }
}