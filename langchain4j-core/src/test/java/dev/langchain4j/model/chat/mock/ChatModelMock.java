package dev.langchain4j.model.chat.mock;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Collections.synchronizedList;

/**
 * An implementation of a {@link ChatLanguageModel} useful for unit testing.
 * Always returns a static response and records all invocations for verification at the end of a test.
 * This implementation is experimental and subject to change in the future. It may utilize Mockito internally.
 */
public class ChatModelMock implements ChatLanguageModel {

    private final String staticResponse;
    private final ToolExecutionRequest toolExecutionRequest;
    private final RuntimeException exception;
    private final List<List<ChatMessage>> requests = synchronizedList(new ArrayList<>());

    public ChatModelMock(ToolExecutionRequest toolExecutionRequest) {
        this.toolExecutionRequest = ensureNotNull(toolExecutionRequest, "toolExecutionRequest");
        this.staticResponse = null;
        this.exception = null;
    }

    public ChatModelMock(String staticResponse) {
        this.toolExecutionRequest = null;
        this.staticResponse = ensureNotBlank(staticResponse, "staticResponse");
        this.exception = null;
    }

    public ChatModelMock(RuntimeException exception) {
        this.toolExecutionRequest = null;
        this.staticResponse = null;
        this.exception = ensureNotNull(exception, "exception");
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        requests.add(new ArrayList<>(messages));

        if (exception != null) {
            throw exception;
        }

        return Response.from(AiMessage.from(staticResponse));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages,
                                        List<ToolSpecification> toolSpecifications) {
        requests.add(new ArrayList<>(messages));

        if (exception != null) {
            throw exception;
        }

        if (toolExecutionRequest == null) {
            Optional<ToolSpecification> toolSpecification = toolSpecifications.stream()
                    .filter(toolSpec -> toolSpec.name().equals(toolExecutionRequest.name()))
                    .findFirst();
            if (toolSpecification.isEmpty()) {
                throw runtime("Requested tool not found in the ToolSpecifications: " + toolExecutionRequest.name());
            }
            return Response.from(AiMessage.from(toolExecutionRequest));
        }

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

    public static ChatModelMock thatRequestsATool(String toolName) {
        return new ChatModelMock("This is a placeholder response for tool: " + toolName);
    }

    public static ChatModelMock thatAlwaysResponds(String response) {
        return new ChatModelMock(response);
    }

    public static ChatModelMock thatAlwaysThrowsException() {
        return thatAlwaysThrowsExceptionWithMessage("Something went wrong, but this is an expected exception");
    }

    public static ChatModelMock thatAlwaysThrowsExceptionWithMessage(String message) {
        return new ChatModelMock(new RuntimeException(message));
    }
}
