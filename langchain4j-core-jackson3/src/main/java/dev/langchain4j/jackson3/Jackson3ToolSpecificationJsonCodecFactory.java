package dev.langchain4j.jackson3;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolSpecificationJsonCodec;
import dev.langchain4j.spi.PrioritizedFactory;
import dev.langchain4j.spi.agent.tool.ToolSpecificationJsonCodecFactory;
import static dev.langchain4j.spi.PrioritizedFactory.YIELDS_TO_OTHERS;


@Internal
public class Jackson3ToolSpecificationJsonCodecFactory implements ToolSpecificationJsonCodecFactory, PrioritizedFactory {

    @Override
    public int priority() {
        return YIELDS_TO_OTHERS; // a framework that supplies its own codec keeps it
    }


    @Override
    public ToolSpecificationJsonCodec create() {
        return new Jackson3ToolSpecificationJsonCodec();
    }
}
