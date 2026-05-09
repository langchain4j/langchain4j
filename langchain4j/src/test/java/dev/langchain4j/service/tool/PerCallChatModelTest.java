package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.service.AiServiceContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Validates the per-call {@link ChatModel} overload of
 * {@link ToolService#executeInferenceAndToolsLoop(AiServiceContext, ChatModel, Object, ChatResponse,
 * ChatRequestParameters, java.util.List, ChatMemory, InvocationContext, ToolServiceContext, boolean)}.
 *
 * <p>Downstream framework integrators (e.g. {@code quarkus-langchain4j}) need this overload to
 * route follow-up inference requests to a method-specific {@code ChatModel}, e.g. when
 * {@code @ModelName} resolves to a different model than the one stored on the {@link AiServiceContext}.
 */
class PerCallChatModelTest {

    static class Echo {
        @Tool
        String echo(String arg) {
            return "echo:" + arg;
        }
    }

    @Test
    void per_call_chat_model_overload_uses_supplied_model_for_follow_up_requests() {
        // given
        // The caller already has the FIRST chatResponse in hand (it contains a tool call).
        // The loop will execute the tool, then call the LLM again — that follow-up should use
        // the per-call model, not context.chatModel.
        ChatModel defaultModel = ChatModelMock.thatAlwaysResponds(AiMessage.from("from-default-model"));
        ChatModel perCallModel = ChatModelMock.thatAlwaysResponds(AiMessage.from("from-per-call-model"));

        ToolService toolService = new ToolService();
        toolService.tools(java.util.Collections.singletonList(new Echo()));

        AiServiceContext context = AiServiceContext.create(SomeAiService.class);
        context.chatModel = defaultModel;
        context.toolService = toolService;

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
        UserMessage userMessage = UserMessage.from("go");
        chatMemory.add(userMessage);

        InvocationContext invocationContext = InvocationContext.builder()
                .interfaceName(SomeAiService.class.getName())
                .methodName("chat")
                .userMessage(userMessage)
                .chatMemoryId("default")
                .timestampNow()
                .build();

        // Initial response with a tool call — caller would normally have produced this from the
        // per-call ChatModel, but for this test we synthesize it so we can probe just the loop.
        ChatResponse initialResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                        .id("c1")
                        .name("echo")
                        .arguments("{\"arg0\": \"hi\"}")
                        .build()))
                .metadata(ChatResponseMetadata.builder().build())
                .build();

        ChatRequestParameters parameters = ChatRequestParameters.builder().build();
        ToolServiceContext toolServiceContext = toolService.createContext(invocationContext, userMessage, new ArrayList<>(chatMemory.messages()));

        // when — invoke the new overload, passing the per-call model explicitly.
        ToolServiceResult result = toolService.executeInferenceAndToolsLoop(
                context,
                perCallModel,
                "default",
                initialResponse,
                parameters,
                new ArrayList<>(chatMemory.messages()),
                chatMemory,
                invocationContext,
                toolServiceContext,
                false);

        // then — only the per-call model should have been invoked for follow-up inference;
        // the default model must not have been touched.
        assertThat(((ChatModelMock) perCallModel).requests()).hasSize(1);
        assertThat(((ChatModelMock) defaultModel).requests()).isEmpty();
        assertThat(result.finalResponse().aiMessage().text()).isEqualTo("from-per-call-model");
        assertThat(result.toolExecutions()).hasSize(1);
    }

    @Test
    void existing_overload_falls_back_to_context_chat_model() {
        // Sanity check: the original (no per-call model) overload should still use context.chatModel.
        ChatModel defaultModel = ChatModelMock.thatAlwaysResponds(AiMessage.from("from-default-model"));

        ToolService toolService = new ToolService();
        toolService.tools(java.util.Collections.singletonList(new Echo()));

        AiServiceContext context = AiServiceContext.create(SomeAiService.class);
        context.chatModel = defaultModel;
        context.toolService = toolService;

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
        UserMessage userMessage = UserMessage.from("go");
        chatMemory.add(userMessage);

        InvocationContext invocationContext = InvocationContext.builder()
                .interfaceName(SomeAiService.class.getName())
                .methodName("chat")
                .userMessage(userMessage)
                .chatMemoryId("default")
                .timestampNow()
                .build();

        ChatResponse initialResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                        .id("c1")
                        .name("echo")
                        .arguments("{\"arg0\": \"hi\"}")
                        .build()))
                .metadata(ChatResponseMetadata.builder().build())
                .build();

        ChatRequestParameters parameters = ChatRequestParameters.builder().build();
        ToolServiceContext toolServiceContext = toolService.createContext(invocationContext, userMessage, new ArrayList<>(chatMemory.messages()));

        ToolServiceResult result = toolService.executeInferenceAndToolsLoop(
                context,
                "default",
                initialResponse,
                parameters,
                new ArrayList<>(chatMemory.messages()),
                chatMemory,
                invocationContext,
                toolServiceContext,
                false);

        assertThat(((ChatModelMock) defaultModel).requests()).hasSize(1);
        assertThat(result.finalResponse().aiMessage().text()).isEqualTo("from-default-model");
    }

    interface SomeAiService {
        String chat(String message);
    }
}
