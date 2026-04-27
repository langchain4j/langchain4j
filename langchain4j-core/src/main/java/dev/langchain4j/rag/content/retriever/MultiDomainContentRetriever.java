package dev.langchain4j.rag.content.retriever;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * A {@link ContentRetriever} that retrieves from multiple domains within a single {@link EmbeddingStore}
 * in parallel, merges the results, and re-ranks them using similarity score multiplied by per-domain boost.
 * <br>
 * This enables multi-domain RAG patterns where documents from different namespaces/metadata domains
 * (e.g., fitness, e-commerce, healthcare) are retrieved with separate result limits and relevance boosts,
 * producing a single merged and re-ranked result set.
 * <br>
 * <br>
 * <b>Example usage:</b>
 * <pre>{@code
 * ContentRetriever retriever = MultiDomainContentRetriever.builder()
 *     .embeddingStore(embeddingStore)
 *     .embeddingModel(embeddingModel)
 *     .domains(
 *         Domain.builder().name("activity").filter(metadataKey("domain").isEqualTo("activity")).maxResults(5).boost(1.2).build(),
 *         Domain.builder().name("nutrition").filter(metadataKey("domain").isEqualTo("nutrition")).maxResults(3).boost(1.0).build(),
 *         Domain.builder().name("user").filter(metadataKey("domain").isEqualTo("user")).maxResults(1).boost(1.5).build()
 *     )
 *     .build();
 *
 * List<Content> results = retriever.retrieve(query);
 * }</pre>
 *
 * @see Domain
 * @see MultiDomainRetrievalRequest
 * @see EmbeddingStoreContentRetriever
 */
public class MultiDomainContentRetriever implements ContentRetriever {

    private static final int DEFAULT_MAX_RESULTS = 3;

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final List<Domain> domains;

    private MultiDomainContentRetriever(MultiDomainContentRetrieverBuilder builder) {
        this.embeddingStore = ensureNotNull(builder.embeddingStore, "embeddingStore");
        this.embeddingModel = ensureNotNull(builder.embeddingModel, "embeddingModel");
        this.domains = ensureNotEmpty(builder.domains, "domains");
    }

    public static MultiDomainContentRetrieverBuilder builder() {
        return new MultiDomainContentRetrieverBuilder();
    }

    @Override
    public List<Content> retrieve(Query query) {

        Embedding embeddedQuery = embeddingModel.embed(query.text()).content();

        // Fan out searches to all domains in parallel using virtual threads
        ExecutorService executor = DefaultExecutorProvider.getDefaultExecutorService();
        List<CompletableFuture<List<Content>>> futures = domains.stream()
                .map(domain -> CompletableFuture.supplyAsync(
                        () -> searchDomain(domain, embeddedQuery, query.text()), executor))
                .collect(Collectors.toList());

        List<Content> mergedResults = futures.stream()
                .<Content>flatMap(f -> f.join().stream())
                .sorted(Comparator.comparingDouble(
                                (Content c) -> (Double) c.metadata().getOrDefault(ContentMetadata.SCORE, 0.0))
                        .reversed())
                .collect(Collectors.toList());

        return mergedResults;
    }

    private List<Content> searchDomain(Domain domain, Embedding embeddedQuery, String queryText) {
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .query(queryText)
                .queryEmbedding(embeddedQuery)
                .maxResults(domain.maxResults())
                .filter(domain.filter())
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

        double boost = domain.boost();
        return searchResult.matches().stream()
                .map(embeddingMatch -> {
                    double boostedScore = embeddingMatch.score() * boost;
                    return Content.from(
                            embeddingMatch.embedded(),
                            Map.of(
                                    ContentMetadata.SCORE,
                                    boostedScore,
                                    ContentMetadata.ORIGINAL_SCORE,
                                    embeddingMatch.score(),
                                    ContentMetadata.EMBEDDING_ID,
                                    embeddingMatch.embeddingId() != null ? embeddingMatch.embeddingId() : "",
                                    ContentMetadata.DOMAIN_NAME,
                                    domain.name()));
                })
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "MultiDomainContentRetriever{" + "domains=" + domains + '}';
    }

    /**
     * Represents a single domain within an {@link EmbeddingStore} to search.
     * <br>
     * Each domain has:
     * <ul>
     *   <li>{@code name}: a unique identifier for the domain</li>
     *   <li>{@code filter}: the {@link Filter} to apply (e.g., metadata domain equals "activity")</li>
     *   <li>{@code maxResults}: the maximum number of results to retrieve from this domain</li>
     *   <li>{@code boost}: a multiplier applied to similarity scores (default 1.0)</li>
     * </ul>
     *
     * @see MultiDomainContentRetriever
     * @see MultiDomainRetrievalRequest
     */
    public static class Domain {

        private final String name;
        private final Filter filter;
        private final int maxResults;
        private final double boost;

