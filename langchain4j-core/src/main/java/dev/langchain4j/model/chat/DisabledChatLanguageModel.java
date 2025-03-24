package dev.langchain4j.model.chat;

import dev.langchain4j.model.ModelDisabledException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * A {@link ChatLanguageModel} which throws a {@link ModelDisabledException} for all of its methods
 * <p>
 * This could be used in tests, or in libraries that extend this one to conditionally enable or disable functionality.
 * </p>
 */
public class DisabledChatLanguageModel implements ChatLanguageModel {

    @Override
    public ChatResponse doChat(final ChatRequest chatRequest) {
        throw new ModelDisabledException("ChatLanguageModel is disabled");
    }
}
