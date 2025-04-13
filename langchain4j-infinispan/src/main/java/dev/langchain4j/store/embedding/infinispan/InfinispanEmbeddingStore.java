package dev.langchain4j.store.embedding.infinispan;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.api.query.Query;
import org.infinispan.commons.configuration.StringConfiguration;
import org.infinispan.commons.marshall.ProtoStreamMarshaller;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.schema.Schema;
import org.infinispan.query.remote.client.ProtobufMetadataManagerConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static dev.langchain4j.store.embedding.infinispan.InfinispanStoreConfiguration.DEFAULT_CACHE_CONFIG;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * Infinispan Embedding Store
 */
public class InfinispanEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(InfinispanEmbeddingStore.class);

    private final RemoteCache<String, LangChainInfinispanItem> remoteCache;
    private final InfinispanStoreConfiguration storeConfiguration;

    /**
     * Creates an Infinispan embedding store from a RemoteCacheManager
     * Assumes marshalling configuration is already provided by the RemoteCacheManager instance.
     *
     * @param remoteCacheManager, the already configured remote cache manager
     * @param storeConfiguration, the store configuration
     */
    public InfinispanEmbeddingStore(RemoteCacheManager remoteCacheManager,
                                    InfinispanStoreConfiguration storeConfiguration) {

        ensureNotNull(remoteCacheManager, "remoteCacheManager");
        ensureNotNull(storeConfiguration, "storeConfiguration");
        ensureNotNull(storeConfiguration.dimension(), "dimension");
        ensureNotBlank(storeConfiguration.cacheName(), "cacheName");

        this.storeConfiguration = storeConfiguration;

        if (storeConfiguration.createCache()) {
            this.remoteCache = remoteCacheManager.administration()
                  .getOrCreateCache(storeConfiguration.cacheName(), new StringConfiguration(computeCacheConfiguration(storeConfiguration)));
        } else {
            this.remoteCache = remoteCacheManager.getCache(storeConfiguration.cacheName());
        }
    }

    /**
     * Creates an instance of InfinispanEmbeddingStore
     */
    public InfinispanEmbeddingStore(ConfigurationBuilder builder,
                                    InfinispanStoreConfiguration storeConfiguration) {
        ensureNotNull(builder, "builder");
        ensureNotNull(storeConfiguration, "storeConfiguration");
        ensureNotBlank(storeConfiguration.cacheName(), "cacheName");
        ensureNotNull(storeConfiguration.dimension(), "dimension");
        this.storeConfiguration = storeConfiguration;
        Schema schema = LangchainSchemaCreator.buildSchema(storeConfiguration);

        if (storeConfiguration.createCache()) {
            String remoteCacheConfig = computeCacheConfiguration(storeConfiguration);
            builder.remoteCache(storeConfiguration.cacheName()).configuration(remoteCacheConfig);
        }

        // Registers the schema on the client
        ProtoStreamMarshaller marshaller = new ProtoStreamMarshaller();
        SerializationContext serializationContext = marshaller.getSerializationContext();
        String schemaContent =  schema.toString();
        FileDescriptorSource fileDescriptorSource = FileDescriptorSource.fromString(storeConfiguration.fileName(), schemaContent);
        serializationContext.registerProtoFiles(fileDescriptorSource);
        serializationContext.registerMarshaller(new LangChainItemMarshaller(storeConfiguration.langchainItemFullType()));
        serializationContext.registerMarshaller(new LangChainMetadataMarshaller(storeConfiguration.metadataFullType()));
        builder.marshaller(marshaller);

        // creates the client
        RemoteCacheManager rmc = new RemoteCacheManager(builder.build());

        // Uploads the schema to the server
        if (storeConfiguration.registerSchema()) {
            RemoteCache<String, String> metadataCache = rmc
                  .getCache(ProtobufMetadataManagerConstants.PROTOBUF_METADATA_CACHE_NAME);
            metadataCache.put(storeConfiguration.fileName(), schemaContent);
        }

        this.remoteCache = rmc.getCache(storeConfiguration.cacheName());
    }

    /**
     * Gets the underlying Infinispan remote cache
     *
     * @return RemoteCache
     */
    public RemoteCache<String, LangChainInfinispanItem> getRemoteCache() {
        return remoteCache;
    }

    private String computeCacheConfiguration(InfinispanStoreConfiguration storeConfiguration) {
        String remoteCacheConfig = storeConfiguration.cacheConfig();
        if (remoteCacheConfig == null) {
            remoteCacheConfig = DEFAULT_CACHE_CONFIG.replace("CACHE_NAME", storeConfiguration.cacheName())
                  .replace("LANGCHAINITEM", storeConfiguration.langchainItemFullType())
                  .replace("LANGCHAIN_METADATA", storeConfiguration.metadataFullType());
        }
        return remoteCacheConfig;
    }

    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .collect(toList());
        addAll(ids, embeddings, null);
        return ids;
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        Query<Object[]> query = remoteCache.query("select i, score(i) from " +
            storeConfiguration.langchainItemFullType() + " i where i.embedding <-> " +
            Arrays.toString(request.queryEmbedding().vector()) +
            "~" + storeConfiguration.distance());

        List<Object[]> hits = query.maxResults(request.maxResults()).list();

        List<EmbeddingMatch<TextSegment>> matches = hits.stream().map(obj -> {
            LangChainInfinispanItem item = (LangChainInfinispanItem) obj[0];
            Float score = (Float) obj[1];
            if (score.doubleValue() < request.minScore()) {
                return null;
            }
            TextSegment embedded = null;
            if (item.text() != null) {
                Map<String, String> map = new HashMap<>();
                for (LangChainMetadata metadata : item.metadata()) {
                    map.put(metadata.name(), metadata.value());
                }
                embedded = new TextSegment(item.text(), new Metadata(map));
            }
            Embedding embedding = new Embedding(item.embedding());
            return new EmbeddingMatch<>(score.doubleValue(), item.id(), embedding, embedded);
        }).filter(Objects::nonNull).collect(toList());

        return new EmbeddingSearchResult<>(matches);
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAll(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("do not add empty embeddings to infinispan");
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(embedded == null || embeddings.size() == embedded.size(), "embeddings size is not equal to embedded size");

        int size = ids.size();
        Map<String, LangChainInfinispanItem> elements = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            String id = ids.get(i);
            Embedding embedding = embeddings.get(i);
            TextSegment textSegment = embedded == null ? null : embedded.get(i);
            if (textSegment != null) {
                Set<LangChainMetadata> metadata = textSegment.metadata().toMap().entrySet().stream()
                      .map(e -> new LangChainMetadata(e.getKey(), Objects.toString(e.getValue(), null)))
                      .collect(Collectors.toSet());
                elements.put(id, new LangChainInfinispanItem(id, embedding.vector(), textSegment.text(), metadata));
            } else {
                elements.put(id, new LangChainInfinispanItem(id, embedding.vector(), null, null));
            }
        }
        // blocking call
        remoteCache.putAll(elements);
    }

    public static Builder builder() {
        return new Builder();
    }

    public RemoteCache<String, LangChainInfinispanItem> remoteCache() {
        return remoteCache;
    }

    public void clearCache() {
        remoteCache.clear();
    }

    public static class Builder {
        private ConfigurationBuilder configurationBuilder;
        private String cacheName;
        private Integer dimension;
        private Integer distance;
        private String similarity;
        private String cacheConfig;
        private String packageName;
        private String fileName;
        private String langchainItemName;
        private String metadataItemName;
        private boolean registerSchema = true;
        private boolean createCache = true;

        /**
         * Infinispan cache name to be used, will be created on first access
         */
        public Builder cacheName(String name) {
            this.cacheName = name;
            return this;
        }

        /**
         * Infinispan cache config to be used, will be created on first access
         */
        public Builder cacheConfig(String cacheConfig) {
            this.cacheConfig = cacheConfig;
            return this;
        }

        /**
         * Infinispan vector dimension
         */
        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        /**
         * Infinispan distance for knn query
         */
        public Builder distance(Integer distance) {
            this.distance = distance;
            return this;
        }

        /**
         * Infinispan similarity for the embedding definition
         */
        public Builder similarity(String similarity) {
            this.similarity = similarity;
            return this;
        }

        /**
         * Infinispan schema package name
         */
        public Builder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        /**
         * Infinispan schema file name
         */
        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        /**
         * Infinispan schema langchainItemName
         */
        public Builder langchainItemName(String langchainItemName) {
            this.langchainItemName = langchainItemName;
            return this;
        }

        /**
         * Infinispan schema metadataItemName
         */
        public Builder metadataItemName(String metadataItemName) {
            this.metadataItemName = metadataItemName;
            return this;
        }

        /**
         * Register Langchain schema in the server
         */
        public Builder registerSchema(boolean registerSchema) {
            this.registerSchema = registerSchema;
            return this;
        }

        /**
         * Create cache in the server
         */
        public Builder createCache(boolean createCache) {
            this.createCache = createCache;
            return this;
        }

        /**
         * Infinispan Configuration Builder
         */
        public Builder infinispanConfigBuilder(ConfigurationBuilder builder) {
            this.configurationBuilder = builder;
            return this;
        }

        /**
         * Builds the store
         *
         * @return InfinispanEmbeddingStore
         */
        public InfinispanEmbeddingStore build() {
            return new InfinispanEmbeddingStore(configurationBuilder,
                  new InfinispanStoreConfiguration(cacheName, dimension, distance, similarity, cacheConfig, packageName, fileName,
                        langchainItemName, metadataItemName, createCache, registerSchema));
        }
    }
}
