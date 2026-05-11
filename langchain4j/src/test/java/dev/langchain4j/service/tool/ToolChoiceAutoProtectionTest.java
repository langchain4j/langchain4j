package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.AiServices;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Validates {@link AiServices#forceToolChoiceAutoAfterFirstIteration(boolean)}: when set,
 * a caller-supplied {@link ToolChoice#REQUIRED} is rewritten to {@link ToolChoice#AUTO}
 * on follow-up iterations of the inference-and-tools loop. This prevents an infinite loop
 * when the LLM honours {@code REQUIRED} on every iteration.
 *
 * <p>The tests inject {@link ToolChoice#REQUIRED} via a one-shot {@code chatRequestTransformer}
 * that runs only on the very first chat call — mirroring how downstream framework integrators
 * (e.g. Quarkus) bind the choice to the initial request rather than every follow-up.
 */
class ToolChoiceAutoProtectionTest {

    interface Assistant {
        String chat(String message);
    }

    static class StickyTool {
        final AtomicInteger calls = new AtomicInteger();

        @Tool
        public String stick(String arg) {
            calls.incrementAndGet();
            return "ok";
        }
    }

    /**
     * A model that responds with a tool call whenever the request's tool choice is
     * {@link ToolChoice#REQUIRED}, and with a final text message once the tool choice
     * is {@link ToolChoice#AUTO} (or unset).
     */
    private static AiMessage respondBasedOnToolChoice(ChatRequest request) {
        ToolChoice toolChoice = request.parameters().toolChoice();
        if (toolChoice == ToolChoice.REQUIRED) {
            return AiMessage.from(ToolExecutionRequest.builder()
                    .id("c-" + System.nanoTime())
                    .name("stick")
                    .arguments("{\"arg0\": \"x\"}")
                    .build());
        }
        return AiMessage.from("done");
    }

    @Test
    void without_flag_required_tool_choice_loops_until_max_invocations() {
        // Without the flag, the loop's parameters.overrideWith(...) carries forward the
        // caller-supplied ToolChoice.REQUIRED on every iteration, so the model keeps emitting
        // tool calls until ToolService aborts at maxSequentialToolsInvocations.
        StickyTool tool = new StickyTool();
        ChatModelMock model = ChatModelMock.thatResponds(ToolChoiceAutoProtectionTest::respondBasedOnToolChoice);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(50))
                .tools(tool)
                .maxSequentialToolsInvocations(5)
                .chatRequestTransformer((req, memId) -> ChatRequest.builder()
                        .messages(req.messages())
                        .parameters(req.parameters().overrideWith(ChatRequestParameters.builder()
                                .toolChoice(ToolChoice.REQUIRED)
                                .build()))
                        .build())
                .build();

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> assistant.chat("go"))
                .withMessageContaining("sequential tool invocations");
    }

    @Test
    void with_flag_required_tool_choice_is_rewritten_to_auto_after_first_iteration() {
        // With forceToolChoiceAutoAfterFirstIteration(true), the loop rewrites REQUIRED to AUTO
        // before each follow-up chat call. Our one-shot transformer only sets REQUIRED on the
        // very first request — so the loop's rewrite then carries AUTO forward and the model
        // produces a final text message, breaking the loop.
        StickyTool tool = new StickyTool();
        ChatModelMock model = ChatModelMock.thatResponds(ToolChoiceAutoProtectionTest::respondBasedOnToolChoice);

        AtomicInteger transformerCalls = new AtomicInteger();
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(50))
                .tools(tool)
                .maxSequentialToolsInvocations(5)
                .forceToolChoiceAutoAfterFirstIteration(true)
                .chatRequestTransformer((req, memId) -> {
                    if (transformerCalls.getAndIncrement() == 0) {
                        return ChatRequest.builder()
                                .messages(req.messages())
                                .parameters(req.parameters().overrideWith(ChatRequestParameters.builder()
                                        .toolChoice(ToolChoice.REQUIRED)
                                        .build()))
                                .build();
                    }
                    return req;
                })
                .build();

        assertThatNoException().isThrownBy(() -> assistant.chat("go"));
        // Iteration 0: REQUIRED -> tool called once. Iteration 1: AUTO (rewritten) -> "done".
        assertThat(tool.calls.get()).isEqualTo(1);
    }

    interface SomeService {
        String chat(String message);
    }

    /**
     * Drives the loop directly with {@code parameters.toolChoice() == REQUIRED} to lock down the
     * exact Hook 2 rewrite contract: when the flag is on, the second iteration's chat request
     * must carry {@link ToolChoice#AUTO}; when the flag is off, it must keep {@link ToolChoice#REQUIRED}.
     * Other parameters (e.g. modelName, temperature) must be preserved across the rewrite.
     */
    @Test
    void rewrite_fires_only_when_flag_is_on_and_preserves_other_parameters() {
        // The mock returns a tool call on iteration 1 (so the loop sends a 2nd chat request),
        // and a final text on iteration 2.
        // Always responds with a final text — limits the loop to a single follow-up chat call.
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(AiMessage.from("done"));

        StickyTool tool = new StickyTool();
        ToolService toolService = new ToolService();
        toolService.tools(Collections.singletonList(tool));
        toolService.forceToolChoiceAutoAfterFirstIteration(true);

        AiServiceContext context = AiServiceContext.create(SomeService.class);
        context.chatModel = model;
        context.toolService = toolService;

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
        UserMessage userMessage = UserMessage.from("go");
        chatMemory.add(userMessage);

        InvocationContext invocationContext = InvocationContext.builder()
                .interfaceName(SomeService.class.getName())
                .methodName("chat")
                .userMessage(userMessage)
                .chatMemoryId("default")
                .timestampNow()
                .build();

        // Iteration 0's response is synthesized — caller would normally produce this via the chat model.
        ChatResponse iterationZero = ChatResponse.builder()
                .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                        .id("c0")
                        .name("stick")
                        .arguments("{\"arg0\": \"x\"}")
                        .build()))
                .metadata(ChatResponseMetadata.builder().build())
                .build();

        // The caller-supplied parameters carry REQUIRED, modelName, and a temperature — the rewrite
        // must touch only toolChoice.
        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .modelName("test-model")
                .temperature(0.42)
                .toolChoice(ToolChoice.REQUIRED)
                .build();

        ToolServiceContext toolServiceContext =
                toolService.createContext(invocationContext, userMessage, new ArrayList<>(chatMemory.messages()));

        toolService.executeInferenceAndToolsLoop(
                context,
                "default",
                iterationZero,
                parameters,
                new ArrayList<>(chatMemory.messages()),
                chatMemory,
                invocationContext,
                toolServiceContext,
                false);

        // Exactly one follow-up chat call was issued (loop terminated after the final text).
        assertThat(model.requests()).hasSize(1);
        ChatRequest followUp = model.requests().get(0);
        // Hook 2: second iteration sees AUTO, not REQUIRED.
        assertThat(followUp.parameters().toolChoice()).isEqualTo(ToolChoice.AUTO);
        // Other parameters preserved through overrideWith.
        assertThat(followUp.parameters().modelName()).isEqualTo("test-model");
        assertThat(followUp.parameters().temperature()).isEqualTo(0.42);
    }

    @Test
    void without_flag_required_tool_choice_is_preserved_into_second_iteration() {
        // Always responds with a final text — limits the loop to a single follow-up chat call.
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(AiMessage.from("done"));

        StickyTool tool = new StickyTool();
        ToolService toolService = new ToolService();
        toolService.tools(Collections.singletonList(tool));
        // Default: forceToolChoiceAutoAfterFirstIteration is false.

        AiServiceContext context = AiServiceContext.create(SomeService.class);
        context.chatModel = model;
        context.toolService = toolService;

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
        UserMessage userMessage = UserMessage.from("go");
        chatMemory.add(userMessage);

        InvocationContext invocationContext = InvocationContext.builder()
                .interfaceName(SomeService.class.getName())
                .methodName("chat")
                .userMessage(userMessage)
                .chatMemoryId("default")
                .timestampNow()
                .build();

        ChatResponse iterationZero = ChatResponse.builder()
                .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                        .id("c0")
                        .name("stick")
                        .arguments("{\"arg0\": \"x\"}")
                        .build()))
                .metadata(ChatResponseMetadata.builder().build())
                .build();

        ChatRequestParameters parameters =
                ChatRequestParameters.builder().toolChoice(ToolChoice.REQUIRED).build();

        ToolServiceContext toolServiceContext =
                toolService.createContext(invocationContext, userMessage, new ArrayList<>(chatMemory.messages()));

        toolService.executeInferenceAndToolsLoop(
                context,
                "default",
                iterationZero,
                parameters,
                new ArrayList<>(chatMemory.messages()),
                chatMemory,
                invocationContext,
                toolServiceContext,
                false);

        // Default behavior: REQUIRED is forwarded unchanged on subsequent iterations.
        assertThat(model.requests()).hasSize(1);
        assertThat(model.requests().get(0).parameters().toolChoice()).isEqualTo(ToolChoice.REQUIRED);
    }

    @Test
    void with_flag_auto_and_none_pass_through_unchanged() {
        // Flag is on, but parameters.toolChoice() != REQUIRED — the rewrite branch must NOT fire.
        // Always responds with a final text — limits the loop to a single follow-up chat call.
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(AiMessage.from("done"));

        StickyTool tool = new StickyTool();
        ToolService toolService = new ToolService();
        toolService.tools(Collections.singletonList(tool));
        toolService.forceToolChoiceAutoAfterFirstIteration(true);

        AiServiceContext context = AiServiceContext.create(SomeService.class);
        context.chatModel = model;
        context.toolService = toolService;

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
        UserMessage userMessage = UserMessage.from("go");
        chatMemory.add(userMessage);

        InvocationContext invocationContext = InvocationContext.builder()
                .interfaceName(SomeService.class.getName())
                .methodName("chat")
                .userMessage(userMessage)
                .chatMemoryId("default")
                .timestampNow()
                .build();

        ChatResponse iterationZero = ChatResponse.builder()
                .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                        .id("c0")
                        .name("stick")
                        .arguments("{\"arg0\": \"x\"}")
                        .build()))
                .metadata(ChatResponseMetadata.builder().build())
                .build();

        ChatRequestParameters parameters =
                ChatRequestParameters.builder().toolChoice(ToolChoice.AUTO).build();

        ToolServiceContext toolServiceContext =
                toolService.createContext(invocationContext, userMessage, new ArrayList<>(chatMemory.messages()));

        toolService.executeInferenceAndToolsLoop(
                context,
                "default",
                iterationZero,
                parameters,
                new ArrayList<>(chatMemory.messages()),
                chatMemory,
                invocationContext,
                toolServiceContext,
                false);

        assertThat(model.requests()).hasSize(1);
        assertThat(model.requests().get(0).parameters().toolChoice()).isEqualTo(ToolChoice.AUTO);
    }
}
