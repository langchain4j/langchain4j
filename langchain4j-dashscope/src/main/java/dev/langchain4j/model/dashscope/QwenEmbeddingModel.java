package dev.langchain4j.model.dashscope;

import com.alibaba.dashscope.embeddings.*;
import com.alibaba.dashscope.exception.NoApiKeyException;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class QwenEmbeddingModel implements EmbeddingModel {
    public static final String TYPE_KEY = "type";
    public static final String TYPE_QUERY = "query";
    public static final String TYPE_DOCUMENT = "document";
    private final String apiKey;
    private final String modelName;
    private final TextEmbedding embedding;

    protected QwenEmbeddingModel(String apiKey, String modelName) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        embedding = new TextEmbedding();
    }

    private boolean containsDocuments(List<TextSegment> textSegments) {
        return textSegments.stream()
                .map(TextSegment::metadata)
                .map(metadata -> metadata.get(TYPE_KEY))
                .filter(TYPE_DOCUMENT::equalsIgnoreCase)
                .anyMatch(Utils::isNullOrBlank);
    }

    private boolean containsQueries(List<TextSegment> textSegments) {
        return textSegments.stream()
                .map(TextSegment::metadata)
                .map(metadata -> metadata.get(TYPE_KEY))
                .filter(TYPE_QUERY::equalsIgnoreCase)
                .anyMatch(Utils::isNullOrBlank);
    }

    private List<Embedding> embedTexts(List<TextSegment> textSegments, TextEmbeddingParam.TextType textType) {
        TextEmbeddingParam param = TextEmbeddingParam.builder()
                .apiKey(apiKey)
                .model(modelName)
                .textType(textType)
                .texts(textSegments.stream()
                        .map(TextSegment::text)
                        .collect(Collectors.toList()))
                .build();
        try {
            return Optional.of(embedding.call(param))
                    .map(TextEmbeddingResult::getOutput)
                    .map(TextEmbeddingOutput::getEmbeddings)
                    .orElse(Collections.emptyList())
                    .stream()
                    .map(TextEmbeddingResultItem::getEmbedding)
                    .map(doubleList -> doubleList.stream().map(Double::floatValue).collect(Collectors.toList()))
                    .map(Embedding::from)
                    .collect(Collectors.toList());
        } catch (NoApiKeyException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Embedding> embedAll(List<TextSegment> textSegments) {
        boolean queries = containsQueries(textSegments);

        if (!queries) {
            // default all documents
            return embedTexts(textSegments, TextEmbeddingParam.TextType.DOCUMENT);
        } else {
            boolean documents = containsDocuments(textSegments);
            if (!documents) {
                return embedTexts(textSegments, TextEmbeddingParam.TextType.QUERY);
            } else {
                // This is a mixed collection of queries and documents. Embed one by one.
                List<Embedding> embeddings = new ArrayList<>(textSegments.size());
                for (TextSegment textSegment: textSegments) {
                    List<Embedding> result;
                    if (TYPE_QUERY.equalsIgnoreCase(textSegment.metadata(TYPE_KEY))) {
                        result = embedTexts(Collections.singletonList(textSegment), TextEmbeddingParam.TextType.QUERY);
                    } else {
                        result = embedTexts(Collections.singletonList(textSegment), TextEmbeddingParam.TextType.DOCUMENT);
                    }
                    embeddings.addAll(result);
                }
                return embeddings;
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }
    public static class Builder {
        private String apiKey;
        private String modelName;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        protected void ensureOptions() {
            if (Utils.isNullOrBlank(apiKey)) {
                throw new IllegalArgumentException("DashScope api key must be defined. It can be generated here: https://dashscope.console.aliyun.com/apiKey");
            }
            modelName = Utils.isNullOrBlank(modelName) ? QwenModelName.TEXT_EMBEDDING_V1 : modelName;
        }

        public QwenEmbeddingModel build() {
            ensureOptions();
            return new QwenEmbeddingModel(apiKey, modelName);
        }
    }
}
