package dev.langchain4j.model.chat.common;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.langchain4j.model.chat.common.AbstractChatModelIT.WEATHER_TOOL;
import static dev.langchain4j.model.chat.request.ToolChoice.ANY;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

/**
 * This test makes sure that all {@link StreamingChatLanguageModel} implementations behave consistently.
 * <p>
 * Make sure these dependencies are present in the module where this test class is extended:
 * <pre>
 * <dependency>
 *     <groupId>dev.langchain4j</groupId>
 *     <artifactId>langchain4j-core</artifactId>
 *     <scope>test</scope>
 * </dependency>
 *
 * <dependency>
 *     <groupId>dev.langchain4j</groupId>
 *     <artifactId>langchain4j-core</artifactId>
 *     <classifier>tests</classifier>
 *     <type>test-jar</type>
 *     <scope>test</scope>
 * </dependency>
 * </pre>
 */
@TestInstance(PER_CLASS)
public abstract class AbstractStreamingChatModelIT {

    protected abstract List<StreamingChatLanguageModel> models();

    // TODO make sure all tests as in sync model

    @ParameterizedTest
    @MethodSource("models")
    void should_answer_simple_question(StreamingChatLanguageModel model) throws Exception {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany?"))
                .build();

        CompletableFuture<ChatResponse> futureChatResponse = new CompletableFuture<>();
        StringBuffer tokenAccumulator = new StringBuffer();
        AtomicInteger timesOnPartialResponseIsCalled = new AtomicInteger();
        Set<Thread> threads = new CopyOnWriteArraySet<>();

        // when
        model.chat(chatRequest, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                tokenAccumulator.append(partialResponse);
                timesOnPartialResponseIsCalled.incrementAndGet();
                threads.add(Thread.currentThread());
            }

            @Override
            public void onCompleteResponse(ChatResponse chatResponse) {
                futureChatResponse.complete(chatResponse);
                threads.add(Thread.currentThread());
            }

            @Override
            public void onError(Throwable error) {
                threads.add(Thread.currentThread());
                futureChatResponse.completeExceptionally(error);
            }
        });

        // then
        ChatResponse chatResponse = futureChatResponse.get(30, SECONDS);

        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        assertThat(tokenAccumulator.toString()).isEqualTo(aiMessage.text());

        if (assertTokenUsage()) {
            TokenUsage tokenUsage = chatResponse.tokenUsage();
            assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
            assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
            assertThat(tokenUsage.totalTokenCount())
                    .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.finishReason()).isEqualTo(STOP);
        }

        assertThat(timesOnPartialResponseIsCalled.get()).isGreaterThan(1);

        if (assertThreads()) {
            assertThat(threads).hasSize(1);
            assertThat(threads.iterator().next()).isNotEqualTo(Thread.currentThread());
        }
    }

    @EnabledIf("supportsTools")
    @ParameterizedTest
    @MethodSource("models")
    void should_call_a_tool(StreamingChatLanguageModel model) throws Exception {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the weather in Munich?"))
                .toolSpecifications(WEATHER_TOOL)
                .build();

        CompletableFuture<ChatResponse> futureChatResponse = new CompletableFuture<>();
        AtomicInteger timesOnPartialResponseIsCalled = new AtomicInteger();
        Set<Thread> threads = new CopyOnWriteArraySet<>();

        // when
        model.chat(chatRequest, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                timesOnPartialResponseIsCalled.incrementAndGet();
                threads.add(Thread.currentThread());
            }

            @Override
            public void onCompleteResponse(ChatResponse chatResponse) {
                futureChatResponse.complete(chatResponse);
                threads.add(Thread.currentThread());
            }

            @Override
            public void onError(Throwable error) {
                threads.add(Thread.currentThread());
                futureChatResponse.completeExceptionally(error);
            }
        });

        // then
        ChatResponse chatResponse = futureChatResponse.get(30, SECONDS);

        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(WEATHER_TOOL.name());
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"city\":\"Munich\"}");

        if (assertTokenUsage()) {
            TokenUsage tokenUsage = chatResponse.tokenUsage();
            assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
            assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
            assertThat(tokenUsage.totalTokenCount())
                    .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.finishReason()).isEqualTo(TOOL_EXECUTION);
        }

        if (assertTimesOnPartialResponseIsCalled()) {
            assertThat(timesOnPartialResponseIsCalled.get()).isEqualTo(0);
        }

        if (assertThreads()) {
            assertThat(threads).hasSize(1);
            assertThat(threads.iterator().next()).isNotEqualTo(Thread.currentThread());
        }
    }

    @DisabledIf("supportsTools")
    @ParameterizedTest
    @MethodSource("models")
    void should_fail_if_tools_are_not_supported(StreamingChatLanguageModel model) throws Exception {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the weather in Munich?"))
                .toolSpecifications(WEATHER_TOOL)
                .build();

        CompletableFuture<ChatResponse> futureChatResponse = new CompletableFuture<>();
        AtomicInteger timesOnPartialResponseIsCalled = new AtomicInteger();
        Set<Thread> threads = new CopyOnWriteArraySet<>();

        // when-then
        // TODO
    }

    @EnabledIf("supportsToolChoiceAnyWithMultipleTools")
    @ParameterizedTest
    @MethodSource("models")
    void should_force_LLM_to_call_any_tool(StreamingChatLanguageModel model) throws Exception {

        // given
        ToolSpecification calculatorTool = ToolSpecification.builder()
                .name("add_two_numbers")
                .parameters(JsonObjectSchema.builder()
                        .addIntegerProperty("a")
                        .addIntegerProperty("b")
                        .build())
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("I live in Munich"))
                .toolSpecifications(WEATHER_TOOL, calculatorTool)
                .toolChoice(ANY) // this will FORCE the LLM to call any tool
                .build();

        CompletableFuture<ChatResponse> futureChatResponse = new CompletableFuture<>();
        AtomicInteger timesOnPartialResponseIsCalled = new AtomicInteger();
        Set<Thread> threads = new CopyOnWriteArraySet<>();

        // when
        model.chat(chatRequest, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                timesOnPartialResponseIsCalled.incrementAndGet();
                threads.add(Thread.currentThread());
            }

            @Override
            public void onCompleteResponse(ChatResponse chatResponse) {
                futureChatResponse.complete(chatResponse);
                threads.add(Thread.currentThread());
            }

            @Override
            public void onError(Throwable error) {
                threads.add(Thread.currentThread());
                futureChatResponse.completeExceptionally(error);
            }
        });

        // then
        ChatResponse chatResponse = futureChatResponse.get(30, SECONDS);

        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(WEATHER_TOOL.name());
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"city\":\"Munich\"}");

        if (assertTokenUsage()) {
            TokenUsage tokenUsage = chatResponse.tokenUsage();
            assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
            assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
            assertThat(tokenUsage.totalTokenCount())
                    .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.finishReason()).isEqualTo(TOOL_EXECUTION);
        }

        if (assertTimesOnPartialResponseIsCalled()) {
            assertThat(timesOnPartialResponseIsCalled.get()).isEqualTo(0);
        }

        if (assertThreads()) {
            assertThat(threads).hasSize(1);
            assertThat(threads.iterator().next()).isNotEqualTo(Thread.currentThread());
        }
    }

    @EnabledIf("supportsToolChoiceAnyWithSingleTool")
    @ParameterizedTest
    @MethodSource("models")
    void should_force_LLM_to_call_specific_tool(StreamingChatLanguageModel model) throws Exception {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("I live in Munich"))
                .toolSpecifications(WEATHER_TOOL)
                .toolChoice(ANY) // this will FORCE the LLM to call weatherTool
                .build();

        CompletableFuture<ChatResponse> futureChatResponse = new CompletableFuture<>();
        AtomicInteger timesOnPartialResponseIsCalled = new AtomicInteger();
        Set<Thread> threads = new CopyOnWriteArraySet<>();

        // when
        model.chat(chatRequest, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                timesOnPartialResponseIsCalled.incrementAndGet();
                threads.add(Thread.currentThread());
            }

            @Override
            public void onCompleteResponse(ChatResponse chatResponse) {
                futureChatResponse.complete(chatResponse);
                threads.add(Thread.currentThread());
            }

            @Override
            public void onError(Throwable error) {
                threads.add(Thread.currentThread());
                futureChatResponse.completeExceptionally(error);
            }
        });

        // then
        ChatResponse chatResponse = futureChatResponse.get(30, SECONDS);

        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(WEATHER_TOOL.name());
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"city\":\"Munich\"}");

        if (assertTokenUsage()) {
            TokenUsage tokenUsage = chatResponse.tokenUsage();
            assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
            assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
            assertThat(tokenUsage.totalTokenCount())
                    .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.finishReason()).isEqualTo(TOOL_EXECUTION);
        }

        assertThat(timesOnPartialResponseIsCalled.get()).isEqualTo(0); // TODO
        // TODO if timesOnPartialResponseIsCalled is 0, aiMessage.text() must be null

        if (assertThreads()) {
            assertThat(threads).hasSize(1);
            assertThat(threads.iterator().next()).isNotEqualTo(Thread.currentThread());
        }
    }

    protected boolean supportsTools() {
        return true;
    }

    protected boolean supportsToolChoiceAnyWithSingleTool() {
        return supportsTools();
    }

    protected boolean supportsToolChoiceAnyWithMultipleTools() {
        return supportsTools();
    }

    protected boolean assertTokenUsage() {
        return true;
    }

    protected boolean assertFinishReason() {
        return true;
    }

    protected boolean assertThreads() {
        return true;
    }

    protected boolean assertTimesOnPartialResponseIsCalled() {
        return true;
    }
}
