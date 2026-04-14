package dev.langchain4j.model.bedrock;

import software.amazon.awssdk.services.bedrockruntime.model.CacheTTL;

/**
 * Cache TTL options for Bedrock prompt caching.
 *
 * @see <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/prompt-caching.html">AWS Bedrock Prompt Caching</a>
 */
public enum BedrockCacheTtl {

    /**
     * 5-minute cache TTL (default).
     * The cache entry expires after 5 minutes of inactivity.
     * Each cache hit resets the TTL.
     */
    FIVE_MINUTES(CacheTTL.VALUE_5_M),

    /**
     * 1-hour cache TTL.
     * The cache entry expires after 1 hour of inactivity.
     * Each cache hit resets the TTL.
     * <p>
     * Note: 1-hour TTL has a higher cache write cost (2x base input price)
     * compared to 5-minute TTL (1.25x base input price).
     * Supported models: Claude Sonnet 4.5, Claude Opus 4.5, Claude Haiku 4.5.
     */
    ONE_HOUR(CacheTTL.VALUE_1_H);

    private final CacheTTL sdkCacheTtl;

    BedrockCacheTtl(CacheTTL sdkCacheTtl) {
        this.sdkCacheTtl = sdkCacheTtl;
    }

    CacheTTL toSdkCacheTtl() {
        return sdkCacheTtl;
    }
}
