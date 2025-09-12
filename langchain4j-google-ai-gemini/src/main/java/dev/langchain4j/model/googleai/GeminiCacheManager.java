package dev.langchain4j.model.googleai;

import dev.langchain4j.exception.HttpException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(GeminiCacheManager.class);

    private final GeminiService geminiService;
    private final Map<String, CachedContentMetadata> cachedContents;

    GeminiCacheManager(GeminiService geminiService) {
        this.geminiService = geminiService;

        GoogleAiListCachedContentsRequest listCachedContentsRequest = new GoogleAiListCachedContentsRequest();
        listCachedContentsRequest.setPageSize(1000);
        this.cachedContents = new ConcurrentHashMap<>(Optional.ofNullable(geminiService.listCachedContents(listCachedContentsRequest)
                        .getCachedContents()).orElse(Collections.emptyList()).stream()
                .map(CachedContentMetadata::new)
                .collect(Collectors.toMap(CachedContentMetadata::getKey, Function.identity())));
        log.debug("Loaded existing cached contents: {}", cachedContents);
    }

    public String getOrCreateCached(String key, Duration ttl, GeminiPart content, String model) {
        return cachedContents.compute(key, (__, cachedContent) -> {
            if (cachedContent != null) {
                if (cachedContent.isExpired()) {
                    log.debug("Cached content for key '{}' is expired: {}", key, cachedContent);
                } else if (cachedContent.isAlmostExpired()) {
                    log.debug("Cached content for key '{}' is almost expired: {}", key, cachedContent);
                    deleteCachedContent(cachedContent);
                } else {
                    if (cachedContent.isChecksumVerified()) {
                        log.debug("Using existing cached content for key '{}': {}", key, cachedContent);
                        return cachedContent;
                    } else {
                        if (cachedContent.checksumMatches(content)) {
                            cachedContent.setChecksumVerified(true);
                            log.debug("Using existing cached content for key '{}' with matching checksum: {}", key, cachedContent);
                            return cachedContent;
                        } else {
                            log.debug("Cached content for key '{}' has different checksum: {}", key, cachedContent);
                            deleteCachedContent(cachedContent);
                        }
                    }
                }
            }
            return createCachedContent(key, ttl, content, model);
        }).getId();
    }

    private void deleteCachedContent(CachedContentMetadata cachedContent) {
        try {
            log.debug("Deleting cached content for key '{}': {}", cachedContent.getKey(), cachedContent);
            geminiService.deleteCachedContent(StringUtils.removeStart(cachedContent.getId(), "cachedContents/"));
        } catch (HttpException e) {
            if (e.statusCode() == 403 || e.statusCode() == 404) {
                log.debug("Couldn't delete cached content for key '{}': {}", cachedContent.getKey(), e.getMessage());
            } else {
                throw e;
            }
        }
    }

    private CachedContentMetadata createCachedContent(String key, Duration ttl, GeminiPart content, String model) {
        GeminiCachedContent cachedContent = GeminiCachedContent.builder()
                .contents(List.of(new GeminiContent(List.of(content), GeminiRole.MODEL.toString())))
                .ttl(ttl.toSeconds() + "s")
                .displayName(key + ":" + getChecksum(content))
                .build();
        cachedContent = geminiService.createCachedContent(model, cachedContent);

        CachedContentMetadata newCachedContent = new CachedContentMetadata(cachedContent);
        newCachedContent.setChecksumVerified(true);
        log.debug("Created new cached content for key '{}': {}", key, cachedContent);
        return newCachedContent;
    }

    private static String getChecksum(GeminiPart content) {
        return DigestUtils.sha256Hex(content.getText());
    }

    private static class CachedContentMetadata {

        String id;
        String key;
        String checksum;
        Instant expirationTime;
        GeminiCachedContent cachedContent;

        boolean checksumVerified;

        CachedContentMetadata(GeminiCachedContent cachedContent) {
            this.id = cachedContent.getName();
            String[] parts = cachedContent.getDisplayName().split(":");
            this.key = parts[0];
            this.checksum = parts.length == 2 ? parts[1] : "undefined";
            this.expirationTime = Instant.parse(cachedContent.getExpireTime());
            this.cachedContent = cachedContent;
            this.checksumVerified = false;
        }

        public String getId() {
            return id;
        }

        public String getKey() {
            return key;
        }

        public String getChecksum() {
            return checksum;
        }

        public boolean isChecksumVerified() {
            return checksumVerified;
        }

        public void setChecksumVerified(boolean checksumVerified) {
            this.checksumVerified = checksumVerified;
        }

        public boolean isAlmostExpired() {
            return expirationTime.minusSeconds(60).isBefore(Instant.now());
        }

        public boolean isExpired() {
            return expirationTime.isBefore(Instant.now());
        }

        public boolean checksumMatches(GeminiPart content) {
            return this.checksum.equals(GeminiCacheManager.getChecksum(content));
        }

        @Override
        public String toString() {
            return "CachedContentMetadata{" +
                   "id='" + id + '\'' +
                   ", key='" + key + '\'' +
                   ", checksum='" + checksum + '\'' +
                   ", expirationTime=" + expirationTime +
                   ", checksumVerified=" + checksumVerified +
                   '}';
        }

    }

}
