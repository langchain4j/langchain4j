package dev.langchain4j.model.openaiofficial.openai.responses;

import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModelAdapter;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialResponsesDynamicToolIT {

    private static final String MODEL_NAME = "gpt-5.4-mini";

    private static final ToolSpecification DYNAMIC_TOOL = ToolSpecification.builder()
            .name("call_tool")
            .description("Invokes a tool that was discovered at runtime. "
                    + "The argument schema is not known upfront, so pass whatever fields the tool needs.")
            .build();

    static List<ChatModel> models() {
        return List.of(
                OpenAiOfficialResponsesChatModel.builder()
                        .baseUrl(System.getenv("OPENAI_BASE_URL"))
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .modelName(MODEL_NAME)
                        .build(),
                StreamingChatModelAdapter.adapt(OpenAiOfficialResponsesStreamingChatModel.builder()
                        .baseUrl(System.getenv("OPENAI_BASE_URL"))
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .modelName(MODEL_NAME)
                        .build()));
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_send_arbitrary_arguments_to_a_tool_without_declared_parameters(ChatModel model) {

        // given
        UserMessage userMessage = UserMessage.from("Call call_tool to invoke the tool named get_weather "
                + "for the city Munich. Put get_weather into a tool_name field "
                + "and {\"city\": \"Munich\"} into an arguments field.");

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(DYNAMIC_TOOL)
                        .toolChoice(REQUIRED)
                        .build())
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        List<ToolExecutionRequest> toolExecutionRequests =
                chatResponse.aiMessage().toolExecutionRequests();
        assertThat(toolExecutionRequests).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = toolExecutionRequests.get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(DYNAMIC_TOOL.name());
        assertThat(toolExecutionRequest.arguments())
                .isNotBlank()
                .isNotEqualToIgnoringWhitespace("{}")
                .contains("get_weather", "Munich");
    }
}
