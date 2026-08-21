package dev.langchain4j.spi.json;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.WireJsonSpec;

/**
 * Creates the {@link Json.JsonCodec} used to serialize a provider's wire DTOs.
 * <p>
 * Providers obtain their codec through {@link dev.langchain4j.internal.WireJson}, so supplying an
 * implementation of this factory replaces the JSON library used for provider requests and
 * responses without touching the providers themselves.
 */
@Internal
public interface WireJsonCodecFactory {

    Json.JsonCodec create(WireJsonSpec spec);
}
