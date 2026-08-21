package dev.langchain4j.json.jackson3;

import dev.langchain4j.agent.tool.ToolSpecificationJsonCodec;
import dev.langchain4j.spi.agent.tool.ToolSpecificationJsonCodecFactory;

public class Jackson3ToolSpecificationJsonCodecFactory implements ToolSpecificationJsonCodecFactory {

    @Override
    public ToolSpecificationJsonCodec create() {
        return new Jackson3ToolSpecificationJsonCodec();
    }
}
