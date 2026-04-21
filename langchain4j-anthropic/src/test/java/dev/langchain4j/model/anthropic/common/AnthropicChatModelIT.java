package dev.langchain4j.model.anthropic.common;

import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_HAIKU_4_5_20251001;

import java.util.Base64;
import java.util.List;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatResponseMetadata;
import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicChatModelIT extends AbstractChatModelIT {

    static final ChatModel ANTHROPIC_CHAT_MODEL = AnthropicChatModel.builder()
            .baseUrl(System.getenv("ANTHROPIC_CACHING_BASE_URL"))
            .apiKey(System.getenv("ANTHROPIC_API_KEY"))
            .modelName(CLAUDE_HAIKU_4_5_20251001)
            .temperature(0.0)
            .logRequests(false) // images are huge in logs
            .logResponses(true)
            .build();

    static final ChatModel ANTHROPIC_SCHEMA_MODEL = AnthropicChatModel.builder()
            .baseUrl(System.getenv("ANTHROPIC_CACHING_BASE_URL"))
            .apiKey(System.getenv("ANTHROPIC_API_KEY"))
            .modelName(CLAUDE_HAIKU_4_5_20251001)
            .temperature(0.0)
            .beta("structured-outputs-2025-11-13")
            .logRequests(false)
            .logResponses(true)
            .build();

    @Override
    protected List<ChatModel> models() {
        return List.of(ANTHROPIC_CHAT_MODEL);
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        var anthropicChatModelBuilder = AnthropicChatModel.builder()
                .baseUrl(System.getenv("ANTHROPIC_CACHING_BASE_URL"))
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .defaultRequestParameters(parameters)
                .logRequests(true)
                .logResponses(true);
        if (parameters.modelName() == null) {
            anthropicChatModelBuilder.modelName(CLAUDE_HAIKU_4_5_20251001);
        }
        return anthropicChatModelBuilder.build();
    }

    @Override
    protected String customModelName() {
        return "claude-sonnet-4-5-20250929";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return ChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(ChatModel chatModel) {
        return AnthropicTokenUsage.class;
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(ChatModel model) {
        return AnthropicChatResponseMetadata.class;
    }

    @Override
    protected List<ChatModel> modelsSupportingStructuredOutputs() {
        return List.of(ANTHROPIC_SCHEMA_MODEL);
    }

    @Override
    protected boolean supportsJsonResponseFormat() {
        // Anthropic does not support JSON response format without a schema yet
        return false;
    }

    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingStructuredOutputs")
    @EnabledIf("supportsToolsAndJsonResponseFormatWithSchema")
    protected void should_execute_a_tool_then_answer_respecting_JSON_response_format_with_schema(ChatModel model) {
        // Anthropic structured outputs are a public beta and only supported by
        // Claude Sonnet 4.5, Opus 4.1/4.5, and Haiku 4.5 when the
        // 'structured-outputs-2025-11-13' beta header is enabled.
        super.should_execute_a_tool_then_answer_respecting_JSON_response_format_with_schema(model);
    }

    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingStructuredOutputs")
    @EnabledIf("supportsJsonResponseFormatWithRawSchema")
    protected void should_respect_JsonRawSchema_responseFormat(ChatModel model) {
        // Anthropic structured outputs are a public beta and only supported by
        // Claude Sonnet 4.5, Opus 4.1/4.5, and Haiku 4.5 when the
        // 'structured-outputs-2025-11-13' beta header is enabled.
        super.should_respect_JsonRawSchema_responseFormat(model);
    }

    @Override
    protected String catImageUrl() {
        return "https://images.all-free-download.com/images/graphicwebp/cat_hangover_relax_213869.webp";
    }

    @Override
    protected ImageContent catImageContentBase64() {
        String base64Data = Base64.getEncoder().encodeToString(readBytes(catImageUrl()));
        return ImageContent.from(base64Data, "image/webp");
    }

    @Override
    protected String diceImageUrl() {
        return "https://images.all-free-download.com/images/graphicwebp/double_six_dice_196084.webp";
    }

    @Override
    protected ImageContent diceImageContentBase64() {
        String base64Data = Base64.getEncoder().encodeToString(readBytes(diceImageUrl()));
        return ImageContent.from(base64Data, "image/webp");
    }
}
