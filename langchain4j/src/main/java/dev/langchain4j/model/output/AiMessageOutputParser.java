package dev.langchain4j.model.output;

import dev.langchain4j.data.message.AiMessage;

import java.util.Set;

import static dev.langchain4j.internal.Utils.setOf;

public class AiMessageOutputParser implements OutputParser<AiMessage> {

    @Override
    public Set<Class<?>> getSupportedTypes() {
        return setOf(AiMessage.class);
    }

    @Override
    public AiMessage parse(final OutputParsingContext context) {
        return context.getResponse().content();
    }

    @Override
    public String formatInstructions() {
        return null;
    }
}
