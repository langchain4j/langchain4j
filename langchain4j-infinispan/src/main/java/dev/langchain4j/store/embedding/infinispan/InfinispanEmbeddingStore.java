package dev.langchain4j.store.embedding.infinispan;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.api.query.Query;
import org.infinispan.commons.marshall.ProtoStreamMarshaller;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.schema.Schema;
import org.infinispan.protostream.schema.Type;
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
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * Infinispan Embedding Store
 */
public class InfinispanEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(InfinispanEmbeddingStore.class);

    private final RemoteCache<String, LangChainInfinispanItem> remoteCache;

    private final LangChainItemMarshaller itemMarshaller;
    private final LangChainMetadataMarshaller metadataMarshaller;

    private static final String DEFAULT_CACHE_CONFIG =
            "<distributed-cache name=\"CACHE_NAME\">\n"
                    + "<indexing storage=\"local-heap\">\n"
                    + "<indexed-entities>\n"
                    + "<indexed-entity>LANGCHAINITEM</indexed-entity>\n"
                    + "<indexed-entity>LANGCHAIN_METADATA</indexed-entity>\n"
                    + "</indexed-entities>\n"
                    + "</indexing>\n"
                    + "</distributed-cache>";

    public static final String ITEM_PACKAGE = "dev.langchain4j";
    public static final String LANGCHAIN_ITEM = "LangChainItem";
    public static final String METADATA_ITEM = "LangChainMetadata";

    /**
     * Creates an instance of InfinispanEmbeddingStore
     *
     * @param builder   Infinispan Configuration Builder
     * @param name      The name of the store
     * @param dimension The dimension of the store
     */
    public InfinispanEmbeddingStore(ConfigurationBuilder builder,
                                    String name,
                                    Integer dimension) {
        ensureNotNull(builder, "builder");
        ensureNotBlank(name, "name");
        ensureNotNull(dimension, "dimension");
        String langchainType = LANGCHAIN_ITEM + dimension;
        String metadataType = METADATA_ITEM + dimension;
        itemMarshaller = new LangChainItemMarshaller(computeTypeWithPackage(langchainType));
        metadataMarshaller = new LangChainMetadataMarshaller(computeTypeWithPackage(metadataType));
        builder.remoteCache(name)
                .configuration(DEFAULT_CACHE_CONFIG.replace("CACHE_NAME", name)
                        .replace("LANGCHAINITEM", itemMarshaller.getTypeName())
                        .replace("LANGCHAIN_METADATA", metadataMarshaller.getTypeName()));

        // Registers the schema on the client
        ProtoStreamMarshaller marshaller = new ProtoStreamMarshaller();
        SerializationContext serializationContext = marshaller.getSerializationContext();
        String fileName = ITEM_PACKAGE + "." + "dimension." + dimension + ".proto";
        Schema schema =  new Schema.Builder("magazine.proto")
              .packageName(ITEM_PACKAGE)
              .addMessage(metadataType)
                .addComment("@Indexed")
                .addField(Type.Scalar.STRING, "name", 1)
                    .addComment("@Text")
                .addField(Type.Scalar.STRING, "value", 2)
                    .addComment("@Text")
              .addMessage(langchainType)
                .addComment("@Indexed")
                .addField(Type.Scalar.STRING, "id", 1)
                    .addComment("@Text")
                .addField(Type.Scalar.STRING, "text", 2)
                    .addComment("@Keyword")
                .addRepeatedField(Type.Scalar.FLOAT, "embedding", 3)
                    .addComment("@Vector(dimension=" + dimension + ", similarity=COSINE)")
                .addRepeatedField(Type.create(metadataType), "metadata", 4)
              .build();

        String schemaContent =  schema.toString();
        FileDescriptorSource fileDescriptorSource = FileDescriptorSource.fromString(fileName, schemaContent);
        serializationContext.registerProtoFiles(fileDescriptorSource);
        serializationContext.registerMarshaller(metadataMarshaller);
        serializationContext.registerMarshaller(itemMarshaller);
        builder.marshaller(marshaller);
        // Uploads the schema to the server
        RemoteCacheManager rmc = new RemoteCacheManager(builder.build());
        RemoteCache<String, String> metadataCache = rmc
                .getCache(ProtobufMetadataManagerConstants.PROTOBUF_METADATA_CACHE_NAME);
        metadataCache.put(fileName, schemaContent);

        this.remoteCache = rmc.getCache(name);
    }

    private static String computeTypeWithPackage(String langchainType) {
        return ITEM_PACKAGE + "." + langchainType;
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
        addAllInternal(ids, embeddings, null);
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .collect(toList());
        addAllInternal(ids, embeddings, embedded);
        return ids;
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        Query<Object[]> query = remoteCache.query("select i, score(i) from " + itemMarshaller.getTypeName() + " i where i.embedding <-> " + Arrays.toString(referenceEmbedding.vector()) + "~3");
        List<Object[]> hits = query.maxResults(maxResults).list();

        return hits.stream().map(obj -> {
            LangChainInfinispanItem item = (LangChainInfinispanItem) obj[0];
            Float score = (Float) obj[1];
            if (score.doubleValue() < minScore) {
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
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAllInternal(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
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
                Set<LangChainMetadata> metadata = textSegment.metadata().asMap().entrySet().stream()
                      .map(e -> new LangChainMetadata(e.getKey(), e.getValue())).collect(Collectors.toSet());
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
        private ConfigurationBuilder builder;
        private String name;
        private Integer dimension;

        /**
         * Infinispan cache name to be used, will be created on first access
         */
        public Builder cacheName(String name) {
            this.name = name;
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
         * Infinispan Configuration Builder
         *
         * @param builder, Infinispan client configuration builder
         * @return this Builder
         */
        public Builder infinispanConfigBuilder(ConfigurationBuilder builder) {
            this.builder = builder;
            return this;
        }

        /**
         * Builds the store
         *
         * @return InfinispanEmbeddingStore
         */
        public InfinispanEmbeddingStore build() {
            return new InfinispanEmbeddingStore(builder, name, dimension);
        }
    }
}
