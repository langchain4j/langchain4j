package dev.langchain4j.store.memory.chat.infinispan;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.ArrayList;
import java.util.List;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.configuration.StringConfiguration;

/**
 * A {@link ChatMemoryStore} backed by an Infinispan cache.
 *
 * <p>Messages are serialized to JSON using {@link ChatMessageSerializer} and stored as strings,
 * following the same approach as the other ChatMemoryStore implementations (Coherence, Cassandra, Oracle).
 * The cache uses {@code application/x-protostream} encoding since strings are a native ProtoStream type.
 */
public class InfinispanChatMemoryStore implements ChatMemoryStore {

    public static final String DEFAULT_CACHE_NAME = "chat-memory";

    private static final String DEFAULT_CACHE_CONFIG = "<distributed-cache name=\"CACHE_NAME\" statistics=\"true\">\n"
            + "<encoding>\n"
            + "<key media-type=\"application/x-protostream\"/>\n"
            + "<value media-type=\"application/x-protostream\"/>\n"
            + "</encoding>\n"
            + "</distributed-cache>";

    private final RemoteCache<String, String> remoteCache;

    /**
     * Creates an InfinispanChatMemoryStore from a pre-configured RemoteCacheManager.
     *
     * @param remoteCacheManager the already configured remote cache manager
     * @param cacheName          the cache name to use
     * @param createCache        whether to create the cache if it doesn't exist. When {@code false}, the cache must
     *                           already exist on the server
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
            this.remoteCache = getExistingCache(remoteCacheManager, cacheName);
        }
    }

    /**
     * Creates an InfinispanChatMemoryStore from a ConfigurationBuilder.
     *
     * @param builder     the Infinispan configuration builder
     * @param cacheName   the cache name to use
     * @param createCache whether to create the cache if it doesn't exist. When {@code false}, the cache must already
     *                    exist on the server
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

        RemoteCacheManager remoteCacheManager = new RemoteCacheManager(builder.build());
        this.remoteCache = getExistingCache(remoteCacheManager, cacheName);
    }

    private static RemoteCache<String, String> getExistingCache(
            RemoteCacheManager remoteCacheManager, String cacheName) {
        RemoteCache<String, String> cache = remoteCacheManager.getCache(cacheName);
        if (cache == null) {
            throw new IllegalStateException("Infinispan cache '" + cacheName
                    + "' does not exist. Create it on the server, or set createCache(true) to let the store create it");
        }
        return cache;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        ensureNotNull(memoryId, "memoryId");
        String json = remoteCache.get(memoryId.toString());
        return json == null ? new ArrayList<>() : ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        ensureNotNull(memoryId, "memoryId");
        ensureNotNull(messages, "messages");
        String json = ChatMessageSerializer.messagesToJson(messages);
        remoteCache.put(memoryId.toString(), json);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        ensureNotNull(memoryId, "memoryId");
        remoteCache.remove(memoryId.toString());
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
         * {@code application/x-protostream} encoding is created.
         */
        public Builder cacheConfig(String cacheConfig) {
            this.cacheConfig = cacheConfig;
            return this;
        }

        /**
         * Create cache in the server, defaults to true.
         * When {@code false}, the cache must already exist on the server, otherwise building the store fails.
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
