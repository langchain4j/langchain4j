package dev.langchain4j.model.googleai;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GeminiCacheManager {

    private static final Logger log = LoggerFactory.getLogger(GeminiCacheManager.class);

    private final GeminiService geminiService;
    private final ConcurrentMap<String, CachedContentMetadata> cachedContents;

    public GeminiCacheManager(GeminiService geminiService) {
        this.geminiService = geminiService;

        GoogleAiListCachedContentsRequest listCachedContentsRequest = new GoogleAiListCachedContentsRequest();
        listCachedContentsRequest.setPageSize(1000);
        this.cachedContents = new ConcurrentHashMap<>(Optional.ofNullable(geminiService.listCachedContents(listCachedContentsRequest)
                        .getCachedContents()).orElse(Collections.emptyList()).stream()
                .map(CachedContentMetadata::new)
                .collect(Collectors.toConcurrentMap(CachedContentMetadata::getEffectiveKey, Function.identity(),
                        BinaryOperator.maxBy(Comparator.comparing(CachedContentMetadata::getExpirationTime)))));
        log.debug("Loaded existing cached contents: {}", cachedContents);
    }

    /**
     * Returns the server-side cache ID for the given content, creating one if needed.
     * The slot is keyed by {@code cacheKey + ":" + sha256(systemInstruction, tools, toolConfig)},
     * so divergent content coexists as separate slots instead of evicting each other.
     * Orphaned caches (e.g. from prompt updates between deploys, or rare cross-node races) are
     * left to expire passively via TTL rather than reaped — storage cost is negligible.
     */
    public String getOrCreateCached(String cacheKey, Duration ttl, GeminiContent content,
                                    List<GeminiTool> tools, GeminiToolConfig toolConfig, String model) {
        String effectiveKey = cacheKey + ":" + getChecksum(content, tools, toolConfig);
        return cachedContents.compute(effectiveKey, (__, cachedContent) -> {
            if (cachedContent != null && !cachedContent.isExpired()) {
                if (cachedContent.isAlmostExpired()) {
                    log.debug("Extending TTL for cached content cacheKey='{}' effectiveKey='{}': {}", cacheKey, effectiveKey, cachedContent);
                    return extendTtl(cachedContent, ttl);
                }
                log.debug("Using existing cached content cacheKey='{}' effectiveKey='{}': {}", cacheKey, effectiveKey, cachedContent);
                return cachedContent;
            }
            return createCachedContent(cacheKey, effectiveKey, ttl, content, tools, toolConfig, model);
        }).getId();
    }

    private CachedContentMetadata extendTtl(CachedContentMetadata cachedContent, Duration ttl) {
        GeminiCachedContent updated = GeminiCachedContent.builder()
                .ttl(ttl.toSeconds() + "s")
                .build();
        String cacheName = StringUtils.removeStart(cachedContent.getId(), "cachedContents/");
        updated = geminiService.updateCachedContent(cacheName, updated);
        CachedContentMetadata newMetadata = new CachedContentMetadata(updated);
        log.debug("Extended TTL for cached content effectiveKey='{}': {}", newMetadata.getEffectiveKey(), newMetadata);
        return newMetadata;
    }

    private CachedContentMetadata createCachedContent(String cacheKey, String effectiveKey, Duration ttl,
                                                      GeminiContent content, List<GeminiTool> tools,
                                                      GeminiToolConfig toolConfig, String model) {
        GeminiCachedContent cachedContent = GeminiCachedContent.builder()
                .systemInstruction(content)
                .tools(tools)
                .toolConfig(toolConfig)
                .ttl(ttl.toSeconds() + "s")
                .displayName(effectiveKey)
                .build();
        cachedContent = geminiService.createCachedContent(model, cachedContent);
        CachedContentMetadata newCachedContent = new CachedContentMetadata(cachedContent);
        log.debug("Created new cached content cacheKey='{}' effectiveKey='{}': {}", cacheKey, effectiveKey, newCachedContent);
        return newCachedContent;
    }

    /**
     * Stable SHA-256 over the system instruction, tools, and tool config. Function declarations
     * are sorted by name before serialization so the same logical tool set hashes identically
     * regardless of upstream iteration order — important when the source is a Set (e.g. Set.of)
     * whose iteration is randomized per JVM run.
     */
    static String getChecksum(GeminiContent content, List<GeminiTool> tools, GeminiToolConfig toolConfig) {
        var sb = new StringBuilder();
        sb.append(content.parts().stream()
                .map(GeminiContent.GeminiPart::text)
                .collect(Collectors.joining(System.lineSeparator())));
        if (tools != null) {
            List<GeminiTool> normalizedTools = tools.stream()
                    .map(GeminiCacheManager::normalize)
                    .toList();
            sb.append(System.lineSeparator()).append(Json.toJson(normalizedTools));
        }
        if (toolConfig != null) {
            sb.append(System.lineSeparator()).append(Json.toJson(toolConfig));
        }
        return DigestUtils.sha256Hex(sb.toString());
    }

    private static GeminiTool normalize(GeminiTool tool) {
        if (tool.functionDeclarations() == null || tool.functionDeclarations().size() < 2) {
            return tool;
        }
        List<GeminiFunctionDeclaration> sorted = tool.functionDeclarations().stream()
                .sorted(Comparator.comparing(GeminiFunctionDeclaration::name))
                .toList();
        return new GeminiTool(
                sorted,
                tool.codeExecution(),
                tool.googleSearch(),
                tool.urlContext(),
                tool.googleMaps());
    }

    private static class CachedContentMetadata {

        final String id;
        final String cacheKey;
        final String effectiveKey;
        final Instant expirationTime;

        CachedContentMetadata(GeminiCachedContent cachedContent) {
            this.id = cachedContent.name();
            this.effectiveKey = cachedContent.displayName();
            int sep = effectiveKey.indexOf(':');
            this.cacheKey = sep >= 0 ? effectiveKey.substring(0, sep) : effectiveKey;
            this.expirationTime = Instant.parse(cachedContent.expireTime());
        }

        public String getId() {
            return id;
        }

        public String getEffectiveKey() {
            return effectiveKey;
        }

        public Instant getExpirationTime() {
            return expirationTime;
        }

        public boolean isAlmostExpired() {
            return expirationTime.minusSeconds(60).isBefore(Instant.now());
        }

        public boolean isExpired() {
            return expirationTime.isBefore(Instant.now());
        }

        @Override
        public String toString() {
            return "CachedContentMetadata{" +
                    "id='" + id + '\'' +
                    ", cacheKey='" + cacheKey + '\'' +
                    ", effectiveKey='" + effectiveKey + '\'' +
                    ", expirationTime=" + expirationTime +
                    '}';
        }

    }

}
