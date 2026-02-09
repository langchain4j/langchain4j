package dev.langchain4j.service.tool.search.vector;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.internal.Json;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.tool.search.ToolSearchRequest;
import dev.langchain4j.service.tool.search.ToolSearchResult;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.toBase64;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * A {@link ToolSearchStrategy} that uses vector similarity search
 * to find relevant tools based on semantic meaning.
 * TODO comment on tool description, caching
 */
@Experimental
public class VectorToolSearchStrategy implements ToolSearchStrategy {

    private static final String DEFAULT_TOOL_NAME = "tool_search_tool";
    private static final String DEFAULT_TOOL_DESCRIPTION = "Finds available tools using semantic vector search";
    private static final String DEFAULT_TOOL_ARGUMENT_NAME = "query";
    private static final String DEFAULT_TOOL_ARGUMENT_DESCRIPTION = "Natural language query describing desired tool";
    private static final int DEFAULT_MAX_RESULTS = 5;
    private static final double DEFAULT_MIN_SCORE = 0.0;
    private static final String METADATA_TOOL_NAME = "toolName";

    private final ToolSpecification toolSearchTool;
    private final EmbeddingModel embeddingModel;
    private final int maxResults;
    private final double minScore;
    private final String toolArgumentName;
    private final boolean throwToolArgumentsExceptions;

    public VectorToolSearchStrategy(EmbeddingModel embeddingModel) {
        this(builder().embeddingModel(embeddingModel));
    }

    public VectorToolSearchStrategy(Builder builder) {
        Boolean cacheEmbeddings = getOrDefault(builder.cacheEmbeddings, true);
        if (cacheEmbeddings) {
            this.embeddingModel = ensureNotNull(new ToolCachingEmbeddingModel(builder.embeddingModel), "embeddingModel");
        } else {
            this.embeddingModel = ensureNotNull(builder.embeddingModel, "embeddingModel");
        }
        this.toolArgumentName = getOrDefault(builder.toolArgumentName, DEFAULT_TOOL_ARGUMENT_NAME);

        this.toolSearchTool = ToolSpecification.builder()
                .name(getOrDefault(builder.toolName, DEFAULT_TOOL_NAME))
                .description(getOrDefault(builder.toolDescription, DEFAULT_TOOL_DESCRIPTION))
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(toolArgumentName, getOrDefault(builder.toolArgumentDescription, DEFAULT_TOOL_ARGUMENT_DESCRIPTION))
                        .required(toolArgumentName)
                        .build())
                .build();

        this.maxResults = getOrDefault(builder.maxResults, DEFAULT_MAX_RESULTS);
        this.minScore = getOrDefault(builder.minScore, DEFAULT_MIN_SCORE);
        this.throwToolArgumentsExceptions = getOrDefault(builder.throwToolArgumentsExceptions, false);
    }

    @Override
    public List<ToolSpecification> getToolSearchTools(InvocationContext context) {
        return List.of(toolSearchTool);
    }

    @Override
    public ToolSearchResult search(ToolSearchRequest request) {

        String query = extractQuery(request.toolExecutionRequest().arguments());

        List<TextSegment> segments = new ArrayList<>();
        segments.add(TextSegment.from(query));

        request.availableTools().stream()
                .map(tool -> {
                    String text = format(tool);
                    Metadata metadata = Metadata.from(METADATA_TOOL_NAME, tool.name());
                    return TextSegment.from(text, metadata);
                })
                .forEach(segments::add);

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        store.addAll(embeddings.subList(1, embeddings.size()), segments.subList(1, segments.size()));

        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .query(query)
                .queryEmbedding(embeddings.get(0))
                .maxResults(maxResults)
                .minScore(minScore)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = store.search(embeddingSearchRequest);

        List<String> toolNames = searchResult.matches().stream()
                .map(match -> (String) match.embedded().metadata().getString(METADATA_TOOL_NAME))
                .toList();

        return new ToolSearchResult(toolNames);
    }

    private String extractQuery(String argumentsJson) {
        Map<String, Object> map = parseMap(argumentsJson);

        if (isNullOrEmpty(map) || !map.containsKey(toolArgumentName)) {
            String message = "Missing required tool argument '%s'".formatted(toolArgumentName);
            throwException(message, null);
        }

        return map.get(toolArgumentName).toString();
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return Json.fromJson(json, Map.class);
        } catch (Exception e) {
            String message = "Failed to parse tool search arguments: '%s' (base64: '%s')".formatted(json, toBase64(json));
            throwException(message, e);
            return null; // unreachable
        }
    }

    protected String format(ToolSpecification tool) {
        return tool.name() + ": " + tool.description();
    }

    private void throwException(String message, Exception e) {
        if (throwToolArgumentsExceptions) {
            throw e == null
                    ? new ToolArgumentsException(message)
                    : new ToolArgumentsException(message, e);
        } else {
            throw e == null
                    ? new ToolExecutionException(message)
                    : new ToolExecutionException(message, e);
        }
    }

    public void clearEmbeddingsCache() {
        if (embeddingModel instanceof ToolCachingEmbeddingModel cachingEmbeddingModel) {
            cachingEmbeddingModel.clearCache();
        } else {
            throw new IllegalStateException("Not caching embeddings, nothing to clear");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private EmbeddingModel embeddingModel;
        private Integer maxResults;
        private Double minScore;
        private String toolName;
        private String toolDescription;
        private String toolArgumentName;
        private String toolArgumentDescription;
        private Boolean throwToolArgumentsExceptions;
        private Boolean cacheEmbeddings;

        public Builder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        public Builder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder toolDescription(String toolDescription) {
            this.toolDescription = toolDescription;
            return this;
        }

        public Builder toolArgumentName(String toolArgumentName) {
            this.toolArgumentName = toolArgumentName;
            return this;
        }

        public Builder toolArgumentDescription(String toolArgumentDescription) {
            this.toolArgumentDescription = toolArgumentDescription;
            return this;
        }

        public Builder throwToolArgumentsExceptions(Boolean throwToolArgumentsExceptions) {
            this.throwToolArgumentsExceptions = throwToolArgumentsExceptions;
            return this;
        }

        public Builder cacheEmbeddings(Boolean cacheEmbeddings) {
            this.cacheEmbeddings = cacheEmbeddings;
            return this;
        }

        public VectorToolSearchStrategy build() {
            return new VectorToolSearchStrategy(this);
        }
    }
}
