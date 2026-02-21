package dev.langchain4j.service.common.openai;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.common.AbstractAiServiceWithToolsIT;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

// TODO move to langchain4j-open-ai module once dependency cycle is resolved
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiResponsesAiServiceWithToolsIT extends AbstractAiServiceWithToolsIT {

    @Captor
    private ArgumentCaptor<ChatRequest> chatRequestCaptor;

    private static final JsonSchemaElement PRIMITIVE_TOOL_EXPECTED_SCHEMA = JsonObjectSchema.builder()
            .addIntegerProperty("arg0")
            .addIntegerProperty("arg1")
            .required("arg0", "arg1")
            .build();

    @Override
    protected List<ChatModel> models() {
        return List.of(new StreamingChatModelAdapter(OpenAiResponsesStreamingChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(GPT_4_O_MINI.toString())
                .temperature(0.0)
                .maxToolCalls(2)
                .parallelToolCalls(true)
                .strict(true)
                .build()));
    }

    @Override
    protected boolean supportsMapParameters() {
        return false;
    }

    @Override
    protected boolean supportsRecursion() {
        return true;
    }

    @ParameterizedTest
    @MethodSource("models")
    @Override
    protected void should_execute_normal_tool_in_parallel_with_primitive_parameters(ChatModel chatModel) {
        parallelToolsExecution(chatModel, ReturnBehavior.TO_LLM);
    }

    @ParameterizedTest
    @MethodSource("models")
    @Override
    protected void should_execute_immediate_tool_in_parallel_with_primitive_parameters(ChatModel chatModel) {
        parallelToolsExecution(chatModel, ReturnBehavior.IMMEDIATE);
    }

    private void parallelToolsExecution(ChatModel model, ReturnBehavior returnBehavior) {
        AdderTool toolInstance;
        int chatInvocations;

        switch (returnBehavior) {
            case TO_LLM -> {
                toolInstance = new ToolWithPrimitiveParameters();
                // 2 times = 2 tool requests + LLM response
                chatInvocations = 2;
            }
            case IMMEDIATE -> {
                toolInstance = new ImmediateToolWithPrimitiveParameters();
                // 1 time, the model is called only once returning immediately the tool result
                chatInvocations = 1;
            }
            default -> throw new IllegalStateException("Unexpected return behavior: " + returnBehavior);
        }

        // given
        model = spy(model);

        var tool = spy(toolInstance);

        var assistant =
                AiServices.builder(Assistant.class).chatModel(model).tools(tool).build();

        var text = "How much is 37 plus 87? How much is 73 plus 78? Call 2 tools in parallel (at the same time)!";

        // when
        var response = assistant.chat(text);

        // then
        assertThat(response.toolExecutions().size()).isGreaterThanOrEqualTo(2);
        assertThat(response.toolExecutions().stream().map(t -> t.result())).contains("124", "151");
        verify(tool, atLeastOnce()).add(37, 87);
        verify(tool, atLeastOnce()).add(73, 78);

        if (verifyModelInteractions()) {
            verify(model).supportedCapabilities();
            verify(model, times(chatInvocations)).chat(chatRequestCaptor.capture());

            var toolSpecifications = chatRequestCaptor.getValue().toolSpecifications();
            assertThat(toolSpecifications).hasSize(1);
            var toolSpecification = toolSpecifications.get(0);
            assertThat(toolSpecification.name()).isEqualTo("add");
            assertThat(toolSpecification.description()).isNull();
            assertThat(toolSpecification.parameters()).isEqualTo(PRIMITIVE_TOOL_EXPECTED_SCHEMA);
        }
    }

    interface AdderTool {
        int add(int a, int b);
    }

    static class ToolWithPrimitiveParameters implements AdderTool {
        @Tool
        @Override
        public int add(int a, int b) {
            return a + b;
        }
    }

    static class ImmediateToolWithPrimitiveParameters implements AdderTool {
        @Tool(returnBehavior = ReturnBehavior.IMMEDIATE)
        public int add(int a, int b) {
            return a + b;
        }
    }

    interface Assistant {
        Result<String> chat(String userMessage);
    }

    private static class StreamingChatModelAdapter implements ChatModel {

        private final StreamingChatModel streamingChatModel;

        private StreamingChatModelAdapter(StreamingChatModel streamingChatModel) {
            this.streamingChatModel = streamingChatModel;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
            streamingChatModel.chat(chatRequest, handler);
            return handler.get();
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return streamingChatModel.defaultRequestParameters();
        }

        @Override
        public List<ChatModelListener> listeners() {
            return streamingChatModel.listeners();
        }

        @Override
        public ModelProvider provider() {
            return streamingChatModel.provider();
        }

        @Override
        public Set<Capability> supportedCapabilities() {
            return streamingChatModel.supportedCapabilities();
        }
    }
}
