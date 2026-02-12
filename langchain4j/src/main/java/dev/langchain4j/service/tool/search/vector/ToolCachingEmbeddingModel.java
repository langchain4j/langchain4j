package dev.langchain4j.service.tool.search.vector;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Embedding model that caches embeddings of tool descriptions, since they rarely change.
 * The embedding of a query is never cached.
 * <p>
 * The cache is never cleared automatically, as the risk of a memory leak is low:
 * the number of tools in an application is usually limited and does not grow over time.
 * <p>
 * The cache can be cleared manually by calling {@link #clearCache()}.
 */
@Experimental
class ToolCachingEmbeddingModel implements EmbeddingModel {

    private static final TokenUsage ZERO_TOKEN_USAGE = new TokenUsage(0, 0, 0);

    private final EmbeddingModel delegate;
    private final Map<String, Embedding> cache = new ConcurrentHashMap<>();

    public ToolCachingEmbeddingModel(EmbeddingModel delegateEmbeddingModel) {
        this.delegate = ensureNotNull(delegateEmbeddingModel, "delegateEmbeddingModel");
    }

    void clearCache() {
        cache.clear();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        ensureNotNull(textSegments, "textSegments");

        List<Embedding> result = new ArrayList<>(textSegments.size());
        List<TextSegment> toEmbed = new ArrayList<>();
        List<Integer> toEmbedIndexes = new ArrayList<>();

        for (int i = 0; i < textSegments.size(); i++) {
            TextSegment segment = textSegments.get(i);
            Embedding cached = cache.get(segment.text());

            if (cached != null) {
                result.add(cached);
            } else {
                result.add(null); // placeholder
                toEmbed.add(segment);
                toEmbedIndexes.add(i);
            }
        }

        if (!toEmbed.isEmpty()) {
            Response<List<Embedding>> response = delegate.embedAll(toEmbed);
            List<Embedding> embeddings = response.content();

            for (int i = 0; i < embeddings.size(); i++) {
                Embedding embedding = embeddings.get(i);
                TextSegment segment = toEmbed.get(i);

                if (i != 0) { // index 0 is for search query, it should not be cached
                    cache.put(segment.text(), embedding);
                }
                result.set(toEmbedIndexes.get(i), embedding);
            }
            return Response.from(result, response.tokenUsage());
        }

        return Response.from(result, ZERO_TOKEN_USAGE);
    }

    @Override
    public Response<Embedding> embed(String text) {
        throw new IllegalStateException("should not be called");
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        throw new IllegalStateException("should not be called");
    }

    @Override
    public int dimension() {
        return delegate.dimension();
    }

    @Override
    public String modelName() {
        return delegate.modelName();
    }
}
