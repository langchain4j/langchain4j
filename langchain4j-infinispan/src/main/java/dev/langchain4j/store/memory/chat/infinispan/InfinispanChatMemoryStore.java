package dev.langchain4j.store.memory.chat.infinispan;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.CustomMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.configuration.StringConfiguration;
import org.infinispan.commons.marshall.ProtoStreamMarshaller;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.schema.Schema;
import org.infinispan.protostream.schema.Type;

/**
 * A {@link ChatMemoryStore} backed by an Infinispan cache using ProtoStream marshalling.
 *
 * <p>Messages are stored as protobuf entities ({@link LangChainChatMemoryItem}) keyed by the memory ID.
 * Each message preserves its {@link ChatMessageType} as a protobuf enum, and all message fields
 * (text, tool execution requests, thinking, etc.) are stored as native protobuf fields.
 *
 * <p>By default, the cache is created as a distributed cache with {@code application/x-protostream} encoding
 * and no indexing. The protobuf schema includes indexing annotations ({@code @Indexed}, {@code @Text},
 * {@code @Basic}, {@code @Embedded}) that are only activated when the cache is configured with indexing enabled.
 *
 * <p>To enable full-text search on chat messages, provide a cache configuration with indexing
 * via {@link Builder#cacheConfig(String)}. The configuration must declare
 * {@code dev.langchain4j.LangChainChatMemoryItem} as an indexed entity:
 * <pre>{@code
 * String indexedCacheConfig = "<distributed-cache name=\"chat-memory\">\n"
 *     + "  <indexing storage=\"local-heap\">\n"
 *     + "    <indexed-entities>\n"
 *     + "      <indexed-entity>dev.langchain4j.LangChainChatMemoryItem</indexed-entity>\n"
 *     + "    </indexed-entities>\n"
 *     + "  </indexing>\n"
 *     + "</distributed-cache>";
 *
 * InfinispanChatMemoryStore store = InfinispanChatMemoryStore.builder()
 *     .infinispanConfigBuilder(builder)
 *     .cacheConfig(indexedCacheConfig)
 *     .build();
 * }</pre>
 *
 * <p>With indexing enabled, you can run Ickle queries directly on the underlying cache. Examples:
 * <ul>
 *   <li>Full-text search: {@code FROM dev.langchain4j.LangChainChatMemoryItem m WHERE m.messages.text : 'vintage store'}</li>
 *   <li>Filter by message type: {@code SELECT m.memoryId FROM dev.langchain4j.LangChainChatMemoryItem m
 *       JOIN m.messages msg WHERE msg.type = 'USER' AND msg.text : 'weather'}</li>
 *   <li>Fuzzy search: {@code FROM dev.langchain4j.LangChainChatMemoryItem m WHERE m.messages.text : 'cofee'~2}</li>
 * </ul>
 *
 * <p>When using a pre-configured {@link RemoteCacheManager} (e.g. in Quarkus), the ProtoStream schema and
 * marshallers must be registered externally. When using a {@link ConfigurationBuilder}, the schema and
 * marshallers are registered automatically.
 */
public class InfinispanChatMemoryStore implements ChatMemoryStore {

    public static final String DEFAULT_CACHE_NAME = "chat-memory";
    static final String DEFAULT_PACKAGE = "dev.langchain4j";
    static final String DEFAULT_TOOL_REQUEST_TYPE = "LangChainToolExecutionRequest";
    static final String DEFAULT_CHAT_MESSAGE_TYPE = "LangChainChatMessage";
    static final String DEFAULT_CHAT_MEMORY_ITEM_TYPE = "LangChainChatMemoryItem";
    static final String DEFAULT_SCHEMA_FILE = "langchain-chat-memory.proto";

    private static final String DEFAULT_CACHE_CONFIG = "<distributed-cache name=\"CACHE_NAME\">\n"
            + "<encoding>\n"
            + "<key media-type=\"application/x-protostream\"/>\n"
            + "<value media-type=\"application/x-protostream\"/>\n"
            + "</encoding>\n"
            + "</distributed-cache>";

    private final RemoteCache<String, LangChainChatMemoryItem> remoteCache;

    /**
     * Returns the underlying remote cache. Useful for running Ickle queries
     * directly when the cache is configured with indexing.
     *
     * @return the remote cache
     */
    public RemoteCache<String, LangChainChatMemoryItem> getRemoteCache() {
        return remoteCache;
    }

    /**
     * Creates an InfinispanChatMemoryStore from a pre-configured RemoteCacheManager.
     * Assumes marshalling configuration is already provided by the RemoteCacheManager instance.
     *
     * @param remoteCacheManager the already configured remote cache manager
     * @param cacheName          the cache name to use
     * @param createCache        whether to create the cache if it doesn't exist
     * @param cacheConfig        optional custom cache configuration XML
     */
    public InfinispanChatMemoryStore(
            RemoteCacheManager remoteCacheManager, String cacheName, boolean createCache, String cacheConfig) {
        ensureNotNull(remoteCacheManager, "remoteCacheManager");
        ensureNotBlank(cacheName, "cacheName");

        if (createCache) {
            String config = cacheConfig != null ? cacheConfig : DEFAULT_CACHE_CONFIG.replace("CACHE_NAME", cacheName);
            this.remoteCache =
                    remoteCacheManager.administration().getOrCreateCache(cacheName, new StringConfiguration(config));
        } else {
            this.remoteCache = remoteCacheManager.getCache(cacheName);
        }
    }

