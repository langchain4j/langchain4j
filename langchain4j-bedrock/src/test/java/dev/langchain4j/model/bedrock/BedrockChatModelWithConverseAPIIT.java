package dev.langchain4j.model.bedrock;

import static dev.langchain4j.data.message.ToolExecutionResultMessage.toolExecutionResultMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.bedrock.BedrockChatModelWithInvokeAPIIT.sleepIfNeeded;
import static dev.langchain4j.model.bedrock.TestedModelsWithConverseAPI.AI_JAMBA_1_5_MINI;
import static dev.langchain4j.model.bedrock.TestedModelsWithConverseAPI.AWS_NOVA_LITE;
import static dev.langchain4j.model.bedrock.TestedModelsWithConverseAPI.AWS_NOVA_MICRO;
import static dev.langchain4j.model.bedrock.TestedModelsWithConverseAPI.AWS_NOVA_PRO;
import static dev.langchain4j.model.bedrock.TestedModelsWithConverseAPI.CLAUDE_3_HAIKU;
import static dev.langchain4j.model.bedrock.TestedModelsWithConverseAPI.COHERE_COMMAND_R_PLUS;
import static dev.langchain4j.model.bedrock.TestedModelsWithConverseAPI.MISTRAL_LARGE;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.common.ChatModelCapabilities;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockChatModelWithConverseAPIIT extends AbstractChatModelIT {

    @Override
    protected List<ChatModelCapabilities<ChatLanguageModel>> models() {
        return List.of(
                AWS_NOVA_LITE,
                AWS_NOVA_PRO,
                AWS_NOVA_MICRO,
                COHERE_COMMAND_R_PLUS,
                AI_JAMBA_1_5_MINI,
                MISTRAL_LARGE,
                CLAUDE_3_HAIKU);
    }

    @Override
    protected String customModelName() {
        return "cohere.command-r-v1:0";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();
    }

    @Override
    protected ChatLanguageModel createModelWith(ChatRequestParameters parameters) {
        return BedrockChatModel.builder()
                .defaultRequestParameters(parameters)
                // force a working model with stopSequence parameter for @Tests
                .modelId("cohere.command-r-v1:0")
                .build();
    }

    // ToolChoice "only supported by Anthropic Claude 3 models and by Mistral AI Mistral Large" from
    // https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ToolChoice.html
    @Override
    protected boolean supportsToolChoiceRequired() {
        return false;
    }

    // output format not supported
    @Override
    protected boolean supportsJsonResponseFormatWithSchema() {
        return false;
    }

    @Override
    protected boolean assertExceptionType() {
        return false;
    }

    // OVERRIDE BECAUSE OF INCOHERENCY IN STOPSEQUENCE MANAGEMENT (Nova models include stopSequence)
    @ParameterizedTest
    @MethodSource("models")
    protected void should_respect_stopSequences_in_chat_request(
            ChatModelCapabilities<ChatLanguageModel> modelCapabilities) {
        if (List.of(AWS_NOVA_MICRO, AWS_NOVA_LITE, AWS_NOVA_PRO).contains(modelCapabilities)) {
            // given
            List<String> stopSequences = List.of("Hello", " Hello");
            ChatRequestParameters parameters =
                    ChatRequestParameters.builder().stopSequences(stopSequences).build();

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(UserMessage.from("Say 'Hello World'"))
                    .parameters(parameters)
                    .build();

            // when
            ChatResponse chatResponse =
                    chat(modelCapabilities.model(), chatRequest).chatResponse();

            // then
            AiMessage aiMessage = chatResponse.aiMessage();
            assertThat(aiMessage.text()).containsIgnoringCase("Hello");
            assertThat(aiMessage.text()).doesNotContainIgnoringCase("World");
            assertThat(aiMessage.toolExecutionRequests()).isNull();

            assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
        } else super.should_respect_stopSequences_in_chat_request(modelCapabilities);
    }

    // ADDING SOME TESTS SCENARIO ABSENT FROM AbstractChatModelIT
    @Test
    void should_generate_with_default_config() {

        BedrockChatModel bedrockChatModel = new BedrockChatModel("us.amazon.nova-micro-v1:0");
        assertThat(bedrockChatModel).isNotNull();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hi, how are you doing?"))
                .build();
        ChatResponse response = bedrockChatModel.chat(chatRequest);

        assertThat(response).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isIn(FinishReason.STOP, FinishReason.LENGTH);
    }

    @Test
    void should_call_multiple_functions() {

        ChatLanguageModel model =
                BedrockChatModel.builder().modelId("us.amazon.nova-micro-v1:0").build();

        UserMessage userMessage = userMessage(
                "Give three numbers, ordered by size: the sum of two plus two, the square of four, and finally the cube of eight.");

        List<ToolSpecification> toolSpecifications = asList(
                ToolSpecification.builder()
                        .name("sum")
                        .description("returns a sum of two numbers")
                        .parameters(JsonObjectSchema.builder()
                                .addIntegerProperty("first")
                                .addIntegerProperty("second")
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("square")
                        .description("returns the square of one number")
                        .parameters(JsonObjectSchema.builder()
                                .addIntegerProperty("number")
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("cube")
                        .description("returns the cube of one number")
                        .parameters(JsonObjectSchema.builder()
                                .addIntegerProperty("number")
                                .build())
                        .build());

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(toolSpecifications)
                        .build())
                .build();
        ChatResponse response = model.chat(chatRequest);

        AiMessage aiMessage = response.aiMessage();
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(userMessage);
        messages.add(aiMessage);
        assertThat(aiMessage.toolExecutionRequests()).hasSize(3);
        for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
            assertThat(toolExecutionRequest.name()).isNotEmpty();
            ToolExecutionResultMessage toolExecutionResultMessage;
            if (toolExecutionRequest.name().equals("sum")) {
                assertThat(toolExecutionRequest.arguments())
                        .isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");
                toolExecutionResultMessage = toolExecutionResultMessage(toolExecutionRequest, "4");
            } else if (toolExecutionRequest.name().equals("square")) {
                assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"number\": 4}");
                toolExecutionResultMessage = toolExecutionResultMessage(toolExecutionRequest, "16");
            } else if (toolExecutionRequest.name().equals("cube")) {
                assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"number\": 8}");
                toolExecutionResultMessage = toolExecutionResultMessage(toolExecutionRequest, "512");
            } else {
                throw new AssertionError("Unexpected tool name: " + toolExecutionRequest.name());
            }
            messages.add(toolExecutionResultMessage);
        }

        sleepIfNeeded();
        ChatRequest chatRequest2 = ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(toolSpecifications)
                        .build())
                .build();
        ChatResponse response2 = model.chat(chatRequest2);

        // then
        assertThat(response2.aiMessage().text()).contains("4", "16", "512");
        assertThat(response2.aiMessage().toolExecutionRequests()).isNull();

        TokenUsage tokenUsage2 = response2.tokenUsage();
        assertThat(tokenUsage2.inputTokenCount()).isPositive();
        assertThat(tokenUsage2.outputTokenCount()).isPositive();
        assertThat(tokenUsage2.totalTokenCount())
                .isEqualTo(tokenUsage2.inputTokenCount() + tokenUsage2.outputTokenCount());

        assertThat(response2.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_accept_PDF_documents() {

        // given
        ChatLanguageModel model =
                BedrockChatModel.builder().modelId("us.amazon.nova-lite-v1:0").build();
        UserMessage msg = UserMessage.from(
                PdfFileContent.from(
                        Paths.get("src/test/resources/gemini-doc-snapshot.pdf").toUri()),
                TextContent.from("Provide a summary of the document"));
        ChatRequest chatRequest = ChatRequest.builder().messages(msg).build();

        // when
        ChatResponse response = model.chat(chatRequest);

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("Gemini");
    }
}
