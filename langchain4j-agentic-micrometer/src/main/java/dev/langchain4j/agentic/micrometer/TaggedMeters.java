package dev.langchain4j.agentic.micrometer;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The meters of one name, looked up by the values of their tags. When no tags are added to them, they are cached by
 * these values, so that recording a meter already registered allocates no tags; the values are names of definitions,
 * of offered tools, outcomes and error types, so the cache is bounded, and a meter removed from the registry is
 * evicted. Added tags are not part of the key, so with them every record looks its meter up in the registry.
 */
final class TaggedMeters<M extends Meter> {

    private final Meter.MeterProvider<M> provider;
    private final String[] tagKeys;
    private final Map<List<String>, M> cache;

    TaggedMeters(boolean caching, Meter.MeterProvider<M> provider, String... tagKeys) {
        this.provider = provider;
        this.tagKeys = tagKeys;
        this.cache = caching ? new ConcurrentHashMap<>() : null;
    }

    M withTags(Tags addedTags, List<String> tagValues) {
        if (cache == null) {
            return provider.withTags(addedTags.and(tags(tagValues)));
        }
        M meter = cache.get(tagValues);
        if (meter == null) {
            meter = provider.withTags(tags(tagValues));
            cache.putIfAbsent(tagValues, meter);
        }
        return meter;
    }

    /**
     * Forgets a meter removed from its registry, under all the tag values it was cached by, as a meter filter can map
     * several of them to one meter, so that the next record registers it again.
     */
    void evict(Meter meter) {
        if (cache != null) {
            cache.values().removeIf(meter::equals);
        }
    }

    private Tags tags(List<String> tagValues) {
        String[] keyValues = new String[tagKeys.length * 2];
        for (int i = 0; i < tagKeys.length; i++) {
            keyValues[2 * i] = tagKeys[i];
            keyValues[2 * i + 1] = tagValues.get(i);
        }
        return Tags.of(keyValues);
    }
}
