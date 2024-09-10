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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;

import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.INTEGER;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.bedrock.BedrockMistralAiChatModel.Types.Mistral7bInstructV0_2;
import static dev.langchain4j.model.bedrock.BedrockMistralAiChatModel.Types.MistralMixtral8x7bInstructV0_1;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

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
                .temperature(0.50f)
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

        Response<AiMessage> secondResponse = bedrockChatModel.generate(messages, calculator);

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

    @Test
    void testBedrockLlama13BChatModel() {

        BedrockLlamaChatModel bedrockChatModel = BedrockLlamaChatModel
                .builder()
                .temperature(0.50f)
                .maxTokens(300)
                .region(Region.US_EAST_1)
                .model(BedrockLlamaChatModel.Types.MetaLlama2Chat13B.getValue())
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
    void testBedrockLlama70BChatModel() {

        BedrockLlamaChatModel bedrockChatModel = BedrockLlamaChatModel
                .builder()
                .temperature(0.50f)
                .maxTokens(300)
                .region(Region.US_EAST_1)
                .model(BedrockLlamaChatModel.Types.MetaLlama2Chat70B.getValue())
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
}
