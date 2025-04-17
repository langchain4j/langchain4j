package dev.langchain4j.model.bedrock;

import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationFrom;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockStreamingChatModelWithConverseIT extends AbstractStreamingChatModelIT {

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(
                //                TestedModelsWithConverseAPI.STREAMING_AWS_NOVA_MICRO,
                TestedModelsWithConverseAPI.STREAMING_AWS_NOVA_LITE,
                TestedModelsWithConverseAPI.STREAMING_AWS_NOVA_PRO);
        //                TestedModelsWithConverseAPI.STREAMING_AI_JAMBA_1_5_MINI,
        //                TestedModelsWithConverseAPI.STREAMING_CLAUDE_3_HAIKU,
        //                TestedModelsWithConverseAPI.STREAMING_COHERE_COMMAND_R_PLUS,
        //                TestedModelsWithConverseAPI.STREAMING_MISTRAL_LARGE);
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        BedrockStreamingChatModel.Builder bedrockStreamingChatModelBuilder = BedrockStreamingChatModel.builder()
                .defaultRequestParameters(parameters)
                .logRequests(true)
                .logResponses(true);
        if (parameters.modelName() == null) {
            bedrockStreamingChatModelBuilder.modelId("us.amazon.nova-lite-v1:0");
        }
        return bedrockStreamingChatModelBuilder.build();
    }

    protected String customModelName() {
        return "cohere.command-r-v1:0";
    }

    // output format not supported
    @Override
    protected boolean supportsJsonResponseFormat() {
        return false;
    }

    // output format not supported
    @Override
    protected boolean supportsJsonResponseFormatWithSchema() {
        return false;
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return BedrockChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    // Nova models support StopSequence but have an incoherent behavior, it includes the stopSequence in the
    // response
    @Override
    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsStopSequencesParameter")
    protected void should_respect_stopSequences_in_chat_request(StreamingChatModel model) {

        // given
        List<String> stopSequences = List.of("ipsum", " Ipsum");
        ChatRequestParameters parameters = BedrockChatRequestParameters.builder()
                .stopSequences(stopSequences)
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Say 'Lorem ipsum dolor sit amet'"))
                .parameters(parameters)
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Lorem");
        assertThat(aiMessage.text()).doesNotContainIgnoringCase("dolor");
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
        }
    }

    @Override
    @Test
    protected void should_respect_stopSequences_in_default_model_parameters() {

        // given
        List<String> stopSequences = List.of("ipsum", " Ipsum");
        ChatRequestParameters parameters = BedrockChatRequestParameters.builder()
                .stopSequences(stopSequences)
                .build();
        StreamingChatModel model = createModelWith(parameters);

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Say 'Lorem ipsum dolor sit amet'"))
                .parameters(parameters)
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Lorem");
        assertThat(aiMessage.text()).doesNotContainIgnoringCase("dolor");
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
        }
    }

    @Test
    void should_reason() {
        // given
        StreamingChatModel model = BedrockStreamingChatModel.builder()
                .modelId("us.anthropic.claude-3-7-sonnet-20250219-v1:0")
                .defaultRequestParameters(BedrockChatRequestParameters.builder()
                        .enableReasoning(1024)
                        .build())
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany? "))
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringWhitespaces("Berlin");
    }

    @Test
    void should_fail_if_reasoning_enabled() {
        // given
        StreamingChatModel model = BedrockStreamingChatModel.builder()
                .modelId("us.amazon.nova-lite-v1:0")
                .defaultRequestParameters(BedrockChatRequestParameters.builder()
                        .enableReasoning(1024)
                        .build())
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany? "))
                .build();

        // when then
        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> chat(model, chatRequest));
    }

    static final ToolSpecification TODAY_TOOL =
            ToolSpecification.builder().name("getTodayDate").build();

    @Test
    void should_call_tool_with_no_parameters() {
        StreamingChatModel model = BedrockStreamingChatModel.builder()
                .modelId("us.amazon.nova-lite-v1:0")
                .build();
        UserMessage userMessage = userMessage("What's today's date?");

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(TODAY_TOOL)
                        .build())
                .build();

        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
            assertThat(toolExecutionRequest.name()).isEqualTo("getTodayDate");
        }
    }

    record Dinosaur(String name, String periodOfActivity, String description) {}

    record Milestone(String name, String period, String description) {}

    @Tool
    String mermaidTimelineDiagram(List<Milestone> milestons, List<Dinosaur> dinosaurs) {
        return "";
    }

    @Test
    void should_call_tool_with_chunked_parameters() {
        StreamingChatModel model = TestedModelsWithConverseAPI.STREAMING_CLAUDE_3_HAIKU;

        UserMessage userMessage = userMessage(
                "Create a clear timeline to be displayed in mermaid.live with iconic dinosaurs and major milestones of the Mesozoic era.");
        Method mermaidTimelineDiagram = Arrays.stream(
                        BedrockStreamingChatModelWithConverseIT.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("mermaidTimelineDiagram"))
                .findFirst()
                .orElseThrow();
        List<ToolSpecification> toolSpecifications = List.of(toolSpecificationFrom(mermaidTimelineDiagram));

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(toolSpecifications)
                .build();

        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
            assertThat(toolExecutionRequest.name()).isEqualTo("mermaidTimelineDiagram");
            assertThat(toolExecutionRequest.arguments()).isNotEmpty();
        }
    }

}
