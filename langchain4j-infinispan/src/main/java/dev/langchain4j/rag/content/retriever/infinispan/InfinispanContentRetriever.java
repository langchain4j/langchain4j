package dev.langchain4j.rag.content.retriever.infinispan;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.infinispan.InfinispanEmbeddingStore;
import dev.langchain4j.store.embedding.infinispan.LangChainInfinispanItem;
import dev.langchain4j.store.embedding.infinispan.LangChainMetadata;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.infinispan.client.hotrod.RemoteCache;

/**
 * An Infinispan-backed {@link ContentRetriever} that uses full-text search (Ickle queries)
 * to retrieve relevant content without requiring embeddings.
 * <p>
 * Built from an existing {@link InfinispanEmbeddingStore}, reusing its remote cache and
 * configuration. The store's schema must have full-text indexing enabled on the text field
 * (use {@link dev.langchain4j.store.embedding.infinispan.InfinispanStoreConfiguration#TEXT_FIELD_ANNOTATION_FULLTEXT}
 * when building the store).
 */
public class InfinispanContentRetriever implements ContentRetriever {

    private static final int DEFAULT_MAX_RESULTS = 3;

    private final RemoteCache<String, LangChainInfinispanItem> remoteCache;
    private final String langchainItemFullType;
    private final int maxResults;

    public InfinispanContentRetriever(InfinispanEmbeddingStore embeddingStore, int maxResults) {
        ensureNotNull(embeddingStore, "embeddingStore");
        this.remoteCache = embeddingStore.remoteCache();
        this.langchainItemFullType = embeddingStore.storeConfiguration().langchainItemFullType();
        this.maxResults = ensureGreaterThanZero(maxResults, "maxResults");
    }

    @Override
    public List<Content> retrieve(Query query) {
        return toContents(buildQuery(query).list());
    }

    @Override
    public CompletableFuture<List<Content>> retrieveAsync(Query query) {
        try {
            return buildQuery(query)
                    .executeAsync()
                    .thenApply(result -> toContents(result.list()))
                    .toCompletableFuture();
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(t);
        }
    }

    private org.infinispan.commons.api.query.Query<Object[]> buildQuery(Query query) {
        ensureNotNull(query, "query");
        List<String> terms = splitTerms(query.text());
        String termList;
        if (terms.isEmpty()) {
            termList = "''";
        } else if (terms.size() == 1) {
            termList = "'" + escapeBackslashes(terms.get(0)) + "'";
        } else {
            StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < terms.size(); i++) {
                if (i > 0) sb.append(' ');
                sb.append('\'').append(escapeBackslashes(terms.get(i))).append('\'');
            }
            sb.append(')');
            termList = sb.toString();
        }
        String fullTextQuery = "select i, score(i) from " + langchainItemFullType + " i where i.text : " + termList;
        return remoteCache.<Object[]>query(fullTextQuery).maxResults(maxResults);
    }

    static List<String> splitTerms(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        for (String part : text.split("[\\s']+")) {
            String cleaned = part.replaceAll("[?*]", "");
            if (!cleaned.isEmpty()) {
                terms.add(cleaned);
            }
        }
        return terms;
    }

    private static String escapeBackslashes(String term) {
        return term.replace("\\", "\\\\");
    }

    private static List<Content> toContents(List<Object[]> hits) {
        return hits.stream()
                .map(hit -> {
                    LangChainInfinispanItem item = (LangChainInfinispanItem) hit[0];
                    Float score = (Float) hit[1];
                    return Content.from(
                            new TextSegment(item.text(), toMetadata(item)),
                            Map.of(
                                    ContentMetadata.SCORE,
                                    score.doubleValue(),
                                    ContentMetadata.EMBEDDING_ID,
                                    item.id()));
                })
                .toList();
    }

    private static Metadata toMetadata(LangChainInfinispanItem item) {
        Map<String, Object> map = new HashMap<>();
        if (item.metadata() != null) {
            for (LangChainMetadata metadata : item.metadata()) {
                map.put(metadata.name(), metadata.value());
            }
        }
        return new Metadata(map);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private InfinispanEmbeddingStore embeddingStore;
        private int maxResults = DEFAULT_MAX_RESULTS;

        public Builder embeddingStore(InfinispanEmbeddingStore embeddingStore) {
            this.embeddingStore = embeddingStore;
            return this;
        }

        public Builder maxResults(int maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public InfinispanContentRetriever build() {
            ensureNotNull(embeddingStore, "embeddingStore");
            return new InfinispanContentRetriever(embeddingStore, maxResults);
        }
    }
}
