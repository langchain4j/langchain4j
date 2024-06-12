package dev.langchain4j.model.ark;

import com.volcengine.ark.runtime.model.embeddings.EmbeddingRequest;
import com.volcengine.ark.runtime.model.embeddings.EmbeddingResult;
import com.volcengine.ark.runtime.service.ArkService;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ark.spi.ArkEmbeddingModelBuilderFactory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;

import java.util.*;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.singletonList;

/**
 * An implementation of an {@link EmbeddingModel} that uses
 * <a href="https://www.volcengine.com/docs/82379/1263512">Ark Embeddings API</a>.
 */
public class ArkEmbeddingModel implements EmbeddingModel {

    private static final int MAX_BATCH_SIZE = 25;

    private final String apiKey;
    private final String model;
    private final Integer dimensions;
    private final String user;
    private final ArkService service;

    @Builder
    public ArkEmbeddingModel(String apiKey, String model, Integer dimensions, String user) {
        if (isNullOrBlank(apiKey)) {
            throw new IllegalArgumentException("Ark api key must be defined. It can be generated here: https://www.volcengine.com/docs/82379/1263279");
        }
        if (isNullOrBlank(model)) {
            throw new IllegalArgumentException("Ark model(endpoint_id) must be defined. ");
        }
        this.apiKey = apiKey;
        this.model = model;
        this.dimensions = getOrDefault(dimensions, 2048);
        this.user = user;
        this.service = new ArkService(apiKey);
    }

    private Response<List<Embedding>> embedTexts(List<TextSegment> textSegments) {
        int size = textSegments.size();
        if (size < MAX_BATCH_SIZE) {
            return batchEmbedTexts(textSegments);
        }

        List<Embedding> allEmbeddings = new ArrayList<>(size);
        TokenUsage allUsage = null;
        int fromIndex = 0;
        int toIndex = MAX_BATCH_SIZE;
        while (fromIndex < size) {
            List<TextSegment> batchTextSegments = textSegments.subList(fromIndex, toIndex);
            Response<List<Embedding>> batchResponse = batchEmbedTexts(batchTextSegments);
            allEmbeddings.addAll(batchResponse.content());
            allUsage = TokenUsage.sum(allUsage, batchResponse.tokenUsage());
            fromIndex = toIndex;
            toIndex = Math.min(size, fromIndex + MAX_BATCH_SIZE);
        }

        return Response.from(allEmbeddings, allUsage);
    }

    private Response<List<Embedding>> batchEmbedTexts(List<TextSegment> textSegments) {
        EmbeddingRequest embeddingRequest = EmbeddingRequest.builder()
                .model(model)
                .user(user)
                .input(textSegments.stream()
                        .map(TextSegment::text)
                        .collect(Collectors.toList()))
                .build();
        EmbeddingResult embeddingResult = service.createEmbeddings(embeddingRequest);
        // shutdown service
        service.shutdownExecutor();

        TokenUsage usage = new TokenUsage((int) embeddingResult.getUsage().getCompletionTokens());
        List<Embedding> embeddings = Optional.of(embeddingResult)
                .map(EmbeddingResult::getData)
                .orElse(Collections.emptyList())
                .stream()
                .sorted(Comparator.comparing(com.volcengine.ark.runtime.model.embeddings.Embedding::getIndex))
                .map(com.volcengine.ark.runtime.model.embeddings.Embedding::getEmbedding)
                .map(doubleList -> doubleList.stream().map(Double::floatValue).collect(Collectors.toList()))
                .map(Embedding::from)
                .collect(Collectors.toList());
        return Response.from(embeddings, usage);
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Embedding> embeddings = new ArrayList<>(textSegments.size());
        Integer tokens = null;
        for (TextSegment textSegment : textSegments) {
            Response<List<Embedding>> result = embedTexts(singletonList(textSegment));
            embeddings.addAll(result.content());
            if (result.tokenUsage() == null) {
                continue;
            }
            if (tokens == null) {
                tokens = result.tokenUsage().inputTokenCount();
            } else {
                tokens += result.tokenUsage().inputTokenCount();
            }
        }
        return Response.from(embeddings, new TokenUsage(tokens));
    }

    public static ArkEmbeddingModelBuilder builder() {
        for (ArkEmbeddingModelBuilderFactory factory : loadFactories(ArkEmbeddingModelBuilderFactory.class)) {
            return factory.get();
        }
        return new ArkEmbeddingModelBuilder();
    }

    public static class ArkEmbeddingModelBuilder {
        public ArkEmbeddingModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
