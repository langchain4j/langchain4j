package dev.langchain4j.model.dashscope;

import com.alibaba.dashscope.embeddings.*;
import com.alibaba.dashscope.exception.NoApiKeyException;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.dashscope.spi.QwenEmbeddingModelBuilderFactory;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;

import java.util.*;
import java.util.stream.Collectors;

import static com.alibaba.dashscope.embeddings.TextEmbeddingParam.TextType.DOCUMENT;
import static com.alibaba.dashscope.embeddings.TextEmbeddingParam.TextType.QUERY;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.singletonList;

/**
 * An implementation of an {@link dev.langchain4j.model.embedding.EmbeddingModel} that uses
 * <a href="https://help.aliyun.com/zh/dashscope/developer-reference/text-embedding-api-details">DashScope Embeddings API</a>.
 */
public class QwenEmbeddingModel extends DimensionAwareEmbeddingModel {

    public static final String TYPE_KEY = "type";
    public static final String TYPE_QUERY = "query";
    public static final String TYPE_DOCUMENT = "document";
    private static final int BATCH_SIZE = 25;

    private final String apiKey;
    private final String modelName;
    private final TextEmbedding embedding;

    @Builder
    public QwenEmbeddingModel(String baseUrl, String apiKey, String modelName) {
        if (Utils.isNullOrBlank(apiKey)) {
            throw new IllegalArgumentException("DashScope api key must be defined. It can be generated here: https://dashscope.console.aliyun.com/apiKey");
        }
        this.modelName = Utils.isNullOrBlank(modelName) ? QwenModelName.TEXT_EMBEDDING_V2 : modelName;
        this.apiKey = apiKey;
        this.embedding = Utils.isNullOrBlank(baseUrl) ? new TextEmbedding() : new TextEmbedding(baseUrl);
    }

    private boolean containsDocuments(List<TextSegment> textSegments) {
        return textSegments.stream()
                .map(TextSegment::metadata)
                .map(metadata -> metadata.getString(TYPE_KEY))
                .anyMatch(TYPE_DOCUMENT::equalsIgnoreCase);
    }

    private boolean containsQueries(List<TextSegment> textSegments) {
        return textSegments.stream()
                .map(TextSegment::metadata)
                .map(metadata -> metadata.getString(TYPE_KEY))
                .anyMatch(TYPE_QUERY::equalsIgnoreCase);
    }

    private Response<List<Embedding>> embedTexts(List<TextSegment> textSegments,
                                                 TextEmbeddingParam.TextType textType) {
        int size = textSegments.size();
        if (size < BATCH_SIZE) {
            return batchEmbedTexts(textSegments, textType);
        }

        List<Embedding> allEmbeddings = new ArrayList<>(size);
        TokenUsage allUsage = null;
        for (int i = 0; i < size; i += BATCH_SIZE) {
            List<TextSegment> batchTextSegments = textSegments.subList(i, Math.min(size, i + BATCH_SIZE));
            Response<List<Embedding>> batchResponse = batchEmbedTexts(batchTextSegments, textType);
            allEmbeddings.addAll(batchResponse.content());
            allUsage = TokenUsage.sum(allUsage, batchResponse.tokenUsage());
        }

        return Response.from(allEmbeddings, allUsage);
    }

    private Response<List<Embedding>> batchEmbedTexts(List<TextSegment> textSegments, TextEmbeddingParam.TextType textType) {
        TextEmbeddingParam param = TextEmbeddingParam.builder()
                .apiKey(apiKey)
                .model(modelName)
                .textType(textType)
                .texts(textSegments.stream()
                        .map(TextSegment::text)
                        .collect(Collectors.toList()))
                .build();
        try {
            TextEmbeddingResult generationResult = embedding.call(param);
            // total_tokens are the same as input_tokens in the embedding model
            TokenUsage usage = new TokenUsage(generationResult.getUsage().getTotalTokens());
            List<Embedding> embeddings = Optional.of(generationResult)
                    .map(TextEmbeddingResult::getOutput)
                    .map(TextEmbeddingOutput::getEmbeddings)
                    .orElse(Collections.emptyList())
                    .stream()
                    .sorted(Comparator.comparing(TextEmbeddingResultItem::getTextIndex))
                    .map(TextEmbeddingResultItem::getEmbedding)
                    .map(doubleList -> doubleList.stream().map(Double::floatValue).collect(Collectors.toList()))
                    .map(Embedding::from)
                    .collect(Collectors.toList());
            return Response.from(embeddings, usage);
        } catch (NoApiKeyException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        boolean queries = containsQueries(textSegments);

        if (!queries) {
            // default all documents
            return embedTexts(textSegments, DOCUMENT);
        } else {
            boolean documents = containsDocuments(textSegments);
            if (!documents) {
                return embedTexts(textSegments, QUERY);
            } else {
                // This is a mixed collection of queries and documents. Embed one by one.
                List<Embedding> embeddings = new ArrayList<>(textSegments.size());
                Integer tokens = null;
                for (TextSegment textSegment : textSegments) {
                    Response<List<Embedding>> result;
                    if (TYPE_QUERY.equalsIgnoreCase(textSegment.metadata().getString(TYPE_KEY))) {
                        result = embedTexts(singletonList(textSegment), QUERY);
                    } else {
                        result = embedTexts(singletonList(textSegment), DOCUMENT);
                    }
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
        }
    }

    public static QwenEmbeddingModelBuilder builder() {
        for (QwenEmbeddingModelBuilderFactory factory : loadFactories(QwenEmbeddingModelBuilderFactory.class)) {
            return factory.get();
        }
        return new QwenEmbeddingModelBuilder();
    }

    public static class QwenEmbeddingModelBuilder {
        public QwenEmbeddingModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
