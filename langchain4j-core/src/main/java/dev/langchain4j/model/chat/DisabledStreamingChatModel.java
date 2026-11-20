package dev.langchain4j.model.chat;

import dev.langchain4j.model.ModelDisabledException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

/**
 * A {@link StreamingChatModel} that is disabled: {@link #chat(ChatRequest, StreamingChatResponseHandler)} and the other
 * {@code chat} methods that take a {@link StreamingChatResponseHandler} report a {@link ModelDisabledException} to the
 * handler's {@code onError}.
 * <p>
 * This could be used in tests, or in libraries that extend this one to conditionally enable or disable functionality.
 * </p>
 */
public class DisabledStreamingChatModel implements StreamingChatModel {

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        throw new ModelDisabledException("StreamingChatModel is disabled");
    }
}
