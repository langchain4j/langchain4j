package dev.langchain4j.model.chat.mock;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static java.util.Arrays.asList;

/**
 * An implementation of a {@link StreamingChatLanguageModel} useful for unit testing.
 * This implementation is experimental and subject to change in the future. It may utilize Mockito internally.
 */
public class StreamingChatModelMock implements StreamingChatLanguageModel {

    private final List<String> tokens;

    public StreamingChatModelMock(List<String> tokens) {
        this.tokens = new ArrayList<>(ensureNotEmpty(tokens, "tokens"));
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        tokens.forEach(handler::onNext);
        handler.onComplete(Response.from(AiMessage.from(String.join("", tokens))));
    }

    public static StreamingChatModelMock thatAlwaysStreams(String... tokens) {
        return new StreamingChatModelMock(asList(tokens));
    }

    public static StreamingChatModelMock thatAlwaysStreams(List<String> tokens) {
        return new StreamingChatModelMock(tokens);
    }
}
