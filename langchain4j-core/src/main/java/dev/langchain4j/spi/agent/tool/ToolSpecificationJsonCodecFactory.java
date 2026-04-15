package dev.langchain4j.spi.agent.tool;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolSpecificationJsonCodec;

/**
 * A factory for creating {@link ToolSpecificationJsonCodec} instances through SPI.
 */
@Internal
public interface ToolSpecificationJsonCodecFactory {

    /**
     * Create a new {@link ToolSpecificationJsonCodec}.
     *
     * @return the new {@link ToolSpecificationJsonCodec}.
     */
    ToolSpecificationJsonCodec create();
}
