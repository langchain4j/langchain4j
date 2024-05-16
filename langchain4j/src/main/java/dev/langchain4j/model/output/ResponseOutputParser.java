package dev.langchain4j.model.output;

import dev.langchain4j.data.message.AiMessage;

import java.util.Set;

import static dev.langchain4j.internal.Utils.setOf;

public class ResponseOutputParser implements OutputParser<Response<AiMessage>> {
    @Override
    public Set<Class<?>> getSupportedTypes() {
        return setOf(Response.class);
    }

    @Override
    public Response<AiMessage> parse(final OutputParsingContext context) {
        return context.getResponse();
    }

    @Override
    public String formatInstructions() {
        return null;
    }
}
