package dev.langchain4j.model.anthropic.internal.api;

import dev.langchain4j.model.anthropic.internal.api.AnthropicMessageContent.CacheControl;
import lombok.Builder;
import lombok.Getter;

import java.util.function.Supplier;

@Getter
public class AnthropicCacheType {
    private final CacheType cacheType;
    private final MessageTypeApplyCache messageTypeApplyCache;

    @Builder
    public AnthropicCacheType(CacheType cacheType, MessageTypeApplyCache messageTypeApplyCache) {
        this.cacheType = cacheType;
        this.messageTypeApplyCache = messageTypeApplyCache;
    }

    public enum CacheType {
        NO_CACHE(() -> new CacheControl("no_cache")),
        EPHEMERAL(() -> new CacheControl("ephemeral"));

        private final Supplier<CacheControl> value;

        CacheType(Supplier<CacheControl> value) {
            this.value = value;
        }

        public CacheControl cacheControl() {
            return this.value.get();
        }
    }

    public enum MessageTypeApplyCache {
        ALL, NONE, SYSTEM_MESSAGE, USER_MESSAGE;
    }

    public CacheControl cacheControl() {
        return cacheType.cacheControl();
    }

    public boolean isApplyCache() {
        return this.cacheType != CacheType.NO_CACHE;
    }

    public boolean isApplyUserMessage() {
        return (this.messageTypeApplyCache == MessageTypeApplyCache.USER_MESSAGE || this.messageTypeApplyCache == MessageTypeApplyCache.ALL)
                && isApplyCache();
    }

    public boolean isApplySystemMessage() {
        return (this.messageTypeApplyCache == MessageTypeApplyCache.SYSTEM_MESSAGE || this.messageTypeApplyCache == MessageTypeApplyCache.ALL)
                && isApplyCache();
    }
}
