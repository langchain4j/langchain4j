package dev.langchain4j.rag.content.retriever.infinispan;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.infinispan.InfinispanStoreConfiguration;
import dev.langchain4j.store.embedding.infinispan.LangChainInfinispanItem;
import dev.langchain4j.store.embedding.infinispan.LangChainItemMarshaller;
import dev.langchain4j.store.embedding.infinispan.LangChainMetadata;
import dev.langchain4j.store.embedding.infinispan.LangChainMetadataMarshaller;
import dev.langchain4j.store.embedding.infinispan.LangchainSchemaCreator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.configuration.StringConfiguration;
import org.infinispan.commons.marshall.ProtoStreamMarshaller;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.schema.Schema;

/**
 * An Infinispan-backed {@link ContentRetriever} that uses full-text search (Ickle queries)
 * to retrieve relevant content without requiring embeddings.
 * <p>
 * This retriever requires that the Infinispan cache schema has full-text indexing enabled
 * on the text field. The builder defaults to {@code @Text(projectable=true, analyzer="standard")};
 * use {@link Builder#textFieldAnnotation(String)} to override with a different analyzer.
 */
public class InfinispanContentRetriever implements ContentRetriever {

    private static final int DEFAULT_MAX_RESULTS = 3;

    private final RemoteCache<String, LangChainInfinispanItem> remoteCache;
    private final InfinispanStoreConfiguration storeConfiguration;
    private final int maxResults;

    public InfinispanContentRetriever(
            RemoteCacheManager remoteCacheManager, InfinispanStoreConfiguration storeConfiguration, int maxResults) {
        ensureNotNull(remoteCacheManager, "remoteCacheManager");
        ensureNotNull(storeConfiguration, "storeConfiguration");
        ensureNotNull(storeConfiguration.dimension(), "dimension");
        ensureNotBlank(storeConfiguration.cacheName(), "cacheName");

        this.storeConfiguration = storeConfiguration;
        this.maxResults = maxResults;

        if (storeConfiguration.createCache()) {
            String remoteCacheConfig = storeConfiguration.cacheConfig();
            if (remoteCacheConfig == null) {
                remoteCacheConfig = InfinispanStoreConfiguration.DEFAULT_CACHE_CONFIG
                        .replace("CACHE_NAME", storeConfiguration.cacheName())
                        .replace("LANGCHAINITEM", storeConfiguration.langchainItemFullType())
                        .replace("LANGCHAIN_METADATA", storeConfiguration.metadataFullType());
            }
            this.remoteCache = remoteCacheManager
                    .administration()
                    .getOrCreateCache(storeConfiguration.cacheName(), new StringConfiguration(remoteCacheConfig));
        } else {
            this.remoteCache = remoteCacheManager.getCache(storeConfiguration.cacheName());
        }
    }

    public InfinispanContentRetriever(
            ConfigurationBuilder builder, InfinispanStoreConfiguration storeConfiguration, int maxResults) {
        ensureNotNull(builder, "builder");
        ensureNotNull(storeConfiguration, "storeConfiguration");
        ensureNotBlank(storeConfiguration.cacheName(), "cacheName");
        ensureNotNull(storeConfiguration.dimension(), "dimension");

        this.storeConfiguration = storeConfiguration;
        this.maxResults = maxResults;
        Schema schema = LangchainSchemaCreator.buildSchema(storeConfiguration);

        if (storeConfiguration.createCache()) {
            String remoteCacheConfig = storeConfiguration.cacheConfig();
            if (remoteCacheConfig == null) {
                remoteCacheConfig = InfinispanStoreConfiguration.DEFAULT_CACHE_CONFIG
                        .replace("CACHE_NAME", storeConfiguration.cacheName())
                        .replace("LANGCHAINITEM", storeConfiguration.langchainItemFullType())
                        .replace("LANGCHAIN_METADATA", storeConfiguration.metadataFullType());
            }
            builder.remoteCache(storeConfiguration.cacheName()).configuration(remoteCacheConfig);
        }

        ProtoStreamMarshaller marshaller = new ProtoStreamMarshaller();
        SerializationContext serializationContext = marshaller.getSerializationContext();
        String schemaContent = schema.toString();
        FileDescriptorSource fileDescriptorSource =
                FileDescriptorSource.fromString(storeConfiguration.fileName(), schemaContent);
        serializationContext.registerProtoFiles(fileDescriptorSource);
        serializationContext.registerMarshaller(
                new LangChainItemMarshaller(storeConfiguration.langchainItemFullType()));
        serializationContext.registerMarshaller(new LangChainMetadataMarshaller(storeConfiguration.metadataFullType()));
        builder.marshaller(marshaller);

        RemoteCacheManager rmc = new RemoteCacheManager(builder.build());

        if (storeConfiguration.registerSchema()) {
            rmc.administration().schemas().createOrUpdate(schema);
        }

        this.remoteCache = rmc.getCache(storeConfiguration.cacheName());
    }

