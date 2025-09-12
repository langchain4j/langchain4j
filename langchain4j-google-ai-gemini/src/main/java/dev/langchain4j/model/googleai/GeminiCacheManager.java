package dev.langchain4j.model.googleai;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

class GeminiCacheManager {

    private final GeminiService geminiService;
    private final Map<String, GeminiCachedContent> cachedContents;

    GeminiCacheManager(GeminiService geminiService) {
        this.geminiService = geminiService;

        GoogleAiListCachedContentsRequest listCachedContentsRequest = new GoogleAiListCachedContentsRequest();
        listCachedContentsRequest.setPageSize(1000);
        this.cachedContents = new ConcurrentHashMap<>(Optional.ofNullable(geminiService.listCachedContents(listCachedContentsRequest)
                        .getCachedContents()).orElse(Collections.emptyList()).stream()
                .collect(Collectors.toMap(GeminiCachedContent::getDisplayName, Function.identity())));
    }

    public String getOrCreateCached(String key, Duration ttl, GeminiPart content, String model) {
        return cachedContents.compute(key, (__, cachedContent) -> {
            if (cachedContent != null) {
                Instant expirationTime = Instant.parse(cachedContent.getExpireTime());
                if (expirationTime.isAfter(Instant.now().plusSeconds(60))) {
                    return cachedContent;
                }
            }
            return createCachedContent(key, ttl, content, model);
        }).getName();
    }

    private GeminiCachedContent createCachedContent(String key, Duration ttl, GeminiPart content, String model) {
        GeminiCachedContent cachedContent = GeminiCachedContent.builder()
                .contents(List.of(new GeminiContent(List.of(content), GeminiRole.MODEL.toString())))
                .ttl(ttl.toSeconds() + "s")
                .displayName(key)
                .build();
        return geminiService.createCachedContent(model, cachedContent);
    }

}
