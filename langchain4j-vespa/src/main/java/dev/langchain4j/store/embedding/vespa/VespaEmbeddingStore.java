package dev.langchain4j.store.embedding.vespa;

import static dev.langchain4j.internal.Utils.generateUUIDFrom;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.store.embedding.vespa.Record.Fields.Vector;
import static dev.langchain4j.store.embedding.vespa.VespaClient.createInstance;

import ai.vespa.client.dsl.A;
import ai.vespa.client.dsl.NearestNeighbor;
import ai.vespa.client.dsl.Q;
import ai.vespa.feed.client.DocumentId;
import ai.vespa.feed.client.FeedClientBuilder;
import ai.vespa.feed.client.FeedException;
import ai.vespa.feed.client.JsonFeeder;
import ai.vespa.feed.client.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.vespa.Record.Fields;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Represents the <a href="https://vespa.ai/">Vespa</a> - search engine and vector database.
 * Does not support storing {@link dev.langchain4j.data.document.Metadata} yet.
 * Example server configuration contains cosine similarity search rank profile, of course other Vespa neighbor search
 * methods are supported too. Read more <a href="https://docs.vespa.ai/en/nearest-neighbor-search.html">here</a>.
 */
public class VespaEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    static final String DEFAULT_NAMESPACE = "namespace";
    static final String DEFAULT_DOCUMENT_TYPE = "langchain4j";
    private static final String DEFAULT_CLUSTER_NAME = "langchain4j";
    private static final boolean DEFAULT_AVOID_DUPS = true;
    private static final String FIELD_NAME_TEXT_SEGMENT = "text_segment";
    private static final String FIELD_NAME_VECTOR = "vector";
    private static final String FIELD_NAME_DOCUMENT_ID = "documentid";
    private static final String DEFAULT_RANK_PROFILE = "langchain4j_relevance_score";
    private static final int DEFAULT_TARGET_HITS = 10;

    private final String url;
    private final Path keyPath;
    private final Path certPath;
    private final Duration timeout;
    private final String namespace;
    private final String documentType;
    private final String clusterName;
    private final String rankProfile;
    private final int targetHits;
    private final boolean avoidDups;
    private final boolean logRequests;
    private final boolean logResponses;

    private VespaApi api;

    /**
     * Creates a new VespaEmbeddingStore instance.
     *
     * @param url          server url, local or cloud one. The latter you can find under Endpoint of your Vespa
     *                     application, e.g. https://alexey-heezer.langchain4j.mytenant346.aws-us-east-1c.dev.z.vespa-app.cloud/
     * @param keyPath      local path to the SSL private key file in PEM format. Read
     *                     <a href="https://cloud.vespa.ai/en/getting-started-java">docs</a> for details.
     *                     Null if there is no SSL private key file e.g. for local Vespa server.
     * @param certPath     local path to the SSL certificate file in PEM format. Read
     *                     <a href="https://cloud.vespa.ai/en/getting-started-java">docs</a> for details.
     *                     Null if there is no SSL certificate file e.g. for local Vespa server.
     * @param timeout      for Vespa Java client in <code>java.time.Duration</code> format.
     * @param namespace    required for document ID generation, find more details
     *                     <a href="https://docs.vespa.ai/en/documents.html#namespace">here</a>.
     * @param documentType document type, used for document ID generation, find more details
     *                     <a href="https://docs.vespa.ai/en/documents.html#namespace">here</a> and data querying
     * @param clusterName  cluster name, used for deleting all documents, find more details
     *                     <a href="https://docs.vespa.ai/en/operations/batch-delete.html">here</a>
     * @param rankProfile  rank profile from your .sd schema. Provided example schema configures cosine similarity match
     * @param targetHits   sets the number of hits (10 is default) exposed to the real Vespa's first-phase ranking
     *                     function per content node, find more details
     *                     <a href="https://docs.vespa.ai/en/nearest-neighbor-search.html#querying-using-nearestneighbor-query-operator">here</a>.
     * @param avoidDups    if true (default), then <code>VespaEmbeddingStore</code> will generate a hashed ID based on
     *                     provided text segment, which avoids duplicated entries in DB.
     *                     If false, then random ID will be generated.
     * @param logRequests  If true, requests to the Vespa service are logged.
     * @param logResponses If true, responses from the Vespa service are logged.
     */
    public VespaEmbeddingStore(
            String url,
            String keyPath,
            String certPath,
            Duration timeout,
            String namespace,
            String documentType,
            String clusterName,
            String rankProfile,
            Integer targetHits,
            Boolean avoidDups,
            Boolean logRequests,
            Boolean logResponses) {
        ensureNotNull(url, "url");

        this.url = url;
        this.keyPath = keyPath != null ? Paths.get(keyPath) : null;
        this.certPath = certPath != null ? Paths.get(certPath) : null;
        this.timeout = getOrDefault(timeout, DEFAULT_TIMEOUT);
        this.namespace = getOrDefault(namespace, DEFAULT_NAMESPACE);
        this.documentType = getOrDefault(documentType, DEFAULT_DOCUMENT_TYPE);
        this.clusterName = getOrDefault(clusterName, DEFAULT_CLUSTER_NAME);
        this.rankProfile = getOrDefault(rankProfile, DEFAULT_RANK_PROFILE);
        this.targetHits = getOrDefault(targetHits, DEFAULT_TARGET_HITS);
        this.avoidDups = getOrDefault(avoidDups, DEFAULT_AVOID_DUPS);
        this.logRequests = getOrDefault(logRequests, false);
        this.logResponses = getOrDefault(logResponses, false);
    }

    private static EmbeddingMatch<TextSegment> toEmbeddingMatch(Record in) {
        return new EmbeddingMatch<>(
                in.relevance(),
                DocumentId.of(in.fields().documentid()).userSpecific(),
                Embedding.from(in.fields().vector().values()),
                in.fields().textSegment() != null ? TextSegment.from(in.fields().textSegment()) : null);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String add(Embedding embedding) {
        return add(null, embedding, null);
    }

    /**
     * Adds a new embedding with provided ID to the store.
     *
     * @param id        "user-specified" part of document ID, find more details
     *                  <a href="https://docs.vespa.ai/en/documents.html#namespace">here</a>
     * @param embedding the embedding to add
     */
    @Override
    public void add(String id, Embedding embedding) {
        add(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        return add(null, embedding, textSegment);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        return addAll(embeddings, null);
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (embedded != null && embeddings.size() != embedded.size()) {
            throw new IllegalArgumentException("The list of embeddings and embedded must have the same size");
        }

        try (JsonFeeder jsonFeeder = feeder()) {
            List<Record> records = new ArrayList<>();

            for (int i = 0; i < embeddings.size(); i++) {
                records.add(buildRecord(ids.get(i), embeddings.get(i), embedded != null ? embedded.get(i) : null));
            }

            jsonFeeder.feedMany(
                    new ByteArrayInputStream(
                            OBJECT_MAPPER.writeValueAsString(records).getBytes()),
                    new JsonFeeder.ResultCallback() {
                        @Override
                        public void onNextResult(Result result, FeedException error) {
                            if (error != null) {
                                throw new RuntimeException(error.getMessage());
                            }
                        }

                        @Override
                        public void onError(FeedException error) {
                            throw new RuntimeException(error.getMessage());
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     * The score inside {@link EmbeddingMatch} is Vespa relevance according to provided rank profile.
     */
    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        try {
            String searchQuery = Q.select(FIELD_NAME_DOCUMENT_ID, FIELD_NAME_TEXT_SEGMENT, FIELD_NAME_VECTOR)
                    .from(documentType)
                    .where(buildNearestNeighbor())
                    .fix()
                    .hits(request.maxResults())
                    .ranking(rankProfile)
                    .param(
                            "input.query(q)",
                            OBJECT_MAPPER.writeValueAsString(
                                    request.queryEmbedding().vectorAsList()))
                    .param("input.query(threshold)", String.valueOf(request.minScore()))
                    .build();

            Response<QueryResponse> response = api().search(searchQuery).execute();
            if (response.isSuccessful()) {
                QueryResponse parsedResponse = response.body();
                List<Record> children = parsedResponse.root().children();
                return new EmbeddingSearchResult<>(
                        isNullOrEmpty(children)
                                ? new ArrayList<>()
                                : children.stream()
                                        .map(VespaEmbeddingStore::toEmbeddingMatch)
                                        .toList());
            } else {
                throw toException(response);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeAll() {
        try {
            Response<DeleteResponse> response =
                    api().deleteAll(namespace, documentType, clusterName).execute();
            if (!response.isSuccessful()) {
                throw toException(response);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String add(String id, Embedding embedding, TextSegment textSegment) {
        AtomicReference<String> resId = new AtomicReference<>();

        try (JsonFeeder jsonFeeder = feeder()) {
            jsonFeeder
                    .feedSingle(OBJECT_MAPPER.writeValueAsString(buildRecord(id, embedding, textSegment)))
                    .whenComplete(((result, throwable) -> {
                        if (throwable != null) {
                            throw new RuntimeException(throwable);
                        } else if (Result.Type.success.equals(result.type())) {
                            resId.set(result.documentId().userSpecific());
                        }
                    }));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return resId.get();
    }

    private JsonFeeder feeder() {
        FeedClientBuilder fcBuilder = FeedClientBuilder.create(URI.create(url));
        if (certPath != null && keyPath != null) {
            fcBuilder.setCertificate(certPath, keyPath);
        }

        return JsonFeeder.builder(fcBuilder.build()).withTimeout(timeout).build();
    }

    private VespaApi api() {
        if (api == null) {
            api = createInstance(url, certPath, keyPath, logRequests, logResponses);
        }
        return api;
    }

    private Record buildRecord(String id, Embedding embedding, TextSegment textSegment) {
        String recordId = id != null
                ? id
                : avoidDups && textSegment != null ? generateUUIDFrom(textSegment.text()) : randomUUID();
        DocumentId documentId = DocumentId.of(namespace, documentType, recordId);
        String text = textSegment != null ? textSegment.text() : null;
        return new Record(documentId.toString(), null, new Fields(null, text, new Vector(embedding.vectorAsList())));
    }

    private NearestNeighbor buildNearestNeighbor() {
        NearestNeighbor nb = Q.nearestNeighbor(FIELD_NAME_VECTOR, "q");
        nb.annotate(A.a("targetHits", targetHits));
        return nb;
    }

    public static class Builder {

        private String url;
        private String keyPath;
        private String certPath;
        private Duration timeout;
        private String namespace;
        private String documentType;
        private String clusterName;
        private String rankProfile;
        private Integer targetHits;
        private Boolean avoidDups;
        private Boolean logRequests;
        private Boolean logResponses;

        /**
         * @param url server url, local or cloud one. The latter you can find under Endpoint of your Vespa
         *            application, e.g. https://alexey-heezer.langchain4j.mytenant346.aws-us-east-1c.dev.z.vespa-app.cloud/
         */
        public Builder url(String url) {
            this.url = url;
            return this;
        }

        /**
         * @param keyPath local path to the SSL private key file in PEM format. Read
         *                <a href="https://cloud.vespa.ai/en/getting-started-java">docs</a> for details.
         */
        public Builder keyPath(String keyPath) {
            this.keyPath = keyPath;
            return this;
        }

        /**
         * @param certPath local path to the SSL certificate file in PEM format. Read
         *                 <a href="https://cloud.vespa.ai/en/getting-started-java">docs</a> for details.
         */
        public Builder certPath(String certPath) {
            this.certPath = certPath;
            return this;
        }

        /**
         * @param timeout for Vespa Java client in <code>java.time.Duration</code> format.
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * @param namespace required for document ID generation, find more details
         *                  <a href="https://docs.vespa.ai/en/documents.html#namespace">here</a>.
         */
        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        /**
         * @param documentType document type, used for document ID generation, find more details
         *                     <a href="https://docs.vespa.ai/en/documents.html#namespace">here</a> and data querying.
         */
        public Builder documentType(String documentType) {
            this.documentType = documentType;
            return this;
        }

        /**
         * @param clusterName cluster name, used for deleting all documents, find more details
         *                    <a href="https://docs.vespa.ai/en/operations/batch-delete.html">here</a>
         */
        public Builder clusterName(String clusterName) {
            this.clusterName = clusterName;
            return this;
        }

        /**
         * @param rankProfile rank profile from your .sd schema. Provided example schema configures cosine similarity match.
         */
        public Builder rankProfile(String rankProfile) {
            this.rankProfile = rankProfile;
            return this;
        }

        /**
         * @param targetHits sets the number of hits (10 is default) exposed to the real Vespa's first-phase ranking
         *                   function per content node, find more details
         *                   <a href="https://docs.vespa.ai/en/nearest-neighbor-search.html#querying-using-nearestneighbor-query-operator">here</a>.
         */
        public Builder targetHits(Integer targetHits) {
            this.targetHits = targetHits;
            return this;
        }

        public Builder avoidDups(Boolean avoidDups) {
            this.avoidDups = avoidDups;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public VespaEmbeddingStore build() {
            return new VespaEmbeddingStore(
                    url,
                    keyPath,
                    certPath,
                    timeout,
                    namespace,
                    documentType,
                    clusterName,
                    rankProfile,
                    targetHits,
                    avoidDups,
                    logRequests,
                    logResponses);
        }
    }

    private static RuntimeException toException(Response<?> response) throws IOException {
        try (ResponseBody responseBody = response.errorBody()) {
            int code = response.code();
            if (responseBody != null) {
                String body = responseBody.string();
                String errorMessage = String.format("status code: %s; body: %s", code, body);
                return new RuntimeException(errorMessage);
            } else {
                return new RuntimeException(String.format("status code: %s;", code));
            }
        }
    }
}