    public RemoteCache<String, LangChainInfinispanItem> remoteCache() {
        return remoteCache;
    }

    @Override
    public List<Content> retrieve(Query query) {
        String fullTextQuery = "FROM " + storeConfiguration.langchainItemFullType() + " i WHERE i.text : '"
                + escapeQuery(query.text()) + "'";

        org.infinispan.commons.api.query.Query<LangChainInfinispanItem> ickleQuery = remoteCache.query(fullTextQuery);
        List<LangChainInfinispanItem> hits = ickleQuery.maxResults(maxResults).list();

        return hits.stream().map(this::toContent).toList();
    }

    @Override
    public CompletableFuture<List<Content>> retrieveAsync(Query query) {
        String fullTextQuery = "FROM " + storeConfiguration.langchainItemFullType() + " i WHERE i.text : '"
                + escapeQuery(query.text()) + "'";

        org.infinispan.commons.api.query.Query<LangChainInfinispanItem> ickleQuery = remoteCache.query(fullTextQuery);
        ickleQuery.maxResults(maxResults);

        return ickleQuery
                .executeAsync()
                .thenApply(result -> result.list().stream().map(this::toContent).toList())
                .toCompletableFuture();
    }

    private Content toContent(LangChainInfinispanItem item) {
        if (item.text() == null) {
            return Content.from("");
        }
        Map<String, Object> map = new HashMap<>();
        if (item.metadata() != null) {
            for (LangChainMetadata metadata : item.metadata()) {
                map.put(metadata.name(), metadata.value());
            }
        }
        return Content.from(new TextSegment(item.text(), new Metadata(map)));
    }

    private static String escapeQuery(String text) {
        return text.replace("'", "\\'");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ConfigurationBuilder configurationBuilder;
        private RemoteCacheManager remoteCacheManager;
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
        private int maxResults = DEFAULT_MAX_RESULTS;
        private String textFieldAnnotation;

        public Builder cacheName(String name) {
            this.cacheName = name;
            return this;
        }

        public Builder cacheConfig(String cacheConfig) {
            this.cacheConfig = cacheConfig;
            return this;
        }

        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        public Builder distance(Integer distance) {
            this.distance = distance;
            return this;
        }

        public Builder similarity(String similarity) {
            this.similarity = similarity;
            return this;
        }

        public Builder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder langchainItemName(String langchainItemName) {
            this.langchainItemName = langchainItemName;
            return this;
        }

        public Builder metadataItemName(String metadataItemName) {
            this.metadataItemName = metadataItemName;
            return this;
        }

        public Builder registerSchema(boolean registerSchema) {
            this.registerSchema = registerSchema;
            return this;
        }

        public Builder createCache(boolean createCache) {
            this.createCache = createCache;
            return this;
        }

        public Builder maxResults(int maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        /**
         * Protobuf annotation for the text field. Defaults to
         * {@code @Text(projectable=true, analyzer="standard")} which enables full-text search.
         * Override to use a different analyzer or annotation.
         */
        public Builder textFieldAnnotation(String textFieldAnnotation) {
            this.textFieldAnnotation = textFieldAnnotation;
            return this;
        }

        public Builder infinispanConfigBuilder(ConfigurationBuilder builder) {
            this.configurationBuilder = builder;
            return this;
        }

        public Builder remoteCacheManager(RemoteCacheManager remoteCacheManager) {
            this.remoteCacheManager = remoteCacheManager;
            return this;
        }

        public InfinispanContentRetriever build() {
            InfinispanStoreConfiguration config = new InfinispanStoreConfiguration(
                    cacheName,
                    dimension,
                    distance,
                    similarity,
                    cacheConfig,
                    packageName,
                    fileName,
                    langchainItemName,
                    metadataItemName,
                    createCache,
                    registerSchema,
                    textFieldAnnotation != null
                            ? textFieldAnnotation
                            : InfinispanStoreConfiguration.TEXT_FIELD_ANNOTATION_FULLTEXT);

            if (remoteCacheManager != null) {
                return new InfinispanContentRetriever(remoteCacheManager, config, maxResults);
            }
            ensureNotNull(configurationBuilder, "configurationBuilder or remoteCacheManager");
            return new InfinispanContentRetriever(configurationBuilder, config, maxResults);
        }
    }
}