    /**
     * Creates an InfinispanChatMemoryStore from a ConfigurationBuilder.
     * Registers the ProtoStream schema and marshallers.
     *
     * @param builder     the Infinispan configuration builder
     * @param cacheName   the cache name to use
     * @param createCache whether to create the cache if it doesn't exist
     * @param cacheConfig optional custom cache configuration XML
     */
    public InfinispanChatMemoryStore(
            ConfigurationBuilder builder, String cacheName, boolean createCache, String cacheConfig) {
        ensureNotNull(builder, "builder");
        ensureNotBlank(cacheName, "cacheName");

        if (createCache) {
            String config = cacheConfig != null ? cacheConfig : DEFAULT_CACHE_CONFIG.replace("CACHE_NAME", cacheName);
            builder.remoteCache(cacheName).configuration(config);
        }

        Schema schema = buildSchema();

        ProtoStreamMarshaller marshaller = new ProtoStreamMarshaller();
        SerializationContext serializationContext = marshaller.getSerializationContext();
        serializationContext.registerProtoFiles(
                FileDescriptorSource.fromString(DEFAULT_SCHEMA_FILE, schema.toString()));
        serializationContext.registerMarshaller(new ChatMessageTypeMarshaller(DEFAULT_PACKAGE + ".ChatMessageType"));
        serializationContext.registerMarshaller(
                new LangChainToolExecutionRequestMarshaller(DEFAULT_PACKAGE + "." + DEFAULT_TOOL_REQUEST_TYPE));
        serializationContext.registerMarshaller(
                new LangChainChatMessageMarshaller(DEFAULT_PACKAGE + "." + DEFAULT_CHAT_MESSAGE_TYPE));
        serializationContext.registerMarshaller(
                new LangChainChatMemoryItemMarshaller(DEFAULT_PACKAGE + "." + DEFAULT_CHAT_MEMORY_ITEM_TYPE));
        builder.marshaller(marshaller);

        RemoteCacheManager remoteCacheManager = new RemoteCacheManager(builder.build());
        remoteCacheManager.administration().schemas().createOrUpdate(schema);

        this.remoteCache = remoteCacheManager.getCache(cacheName);
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        ensureNotNull(memoryId, "memoryId");
        LangChainChatMemoryItem item = remoteCache.get(memoryId.toString());
        if (item == null) {
            return new ArrayList<>();
        }
        return item.messages().stream()
                .map(InfinispanChatMemoryStore::toChatMessage)
                .collect(Collectors.toList());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        ensureNotNull(memoryId, "memoryId");
        ensureNotNull(messages, "messages");
        List<LangChainChatMessage> protoMessages =
                messages.stream().map(InfinispanChatMemoryStore::toProtoMessage).collect(Collectors.toList());
        remoteCache.put(memoryId.toString(), new LangChainChatMemoryItem(memoryId.toString(), protoMessages));
    }

    @Override
    public void deleteMessages(Object memoryId) {
        ensureNotNull(memoryId, "memoryId");
        remoteCache.remove(memoryId.toString());
    }

    static LangChainChatMessage toProtoMessage(ChatMessage message) {
        return switch (message.type()) {
            case SYSTEM -> {
                SystemMessage sm = (SystemMessage) message;
                yield new LangChainChatMessage(
                        ChatMessageType.SYSTEM, sm.text(), null, null, Collections.emptyList(), null, null, null);
            }
            case USER -> {
                UserMessage um = (UserMessage) message;
                String text = um.hasSingleText() ? um.singleText() : null;
                yield new LangChainChatMessage(
                        ChatMessageType.USER, text, um.name(), null, Collections.emptyList(), null, null, null);
            }
            case AI -> {
                AiMessage am = (AiMessage) message;
                List<LangChainToolExecutionRequest> toolRequests = am.hasToolExecutionRequests()
                        ? am.toolExecutionRequests().stream()
                                .map(r -> new LangChainToolExecutionRequest(r.id(), r.name(), r.arguments()))
                                .collect(Collectors.toList())
                        : Collections.emptyList();
                yield new LangChainChatMessage(
                        ChatMessageType.AI, am.text(), null, am.thinking(), toolRequests, null, null, null);
            }
            case TOOL_EXECUTION_RESULT -> {
                ToolExecutionResultMessage tm = (ToolExecutionResultMessage) message;
                String text = tm.hasSingleText() ? tm.text() : null;
                yield new LangChainChatMessage(
                        ChatMessageType.TOOL_EXECUTION_RESULT,
                        text,
                        null,
                        null,
                        Collections.emptyList(),
                        tm.id(),
                        tm.toolName(),
                        tm.isError());
            }
            case CUSTOM -> {
                yield new LangChainChatMessage(
                        ChatMessageType.CUSTOM, null, null, null, Collections.emptyList(), null, null, null);
            }
        };
    }

