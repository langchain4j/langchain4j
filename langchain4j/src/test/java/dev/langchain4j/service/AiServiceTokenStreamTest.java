package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiServiceTokenStreamTest {

    private static final InvocationContext DEFAULT_INVOCATION_CONTEXT = InvocationContext.builder()
            .interfaceName("SomeInterface")
            .methodName("someMethod")
            .methodArgument("one")
            .methodArgument("two")
            .chatMemoryId("one")
            .build();

    static Consumer<String> DUMMY_PARTIAL_RESPONSE_HANDLER = (partialResponse) -> {};
    static BiConsumer<PartialResponse, PartialResponseContext> DUMMY_PARTIAL_RESPONSE_WITH_CONTEXT_HANDLER =
            (partialResponse, partialResponseContext) -> {};
    static Consumer<PartialThinking> DUMMY_PARTIAL_THINKING_HANDLER = (partialThinking) -> {};
    static BiConsumer<PartialThinking, PartialThinkingContext> DUMMY_PARTIAL_THINKING_WITH_CONTEXT_HANDLER =
            (partialThinking, partialThinkingContext) -> {};
    static Consumer<PartialToolCall> DUMMY_PARTIAL_TOOL_CALL_HANDLER = (partialToolCall) -> {};
    static BiConsumer<PartialToolCall, PartialToolCallContext> DUMMY_PARTIAL_TOOL_CALL_WITH_CONTEXT_HANDLER =
            (partialToolCall, partialToolCallContext) -> {};
    static Consumer<Throwable> DUMMY_ERROR_HANDLER = (error) -> {};
    static Consumer<ChatResponse> DUMMY_CHAT_RESPONSE_HANDLER = (chatResponse) -> {};
    static Consumer<BeforeToolExecution> DUMMY_BEFORE_TOOL_EXECUTION_HANDLER = (beforeToolExecution) -> {};

    List<ChatMessage> messages = new ArrayList<>();

    @Mock
    List<Content> content;

    @Mock
    Object memoryId;

    AiServiceTokenStream tokenStream;

    @BeforeEach
    void prepareMessages() {
        this.messages.add(dev.langchain4j.data.message.SystemMessage.from("A system message"));
        this.tokenStream = setupAiServiceTokenStream();
    }

    @Test
    void start_with_onPartialResponse_shouldNotThrowException() {
        tokenStream.onPartialResponse(DUMMY_PARTIAL_RESPONSE_HANDLER).ignoreErrors();

        assertThatNoException().isThrownBy(() -> tokenStream.start());
    }

    @Test
    void start_onPartialResponseNotInvoked_withOnCompleteResponse_shouldNotThrowException() {
        tokenStream.onCompleteResponse(DUMMY_CHAT_RESPONSE_HANDLER).ignoreErrors();

        assertThatNoException().isThrownBy(() -> tokenStream.start());
    }

    @Test
    void start_withOnPartialResponseAndOnCompleteResponse_shouldNotThrowException() {
        tokenStream
                .onPartialResponse(DUMMY_PARTIAL_RESPONSE_HANDLER)
                .onCompleteResponse(DUMMY_CHAT_RESPONSE_HANDLER)
                .ignoreErrors();

        assertThatNoException().isThrownBy(() -> tokenStream.start());
    }

    @Test
    void start_onPartialResponseInvokedMultipleTimes_shouldThrowException() {
        tokenStream
                .onPartialResponse(DUMMY_PARTIAL_RESPONSE_HANDLER)
                .onPartialResponse(DUMMY_PARTIAL_RESPONSE_HANDLER)
                .ignoreErrors();

        assertThatThrownBy(() -> tokenStream.start())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("One of [onPartialResponse, onPartialResponseWithContext] can be invoked on TokenStream "
                        + "at most 1 time");
    }

    @Test
    void start_onPartialResponse_and_onPartialResponseWithContext_shouldThrowException() {
        tokenStream
                .onPartialResponse(DUMMY_PARTIAL_RESPONSE_HANDLER)
                .onPartialResponseWithContext(DUMMY_PARTIAL_RESPONSE_WITH_CONTEXT_HANDLER)
                .ignoreErrors();

        assertThatThrownBy(() -> tokenStream.start())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("One of [onPartialResponse, onPartialResponseWithContext] can be invoked on TokenStream "
                        + "at most 1 time");
    }

    @Test
    void start_beforeToolExecutionInvoked_shouldNotThrowException() {
        tokenStream.beforeToolExecution(DUMMY_BEFORE_TOOL_EXECUTION_HANDLER).ignoreErrors();

        assertThatNoException().isThrownBy(() -> tokenStream.start());
    }

    @Test
    void start_beforeToolExecutionInvokedMultipleTimes_shouldThrowException() {
        tokenStream
                .beforeToolExecution(DUMMY_BEFORE_TOOL_EXECUTION_HANDLER)
                .beforeToolExecution(DUMMY_BEFORE_TOOL_EXECUTION_HANDLER)
                .ignoreErrors();

        assertThatThrownBy(() -> tokenStream.start())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("beforeToolExecution can be invoked on TokenStream at most 1 time");
    }

    @Test
    void start_onErrorNorIgnoreErrorsInvoked_shouldThrowException() {
        tokenStream.onPartialResponse(DUMMY_PARTIAL_RESPONSE_HANDLER);

        assertThatThrownBy(() -> tokenStream.start())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("One of [onError, ignoreErrors] must be invoked on TokenStream exactly 1 time");
    }

    @Test
    void start_onErrorAndIgnoreErrorsInvoked_shouldThrowException() {
        tokenStream.onError(DUMMY_ERROR_HANDLER).ignoreErrors();

        assertThatThrownBy(() -> tokenStream.start())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("One of [onError, ignoreErrors] must be invoked on TokenStream exactly 1 time");
    }

    @Test
    void start_onPartialThinkingInvokedMultipleTimes_shouldThrowException() {
        tokenStream
                .onPartialThinking(DUMMY_PARTIAL_THINKING_HANDLER)
                .onPartialThinking(DUMMY_PARTIAL_THINKING_HANDLER)
                .ignoreErrors();

        assertThatThrownBy(() -> tokenStream.start())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("One of [onPartialThinking, onPartialThinkingWithContext] can be invoked on TokenStream "
                        + "at most 1 time");
    }

    @Test
    void start_onPartialThinking_and_onPartialThinkingWithContext_shouldThrowException() {
        tokenStream
                .onPartialThinking(DUMMY_PARTIAL_THINKING_HANDLER)
                .onPartialThinkingWithContext(DUMMY_PARTIAL_THINKING_WITH_CONTEXT_HANDLER)
                .ignoreErrors();

        assertThatThrownBy(() -> tokenStream.start())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("One of [onPartialThinking, onPartialThinkingWithContext] can be invoked on TokenStream "
                        + "at most 1 time");
    }

    @Test
    void start_with_onPartialToolCall_Consumer_shouldNotThrowException() {
        tokenStream.onPartialToolCall(DUMMY_PARTIAL_TOOL_CALL_HANDLER).ignoreErrors();

        assertThatNoException().isThrownBy(() -> tokenStream.start());
    }

    @Test
    void start_with_onPartialToolCallWithContext_BiConsumer_shouldNotThrowException() {
        tokenStream
                .onPartialToolCallWithContext(DUMMY_PARTIAL_TOOL_CALL_WITH_CONTEXT_HANDLER)
                .ignoreErrors();

        assertThatNoException().isThrownBy(() -> tokenStream.start());
    }

    @Test
    void start_onPartialToolCallInvokedMultipleTimes_shouldThrowException() {
        tokenStream
                .onPartialToolCall(DUMMY_PARTIAL_TOOL_CALL_HANDLER)
                .onPartialToolCall(DUMMY_PARTIAL_TOOL_CALL_HANDLER)
                .ignoreErrors();

        assertThatThrownBy(() -> tokenStream.start())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("One of [onPartialToolCall, onPartialToolCallWithContext] can be "
                        + "invoked on TokenStream at most 1 time");
    }

    @Test
    void start_onPartialToolCall_Consumer_and_onPartialToolCallWithContext_BiConsumer_shouldThrowException() {
        tokenStream
                .onPartialToolCall(DUMMY_PARTIAL_TOOL_CALL_HANDLER)
                .onPartialToolCallWithContext(DUMMY_PARTIAL_TOOL_CALL_WITH_CONTEXT_HANDLER)
                .ignoreErrors();

        assertThatThrownBy(() -> tokenStream.start())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("One of [onPartialToolCall, onPartialToolCallWithContext] can be "
                        + "invoked on TokenStream at most 1 time");
    }

    @Test
    void start_onCompleteResponseInvokedMultipleTimes_shouldThrowException() {
        tokenStream
                .onCompleteResponse(DUMMY_CHAT_RESPONSE_HANDLER)
                .onCompleteResponse(DUMMY_CHAT_RESPONSE_HANDLER)
                .ignoreErrors();

        assertThatThrownBy(() -> tokenStream.start())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("onCompleteResponse can be invoked on TokenStream at most 1 time");
    }

    @Test
    void start_onIntermediateResponseInvokedMultipleTimes_shouldThrowException() {
        tokenStream
                .onIntermediateResponse(DUMMY_CHAT_RESPONSE_HANDLER)
                .onIntermediateResponse(DUMMY_CHAT_RESPONSE_HANDLER)
                .ignoreErrors();

        assertThatThrownBy(() -> tokenStream.start())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("onIntermediateResponse can be invoked on TokenStream at most 1 time");
    }

    private AiServiceTokenStream setupAiServiceTokenStream() {
        StreamingChatModel streamingModel = mock(StreamingChatModel.class);
        ChatModel chatModel = mock(ChatModel.class);

        AiServiceContext context = AiServiceContext.create(getClass());
        context.streamingChatModel = streamingModel;
        context.chatModel = chatModel;

        return new AiServiceTokenStream(AiServiceTokenStreamParameters.builder()
                .messages(messages)
                .retrievedContents(content)
                .context(context)
                .invocationContext(InvocationContext.builder()
                        .chatMemoryId(memoryId)
                        .invocationParameters(new InvocationParameters())
                        .build())
                .commonGuardrailParams(GuardrailRequestParams.builder()
                        .chatMemory(null)
                        .augmentationResult(null)
                        .userMessageTemplate("")
                        .variables(Map.of())
                        .invocationContext(DEFAULT_INVOCATION_CONTEXT)
                        .build())
                .toolArgumentsErrorHandler((e, c) -> {
                    throw new RuntimeException(e);
                })
                .toolExecutionErrorHandler((e, c) -> ToolErrorHandlerResult.text(e.getMessage()))
                .build());
    }
}
