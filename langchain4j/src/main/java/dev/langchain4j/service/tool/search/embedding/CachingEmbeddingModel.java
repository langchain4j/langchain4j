package dev.langchain4j.service.tool.search.embedding;

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
 * TODO comment on "caching"
 */
class CachingEmbeddingModel implements EmbeddingModel {

    private static final TokenUsage ZERO_TOKEN_USAGE = new TokenUsage(0, 0, 0);

    private final EmbeddingModel delegate;
    private final Map<String, Embedding> cache = new ConcurrentHashMap<>();

    public CachingEmbeddingModel(EmbeddingModel delegateEmbeddingModel) {
        this.delegate = ensureNotNull(delegateEmbeddingModel, "delegateEmbeddingModel");
    }

    @Override
    public Response<Embedding> embed(String text) {
        ensureNotNull(text, "text");

        if (cache.containsKey(text)) {
            return Response.from(cache.get(text), ZERO_TOKEN_USAGE);
        }

        Response<Embedding> response = delegate.embed(text);
        cache.put(text, response.content());
        return response;
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        ensureNotNull(textSegment, "text");

        if (cache.containsKey(textSegment.text())) {
            return Response.from(cache.get(textSegment.text()), ZERO_TOKEN_USAGE);
        }

        Response<Embedding> response = delegate.embed(textSegment);
        cache.put(textSegment.text(), response.content());
        return response;
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

                cache.put(segment.text(), embedding);
                result.set(toEmbedIndexes.get(i), embedding);
            }
            return Response.from(result, response.tokenUsage());
        }

        return Response.from(result, ZERO_TOKEN_USAGE);
    }

    @Override
    public int dimension() {
        return delegate.dimension();
    }

    @Override
    public String modelName() {
        return delegate.modelName();
    }

    void clearCache() {
        cache.clear();
    }
}
