package dev.langchain4j.spi.json;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.TypeAllowlist;
import dev.langchain4j.spi.ServiceHelper;

/**
 * A factory for a JSON codec that writes and reads type information, discovered through
 * {@link ServiceHelper}.
 *
 * <p>Implement this to supply the codec used for state that can hold arbitrary values - agent state
 * being the case in LangChain4j - so that such state is read and written by the same JSON library
 * as everything else.
 *
 * @see dev.langchain4j.internal.PolymorphicJson
 */
@Internal
public interface PolymorphicJsonCodecFactory {

    /**
     * @param allowlist decides which types the returned codec may instantiate while reading.
     */
    Json.JsonCodec create(TypeAllowlist allowlist);
}