        private Domain(DomainBuilder builder) {
            this.name = ensureNotNull(builder.name, "name");
            this.filter = builder.filter; // may be null
            this.maxResults = builder.maxResults != null ? builder.maxResults : DEFAULT_MAX_RESULTS;
            this.boost = builder.boost != null ? builder.boost : 1.0;
        }

        public String name() {
            return name;
        }

        public Filter filter() {
            return filter;
        }

        public int maxResults() {
            return maxResults;
        }

        public double boost() {
            return boost;
        }

        public static DomainBuilder builder() {
            return new DomainBuilder();
        }

        @Override
        public String toString() {
            return "Domain{" + "name='" + name + '\'' + ", maxResults=" + maxResults + ", boost=" + boost + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof final Domain other)) return false;
            return name.equals(other.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    public static class DomainBuilder {

        private String name;
        private Filter filter;
        private Integer maxResults;
        private Double boost;

        DomainBuilder() {}

        public DomainBuilder name(String name) {
            this.name = name;
            return this;
        }

        public DomainBuilder filter(Filter filter) {
            this.filter = filter;
            return this;
        }

        public DomainBuilder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public DomainBuilder boost(Double boost) {
            this.boost = boost;
            return this;
        }

        public Domain build() {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name");
            }
            if (maxResults != null) {
                ensureBetween(maxResults, 1, Integer.MAX_VALUE, "maxResults");
            }
            if (boost != null) {
                ensureBetween(boost, 0.0, Double.MAX_VALUE, "boost");
            }
            return new Domain(this);
        }
    }

    /**
     * A request for multi-domain retrieval. This is an optional intermediate object
     * that can be used to configure domains before passing to {@link MultiDomainContentRetriever}.
     *
     * @see MultiDomainContentRetriever
     * @see Domain
     */
    public static class MultiDomainRetrievalRequest {

        private final List<Domain> domains;

        private MultiDomainRetrievalRequest(List<Domain> domains) {
            this.domains = domains;
        }

        public List<Domain> domains() {
            return domains;
        }

        public static MultiDomainRetrievalRequestBuilder builder() {
            return new MultiDomainRetrievalRequestBuilder();
        }

        public static class MultiDomainRetrievalRequestBuilder {

            private final java.util.ArrayList<Domain> domains = new java.util.ArrayList<>();

            MultiDomainRetrievalRequestBuilder() {}

            /**
             * Add a domain to this request.
             *
             * @param domain the {@link Domain} to add
             * @return this builder
             */
            public MultiDomainRetrievalRequestBuilder domain(Domain domain) {
                this.domains.add(domain);
                return this;
            }

            /**
             * Add multiple domains to this request.
             *
             * @param domains the domains to add
             * @return this builder
             */
            public MultiDomainRetrievalRequestBuilder domains(Domain... domains) {
                for (Domain domain : domains) {
                    this.domains.add(domain);
                }
                return this;
            }

            /**
             * Add multiple domains to this request.
             *
             * @param domains the collection of domains to add
             * @return this builder
             */
            public MultiDomainRetrievalRequestBuilder domains(Collection<Domain> domains) {
                this.domains.addAll(domains);
                return this;
            }

            public MultiDomainRetrievalRequest build() {
                ensureNotEmpty(domains, "domains");
                return new MultiDomainRetrievalRequest(new java.util.ArrayList<>(domains));
            }
        }
    }

    public static class MultiDomainContentRetrieverBuilder {

        private EmbeddingStore<TextSegment> embeddingStore;
        private EmbeddingModel embeddingModel;
        private List<Domain> domains;

        MultiDomainContentRetrieverBuilder() {}

        public MultiDomainContentRetrieverBuilder embeddingStore(EmbeddingStore<TextSegment> embeddingStore) {
            this.embeddingStore = embeddingStore;
            return this;
        }

        public MultiDomainContentRetrieverBuilder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        /**
         * Set the list of domains to search.
         *
         * @param domains the domains
         * @return this builder
         */
        public MultiDomainContentRetrieverBuilder domains(List<Domain> domains) {
            this.domains = domains;
            return this;
        }

        /**
         * Set the domains to search (varargs).
         *
         * @param domains the domains
         * @return this builder
         */
        public MultiDomainContentRetrieverBuilder domains(Domain... domains) {
            this.domains = java.util.Arrays.asList(domains);
            return this;
        }

        public MultiDomainContentRetriever build() {
            if (embeddingStore == null) {
                throw new IllegalArgumentException("embeddingStore");
            }
            if (embeddingModel == null) {
                throw new IllegalArgumentException("embeddingModel");
            }
            if (domains == null || domains.isEmpty()) {
                throw new IllegalArgumentException("domains");
            }
            return new MultiDomainContentRetriever(this);
        }
    }
}
