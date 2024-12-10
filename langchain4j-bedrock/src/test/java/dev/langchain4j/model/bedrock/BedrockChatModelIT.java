package dev.langchain4j.model.bedrock;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.regions.Region;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.INTEGER;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.STRING;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.bedrock.BedrockMistralAiChatModel.Types.Mistral7bInstructV0_2;
import static dev.langchain4j.model.bedrock.BedrockMistralAiChatModel.Types.MistralMixtral8x7bInstructV0_1;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.INCLUDE;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockChatModelIT {

    private static final String CAT_IMAGE_URL = "https://upload.wikimedia.org/wikipedia/commons/e/e9/Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png";

    @Test
    void testBedrockAnthropicV3SonnetChatModel() {

        BedrockAnthropicMessageChatModel bedrockChatModel = BedrockAnthropicMessageChatModel
                .builder()
                .temperature(0.50f)
                .maxTokens(300)
                .region(Region.US_EAST_1)
                .model(BedrockAnthropicMessageChatModel.Types.AnthropicClaude3SonnetV1.getValue())
                .maxRetries(1)
                .timeout(Duration.ofMinutes(2L))
                .build();

        assertThat(bedrockChatModel).isNotNull();
        assertThat(bedrockChatModel.getTimeout().toMinutes()).isEqualTo(2L);

        Response<AiMessage> response = bedrockChatModel.generate(UserMessage.from("hi, how are you doing?"));

        assertThat(response).isNotNull();
        assertThat(response.content().text()).isNotBlank();
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isIn(FinishReason.STOP, FinishReason.LENGTH);
    }

    @Test
    void testBedrockAnthropicV3SonnetChatModelImageContent() {

        BedrockAnthropicMessageChatModel bedrockChatModel = BedrockAnthropicMessageChatModel
                .builder()
                .temperature(0.50f)
                .maxTokens(300)
                .region(Region.US_EAST_1)
                .model(BedrockAnthropicMessageChatModel.Types.AnthropicClaude3SonnetV1.getValue())
                .maxRetries(1)
                .build();

        assertThat(bedrockChatModel).isNotNull();
        assertThat(bedrockChatModel.getTimeout().toMinutes()).isEqualTo(1L);

        String base64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
        ImageContent imageContent = ImageContent.from(base64Data, "image/png");
        UserMessage userMessage = UserMessage.from(imageContent);

        Response<AiMessage> response = bedrockChatModel.generate(userMessage);

        assertThat(response).isNotNull();
        assertThat(response.content().text()).isNotBlank();
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isIn(FinishReason.STOP, FinishReason.LENGTH);
    }

    @Test
    void testFunctionCallingWithBedrockAnthropicV3SonnetChatModel() {

        BedrockAnthropicMessageChatModel bedrockChatModel = BedrockAnthropicMessageChatModel
                .builder()
                .temperature(0.00f)
                .maxTokens(300)
                .region(Region.US_EAST_1)
                .model(BedrockAnthropicMessageChatModel.Types.AnthropicClaude3SonnetV1.getValue())
                .maxRetries(1)
                .build();

        assertThat(bedrockChatModel).isNotNull();

        ToolSpecification calculator = ToolSpecification.builder()
                .name("calculator")
                .description("returns a sum of two numbers")
                .addParameter("first", INTEGER)
                .addParameter("second", INTEGER)
                .build();

        assertThat(calculator).isNotNull();

        UserMessage userMessage = UserMessage.from("2+2=?");

        Response<AiMessage> response = bedrockChatModel.generate(singletonList(userMessage), singletonList(calculator));

        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.id()).isNotBlank();
        assertThat(toolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "4");
        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        sleepIfNeeded();
        Response<AiMessage> secondResponse = bedrockChatModel.generate(messages, singletonList(calculator));

        AiMessage secondAiMessage = secondResponse.content();
        assertThat(secondAiMessage.text()).contains("4");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.inputTokenCount()).isEqualTo(318);
        assertThat(secondTokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsage.totalTokenCount()).isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @Test
    void testSequentialFunctionCallingWithBedrockAnthropicV3SonnetChatModel() {

        BedrockAnthropicMessageChatModel bedrockChatModel = BedrockAnthropicMessageChatModel
                .builder()
                .temperature(0.00f)
                .maxTokens(300)
                .region(Region.US_EAST_1)
                .model(BedrockAnthropicMessageChatModel.Types.AnthropicClaude3SonnetV1.getValue())
                .maxRetries(1)
                .build();

        assertThat(bedrockChatModel).isNotNull();

        ToolSpecification calculator = ToolSpecification.builder()
                .name("calculator")
                .description("returns a sum of two numbers")
                .addParameter("first", INTEGER)
                .addParameter("second", INTEGER)
                .build();

        assertThat(calculator).isNotNull();

        ToolSpecification currentTemperature = ToolSpecification.builder()
                .name("currentTemperature")
                .description("returns the temperature of a city in degrees Celsius")
                .addParameter("city", STRING)
                .build();

        assertThat(currentTemperature).isNotNull();

        List<ToolSpecification> toolSpecifications = asList(calculator, currentTemperature);

        assertThat(currentTemperature).isNotNull();
        assertThat(toolSpecifications).hasSize(2);

        UserMessage userMessageCalc = UserMessage.from("2+2=?");

        Response<AiMessage> responseCalc = bedrockChatModel.generate(singletonList(userMessageCalc), toolSpecifications);

        AiMessage aiMessageCalc = responseCalc.content();
        assertThat(aiMessageCalc.text()).isNull();
        assertThat(aiMessageCalc.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequestCalc = aiMessageCalc.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequestCalc.id()).isNotBlank();
        assertThat(toolExecutionRequestCalc.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequestCalc.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        TokenUsage tokenUsageCalc = responseCalc.tokenUsage();
        assertThat(tokenUsageCalc.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsageCalc.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsageCalc.totalTokenCount()).isEqualTo(tokenUsageCalc.inputTokenCount() + tokenUsageCalc.outputTokenCount());

        assertThat(responseCalc.finishReason()).isEqualTo(TOOL_EXECUTION);

        ToolExecutionResultMessage toolExecutionResultMessageCalc = from(toolExecutionRequestCalc, "4");
        List<ChatMessage> messagesCalc = asList(userMessageCalc, aiMessageCalc, toolExecutionResultMessageCalc);

        sleepIfNeeded();
        Response<AiMessage> secondResponseCalc = bedrockChatModel.generate(messagesCalc, toolSpecifications);

        AiMessage secondAiMessageCalc = secondResponseCalc.content();
        assertThat(secondAiMessageCalc.text()).contains("4");
        assertThat(secondAiMessageCalc.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsageCalc = secondResponseCalc.tokenUsage();
        assertThat(secondTokenUsageCalc.inputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsageCalc.outputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsageCalc.totalTokenCount()).isEqualTo(secondTokenUsageCalc.inputTokenCount() + secondTokenUsageCalc.outputTokenCount());

        assertThat(secondResponseCalc.finishReason()).isEqualTo(STOP);

        UserMessage userMessageTemp = UserMessage.from("Temperature in New York = ?");

        sleepIfNeeded();
        Response<AiMessage> responseTemp = bedrockChatModel.generate(singletonList(userMessageTemp), toolSpecifications);

        AiMessage aiMessageTemp = responseTemp.content();
        assertThat(aiMessageTemp.text()).isNull();
        assertThat(aiMessageTemp.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequestTemp = aiMessageTemp.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequestTemp.id()).isNotBlank();
        assertThat(toolExecutionRequestTemp.name()).isEqualTo("currentTemperature");
        assertThat(toolExecutionRequestTemp.arguments()).isEqualToIgnoringWhitespace("{\"city\": \"New York\"}");

        TokenUsage tokenUsageTemp = responseTemp.tokenUsage();
        assertThat(tokenUsageTemp.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsageTemp.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsageTemp.totalTokenCount()).isEqualTo(tokenUsageTemp.inputTokenCount() + tokenUsageTemp.outputTokenCount());

        assertThat(responseTemp.finishReason()).isEqualTo(TOOL_EXECUTION);

        ToolExecutionResultMessage toolExecutionResultMessageTemp = from(toolExecutionRequestTemp, "25.0");
        List<ChatMessage> messagesTemp = asList(userMessageTemp, aiMessageTemp, toolExecutionResultMessageTemp);

        sleepIfNeeded();
        Response<AiMessage> secondResponseTemp = bedrockChatModel.generate(messagesTemp, toolSpecifications);

        AiMessage secondAiMessageTemp = secondResponseTemp.content();
        assertThat(secondAiMessageTemp.text()).contains("25");
        assertThat(secondAiMessageTemp.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsageTemp = secondResponseTemp.tokenUsage();
        assertThat(secondTokenUsageTemp.inputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsageTemp.outputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsageTemp.totalTokenCount()).isEqualTo(secondTokenUsageTemp.inputTokenCount() + secondTokenUsageTemp.outputTokenCount());

        assertThat(secondResponseTemp.finishReason()).isEqualTo(STOP);
    }

    @Test
    void testMultipleFunctionCallingInParallelWithBedrockAnthropicV3SonnetChatModel() {

        BedrockAnthropicMessageChatModel bedrockChatModel = BedrockAnthropicMessageChatModel
                .builder()
                .temperature(0.00f)
                .maxTokens(300)
                .region(Region.US_EAST_1)
                .model(BedrockAnthropicMessageChatModel.Types.AnthropicClaude3SonnetV1.getValue())
                .maxRetries(1)
                .build();

        assertThat(bedrockChatModel).isNotNull();

        ToolSpecification calculator = ToolSpecification.builder()
                .name("calculator")
                .description("returns a sum of two numbers")
                .addParameter("first", INTEGER)
                .addParameter("second", INTEGER)
                .build();

        assertThat(calculator).isNotNull();

        List<ToolSpecification> toolSpecifications = singletonList(calculator);

        UserMessage userMessage = userMessage("How much is 2+2 and 3+3? Call tools in parallel!");

        Response<AiMessage> response = bedrockChatModel.generate(singletonList(userMessage), toolSpecifications);

        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(2);

        ToolExecutionRequest firstToolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(firstToolExecutionRequest.id()).isNotBlank();
        assertThat(firstToolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(firstToolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        ToolExecutionRequest secondToolExecutionRequest = aiMessage.toolExecutionRequests().get(1);
        assertThat(secondToolExecutionRequest.id()).isNotBlank();
        assertThat(secondToolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(secondToolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 3, \"second\": 3}");

        TokenUsage tokenUsageCalc = response.tokenUsage();
        assertThat(tokenUsageCalc.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsageCalc.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsageCalc.totalTokenCount())
                .isEqualTo(tokenUsageCalc.inputTokenCount() + tokenUsageCalc.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        ToolExecutionResultMessage firstToolExecutionResultMessageCalc = from(firstToolExecutionRequest, "4");
        ToolExecutionResultMessage secondToolExecutionResultMessageCalc = from(secondToolExecutionRequest, "6");
        List<ChatMessage> messages = asList(
                userMessage,
                aiMessage,
                firstToolExecutionResultMessageCalc,
                secondToolExecutionResultMessageCalc
        );

        sleepIfNeeded();
        Response<AiMessage> secondeResponse = bedrockChatModel.generate(messages, toolSpecifications);

        AiMessage secondAiMessage = secondeResponse.content();
        assertThat(secondAiMessage.text()).contains("4", "6");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsage = secondeResponse.tokenUsage();
        assertThat(secondTokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());

        assertThat(secondeResponse.finishReason()).isEqualTo(STOP);
    }

    @Test
    void testNoParametersFunctionCallingWithBedrockAnthropicV3SonnetChatModel() {

        BedrockAnthropicMessageChatModel bedrockChatModel = BedrockAnthropicMessageChatModel
                .builder()
                .temperature(0.0f)
                .maxTokens(300)
                .region(Region.US_EAST_1)
                .model(BedrockAnthropicMessageChatModel.Types.AnthropicClaude3SonnetV1.getValue())
                .maxRetries(1)
                .build();

        assertThat(bedrockChatModel).isNotNull();

        ToolSpecification currentDateTime = ToolSpecification.builder()
                .name("currentDateTime")
                .description("returns current date and time")
                .build();

        assertThat(currentDateTime).isNotNull();

        UserMessage userMessageCalc = UserMessage.from("Current date and time is = ?");

        Response<AiMessage> responseCalc = bedrockChatModel.generate(singletonList(userMessageCalc), singletonList(currentDateTime));

        AiMessage aiMessageCalc = responseCalc.content();
        assertThat(aiMessageCalc.text()).isNull();
        assertThat(aiMessageCalc.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequestCalc = aiMessageCalc.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequestCalc.id()).isNotBlank();
        assertThat(toolExecutionRequestCalc.name()).isEqualTo("currentDateTime");
        assertThat(toolExecutionRequestCalc.arguments()).isEqualToIgnoringWhitespace("{}");

        TokenUsage tokenUsageCalc = responseCalc.tokenUsage();
        assertThat(tokenUsageCalc.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsageCalc.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsageCalc.totalTokenCount()).isEqualTo(tokenUsageCalc.inputTokenCount() + tokenUsageCalc.outputTokenCount());

        assertThat(responseCalc.finishReason()).isEqualTo(TOOL_EXECUTION);

        String nowDateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        ToolExecutionResultMessage toolExecutionResultMessageCalc = from(toolExecutionRequestCalc, nowDateTime);
        List<ChatMessage> messagesCalc = asList(userMessageCalc, aiMessageCalc, toolExecutionResultMessageCalc);

        sleepIfNeeded();
        Response<AiMessage> secondResponseCalc = bedrockChatModel.generate(messagesCalc, singletonList(currentDateTime));

        AiMessage secondAiMessageCalc = secondResponseCalc.content();
        assertThat(secondAiMessageCalc.text()).contains(nowDateTime);
        assertThat(secondAiMessageCalc.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsageCalc = secondResponseCalc.tokenUsage();
        assertThat(secondTokenUsageCalc.inputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsageCalc.outputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsageCalc.totalTokenCount()).isEqualTo(secondTokenUsageCalc.inputTokenCount() + secondTokenUsageCalc.outputTokenCount());

        assertThat(secondResponseCalc.finishReason()).isEqualTo(STOP);
    }

    @Test
    void testToolChoiceFunctionCallingWithBedrockAnthropicV3SonnetChatModel() {
        BedrockAnthropicMessageChatModel bedrockChatModel = BedrockAnthropicMessageChatModel
                .builder()
                .temperature(0.0f)
                .maxTokens(300)
                .region(Region.US_EAST_1)
                .model(BedrockAnthropicMessageChatModel.Types.AnthropicClaude3SonnetV1.getValue())
                .maxRetries(1)
                .build();

        assertThat(bedrockChatModel).isNotNull();

        ToolSpecification calculator = ToolSpecification.builder()
                .name("calculator")
                .description("returns a sum of two numbers")
                .addParameter("first", INTEGER)
                .addParameter("second", INTEGER)
                .build();

        assertThat(calculator).isNotNull();

        UserMessage userMessage = UserMessage.from("2+2=?");

        Response<AiMessage> response = bedrockChatModel.generate(singletonList(userMessage), calculator);

        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.id()).isNotBlank();
        assertThat(toolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "4");
        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        sleepIfNeeded();
        Response<AiMessage> secondResponse = bedrockChatModel.generate(messages, calculator);

        AiMessage secondAiMessage = secondResponse.content();
        assertThat(secondAiMessage.text()).isNull();
        assertThat(secondAiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest secondToolExecutionRequest = secondAiMessage.toolExecutionRequests().get(0);
        assertThat(secondToolExecutionRequest.id()).isNotBlank();
        assertThat(secondToolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(secondToolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());

        assertThat(secondResponse.finishReason()).isEqualTo(TOOL_EXECUTION);
    }

    @Test
    void testFunctionCallingWithBedrockAnthropicChatModelWithoutToolsSupport() {
        BedrockAnthropicMessageChatModel bedrockChatModel = BedrockAnthropicMessageChatModel
                .builder()
                .temperature(0.00f)
                .maxTokens(300)
                .region(Region.US_EAST_1)
                .model(BedrockAnthropicMessageChatModel.Types.AnthropicClaudeV2_1.getValue())
                .maxRetries(1)
                .build();

        assertThat(bedrockChatModel).isNotNull();

        ToolSpecification calculator = ToolSpecification.builder()
                .name("calculator")
                .description("returns a sum of two numbers")
                .addParameter("first", INTEGER)
                .addParameter("second", INTEGER)
                .build();

        assertThat(calculator).isNotNull();

        UserMessage userMessage = UserMessage.from("2+2=?");

        IllegalArgumentException exception = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> bedrockChatModel.generate(singletonList(userMessage), singletonList(calculator)),
                "Expected generate() to throw, but it didn't"
        );

        assertThat(exception.getMessage()).isEqualTo("Tools are currently not supported by this model");
    }

    @ParameterizedTest
    @MethodSource("anthropicModelsWithoutToolSupport")
    void testValidateModelIdWithoutToolsSupport(String modelId) {
        IllegalArgumentException illegalArgumentException = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> BedrockAnthropicMessageChatModel.validateModelIdWithToolsSupport(modelId),
                "Expected validateModelIdWithToolsSupport() to throw, but it didn't"
        );

        assertThat(illegalArgumentException.getMessage()).isEqualTo("Tools are currently not supported by this model");
    }

    static Stream<String> anthropicModelsWithoutToolSupport() {
        return Stream.of(
                BedrockAnthropicMessageChatModel.Types.AnthropicClaudeInstantV1.getValue(),
                BedrockAnthropicMessageChatModel.Types.AnthropicClaudeV2.getValue(),
                BedrockAnthropicMessageChatModel.Types.AnthropicClaudeV2_1.getValue()
        );
    }

    @ParameterizedTest
    @MethodSource("anthropicModelsWithToolSupport")
    void testValidateModelIdWithToolsSupport(String modelId) {
        Assertions.assertDoesNotThrow(
                () -> BedrockAnthropicMessageChatModel.validateModelIdWithToolsSupport(modelId),
                "Expected validateModelIdWithToolsSupport() to not throw, but it did"
        );
    }

    static Stream<String> anthropicModelsWithToolSupport() {
        return Stream.of(
                BedrockAnthropicMessageChatModel.Types.AnthropicClaude3SonnetV1.getValue(),
                BedrockAnthropicMessageChatModel.Types.AnthropicClaude3_5SonnetV1.getValue(),
                BedrockAnthropicMessageChatModel.Types.AnthropicClaude3HaikuV1.getValue()
        );
    }

    @Test
    void testBedrockAnthropicV3HaikuChatModel() {

        BedrockAnthropicMessageChatModel bedrockChatModel = BedrockAnthropicMessageChatModel
                .builder()
                .temperature(0.50f)
                .maxTokens(300)
                .region(Region.US_EAST_1)
                .model(BedrockAnthropicMessageChatModel.Types.AnthropicClaude3HaikuV1.getValue())
                .maxRetries(1)
                .build();

        assertThat(bedrockChatModel).isNotNull();

        Response<AiMessage> response = bedrockChatModel.generate(UserMessage.from("hi, how are you doing?"));

        assertThat(response).isNotNull();
        assertThat(response.content().text()).isNotBlank();
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isIn(FinishReason.STOP, FinishReason.LENGTH);
    }

    @Test
    void testBedrockAnthropicV3HaikuChatModelImageContent() {

        BedrockAnthropicMessageChatModel bedrockChatModel = BedrockAnthropicMessageChatModel
                .builder()
                .temperature(0.50f)
                .maxTokens(300)
                .region(Region.US_EAST_1)
                .model(BedrockAnthropicMessageChatModel.Types.AnthropicClaude3HaikuV1.getValue())
                .maxRetries(1)
                .build();

        assertThat(bedrockChatModel).isNotNull();

        String base64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
        ImageContent imageContent = ImageContent.from(base64Data, "image/png");
        UserMessage userMessage = UserMessage.from(imageContent);

        Response<AiMessage> response = bedrockChatModel.generate(userMessage);

        assertThat(response).isNotNull();
        assertThat(response.content().text()).isNotBlank();
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isIn(FinishReason.STOP, FinishReason.LENGTH);
    }

    @Test
    void testBedrockAnthropicV2ChatModelEnumModelType() {

        BedrockAnthropicCompletionChatModel bedrockChatModel = BedrockAnthropicCompletionChatModel
                .builder()
                .temperature(0.50f)
                .maxTokens(300)
                .region(Region.US_EAST_1)
                .model(BedrockAnthropicCompletionChatModel.Types.AnthropicClaudeV2.getValue())
                .maxRetries(1)
                .build();

        assertThat(bedrockChatModel).isNotNull();

        Response<AiMessage> response = bedrockChatModel.generate(UserMessage.from("hi, how are you doing?"));

        assertThat(response).isNotNull();
        assertThat(response.content().text()).isNotBlank();
        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isIn(FinishReason.STOP, FinishReason.LENGTH);
    }

    @Test
    void testBedrockAnthropicV2ChatModelStringModelType() {

        BedrockAnthropicCompletionChatModel bedrockChatModel = BedrockAnthropicCompletionChatModel
                .builder()
                .temperature(0.50f)
                .maxTokens(300)
                .region(Region.US_EAST_1)
                .model("anthropic.claude-v2")
                .maxRetries(1)
                .build();

        assertThat(bedrockChatModel).isNotNull();

        Response<AiMessage> response = bedrockChatModel.generate(UserMessage.from("hi, how are you doing?"));

        assertThat(response).isNotNull();
        assertThat(response.content().text()).isNotBlank();
        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isIn(FinishReason.STOP, FinishReason.LENGTH);
    }

    @Test
    void testBedrockTitanChatModel() {

        BedrockTitanChatModel bedrockChatModel = BedrockTitanChatModel
                .builder()
                .temperature(0.50f)
                .maxTokens(300)
                .region(Region.US_EAST_1)
                .model(BedrockTitanChatModel.Types.TitanTextExpressV1.getValue())
                .maxRetries(1)
                .build();

        assertThat(bedrockChatModel).isNotNull();

        Response<AiMessage> response = bedrockChatModel.generate(UserMessage.from("hi, how are you doing?"));

        assertThat(response).isNotNull();
        assertThat(response.content().text()).isNotBlank();
        assertThat(response.tokenUsage()).isNotNull();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isIn(FinishReason.STOP, FinishReason.LENGTH);
    }

    @Test
    void testBedrockCohereChatModel() {

        BedrockCohereChatModel bedrockChatModel = BedrockCohereChatModel
                .builder()
                .temperature(0.50f)
                .maxTokens(300)
                .region(Region.US_EAST_1)
                .maxRetries(1)
                .build();

        assertThat(bedrockChatModel).isNotNull();

        Response<AiMessage> response = bedrockChatModel.generate(UserMessage.from("hi, how are you doing?"));

        assertThat(response).isNotNull();
        assertThat(response.content().text()).isNotBlank();
        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isIn(FinishReason.STOP, FinishReason.LENGTH);
    }

    @Test
    void testBedrockStabilityChatModel() {

        BedrockStabilityAIChatModel bedrockChatModel = BedrockStabilityAIChatModel
                .builder()
                .temperature(0.50f)
                .maxTokens(300)
                .region(Region.US_EAST_1)
                .stylePreset(BedrockStabilityAIChatModel.StylePreset.Anime)
                .maxRetries(1)
                .build();

        assertThat(bedrockChatModel).isNotNull();

        Response<AiMessage> response = bedrockChatModel.generate(UserMessage.from("Draw me a flower with any human in background."));

        assertThat(response).isNotNull();
        assertThat(response.content().text()).isNotBlank();
        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isIn(FinishReason.STOP, FinishReason.LENGTH);
    }

    @ParameterizedTest
    @EnumSource(value = BedrockLlamaChatModel.Types.class, mode = INCLUDE, names = {
            "META_LLAMA3_8B_INSTRUCT_V1_0",
            "META_LLAMA3_70B_INSTRUCT_V1_0"
    })
    void testBedrockLlamaChatModel(BedrockLlamaChatModel.Types modelId) {

        BedrockLlamaChatModel bedrockChatModel = BedrockLlamaChatModel
                .builder()
                .temperature(0.50f)
                .maxTokens(300)
                .region(Region.US_EAST_1)
                .model(modelId.getValue())
                .maxRetries(1)
                .build();

        assertThat(bedrockChatModel).isNotNull();

        Response<AiMessage> response = bedrockChatModel.generate(UserMessage.from("hi, how are you doing?"));

        assertThat(response).isNotNull();
        assertThat(response.content().text()).isNotBlank();
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isIn(FinishReason.STOP, FinishReason.LENGTH);
    }

    @Test
    void testBedrockMistralAi7bInstructChatModel() {

        BedrockMistralAiChatModel bedrockChatModel = BedrockMistralAiChatModel
                .builder()
                .temperature(0.50f)
                .maxTokens(300)
                .region(Region.US_EAST_1)
                .model(Mistral7bInstructV0_2.getValue())
                .maxRetries(1)
                .build();

        assertThat(bedrockChatModel).isNotNull();

        List<ChatMessage> messages = Arrays.asList(
                UserMessage.from("hi, how are you doing"),
                AiMessage.from("I am an AI model so I don't have feelings"),
                UserMessage.from("Ok no worries, tell me story about a man who wears a tin hat."));

        Response<AiMessage> response = bedrockChatModel.generate(messages);

        assertThat(response).isNotNull();
        assertThat(response.content().text()).isNotBlank();
        assertThat(response.finishReason()).isIn(FinishReason.STOP, FinishReason.LENGTH);
    }

    @Test
    void testBedrockMistralAiMixtral8x7bInstructChatModel() {

        BedrockMistralAiChatModel bedrockChatModel = BedrockMistralAiChatModel
                .builder()
                .temperature(0.50f)
                .maxTokens(300)
                .region(Region.US_EAST_1)
                .model(MistralMixtral8x7bInstructV0_1.getValue())
                .maxRetries(1)
                .build();

        assertThat(bedrockChatModel).isNotNull();

        Response<AiMessage> response = bedrockChatModel.generate(UserMessage.from("hi, how are you doing?"));

        assertThat(response).isNotNull();
        assertThat(response.content().text()).isNotBlank();
        assertThat(response.finishReason()).isIn(FinishReason.STOP, FinishReason.LENGTH);
    }

    @AfterEach
    void afterEach() {
        sleepIfNeeded();
    }

    private static void sleepIfNeeded() {
        try {
            String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_BEDROCK");
            if (ciDelaySeconds != null) {
                Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