    static ChatMessage toChatMessage(LangChainChatMessage proto) {
        return switch (proto.type()) {
            case SYSTEM -> SystemMessage.from(proto.text());
            case USER -> {
                if (proto.name() != null) {
                    yield UserMessage.from(proto.name(), proto.text());
                }
                yield UserMessage.from(proto.text());
            }
            case AI -> {
                if (proto.toolExecutionRequests() != null
                        && !proto.toolExecutionRequests().isEmpty()) {
                    List<ToolExecutionRequest> requests = proto.toolExecutionRequests().stream()
                            .map(r -> ToolExecutionRequest.builder()
                                    .id(r.id())
                                    .name(r.name())
                                    .arguments(r.arguments())
                                    .build())
                            .collect(Collectors.toList());
                    yield AiMessage.builder()
                            .text(proto.text())
                            .thinking(proto.thinking())
                            .toolExecutionRequests(requests)
                            .build();
                }
                AiMessage.Builder builder = AiMessage.builder().text(proto.text());
                if (proto.thinking() != null) {
                    builder.thinking(proto.thinking());
                }
                yield builder.build();
            }
            case TOOL_EXECUTION_RESULT ->
                ToolExecutionResultMessage.builder()
                        .id(proto.toolExecutionId())
                        .toolName(proto.toolName())
                        .text(proto.text())
                        .isError(proto.isError())
                        .build();
            case CUSTOM -> CustomMessage.from(Collections.emptyMap());
        };
    }

    static Schema buildSchema() {
        return new Schema.Builder(DEFAULT_SCHEMA_FILE)
                .packageName(DEFAULT_PACKAGE)
                .addEnum("ChatMessageType")
                .addValue("SYSTEM", 0)
                .addValue("USER", 1)
                .addValue("AI", 2)
                .addValue("TOOL_EXECUTION_RESULT", 3)
                .addValue("CUSTOM", 4)
                .addMessage(DEFAULT_TOOL_REQUEST_TYPE)
                .addField(Type.Scalar.STRING, "id", 1)
                .addField(Type.Scalar.STRING, "name", 2)
                .addField(Type.Scalar.STRING, "arguments", 3)
                .addMessage(DEFAULT_CHAT_MESSAGE_TYPE)
                .addField(Type.create("ChatMessageType"), "type", 1)
                .addComment("@Basic")
                .addField(Type.Scalar.STRING, "text", 2)
                .addComment("@Text")
                .addField(Type.Scalar.STRING, "name", 3)
                .addComment("@Basic")
                .addField(Type.Scalar.STRING, "thinking", 4)
                .addComment("@Text")
                .addRepeatedField(Type.create(DEFAULT_TOOL_REQUEST_TYPE), "toolExecutionRequests", 5)
                .addField(Type.Scalar.STRING, "toolExecutionId", 6)
                .addComment("@Basic")
                .addField(Type.Scalar.STRING, "toolName", 7)
                .addComment("@Basic")
                .addField(Type.Scalar.BOOL, "isError", 8)
                .addMessage(DEFAULT_CHAT_MEMORY_ITEM_TYPE)
                .addComment("@Indexed")
                .addField(Type.Scalar.STRING, "memoryId", 1)
                .addComment("@Basic")
                .addRepeatedField(Type.create(DEFAULT_CHAT_MESSAGE_TYPE), "messages", 2)
                .addComment("@Embedded(structure=NESTED)")
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ConfigurationBuilder configurationBuilder;
        private RemoteCacheManager remoteCacheManager;
        private String cacheName = DEFAULT_CACHE_NAME;
        private String cacheConfig;
        private boolean createCache = true;

        /**
         * Infinispan cache name to be used, defaults to "chat-memory"
         */
        public Builder cacheName(String cacheName) {
            this.cacheName = cacheName;
            return this;
        }

        /**
         * Custom Infinispan cache configuration XML. When not set, a distributed cache with
         * {@code application/x-protostream} encoding and no indexing is created.
         * Use this to enable indexing for full-text search on chat messages.
         */
        public Builder cacheConfig(String cacheConfig) {
            this.cacheConfig = cacheConfig;
            return this;
        }

        /**
         * Create cache in the server, defaults to true
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
         * Pre-configured RemoteCacheManager
         */
        public Builder remoteCacheManager(RemoteCacheManager remoteCacheManager) {
            this.remoteCacheManager = remoteCacheManager;
            return this;
        }

        public InfinispanChatMemoryStore build() {
            if (remoteCacheManager != null) {
                return new InfinispanChatMemoryStore(remoteCacheManager, cacheName, createCache, cacheConfig);
            }
            ensureNotNull(configurationBuilder, "configurationBuilder or remoteCacheManager");
            return new InfinispanChatMemoryStore(configurationBuilder, cacheName, createCache, cacheConfig);
        }
    }
}
