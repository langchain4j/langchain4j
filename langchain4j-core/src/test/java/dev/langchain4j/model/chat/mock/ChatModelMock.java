package dev.langchain4j.model.chat.mock;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.Exceptions.runtime;
import static java.util.Collections.synchronizedList;

/**
 * An implementation of a {@link ChatLanguageModel} useful for unit testing.
 * Always returns a static response and records all invocations for verification at the end of a test.
 * This implementation is experimental and subject to change in the future. It may utilize Mockito internally.
 */
public class ChatModelMock implements ChatLanguageModel {

    private final String staticResponse;
    private final List<List<ChatMessage>> requests = synchronizedList(new ArrayList<>());

    public ChatModelMock(String staticResponse) {
        this.staticResponse = staticResponse;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        requests.add(new ArrayList<>(messages));
        return Response.from(AiMessage.from(staticResponse));
    }

    public String userMessageText() {
        if (requests.size() != 1) {
            throw runtime("Expected exactly 1 request, got: " + requests.size());
        }

        List<ChatMessage> messages = requests.get(0);
        if (messages.size() != 1) {
            throw runtime("Expected exactly 1 message, got: " + messages.size());
        }

        ChatMessage message = messages.get(0);
        if (!(message instanceof UserMessage)) {
            throw runtime("Expected exactly UserMessage, got: " + message);
        }

        return message.text();
    }

    public static ChatModelMock withStaticResponse(String response) {
        return new ChatModelMock(response);
    }
}
