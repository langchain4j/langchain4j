package dev.langchain4j.model.openai.common;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.common.StreamingMode;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Set;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_5_MINI;
import static dev.langchain4j.model.openai.common.OpenAiChatModelIT.adjustForGpt5;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiStreamingChatModelIT extends AbstractStreamingChatModelIT {

    public static OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder defaultStreamingModelBuilder() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_5_MINI)
                .logRequests(false) // base64-encoded images are huge in logs
                .logResponses(true);
    }

    @Override
    protected List<StreamingChatModel> baseModels() {
        return List.of(
                defaultStreamingModelBuilder()
                        .build(),
                defaultStreamingModelBuilder()
                        .strictJsonSchema(true)
                        .build(),
                defaultStreamingModelBuilder()
                        .responseFormat("json_schema") // testing backward compatibility
                        .strictJsonSchema(true)
                        .build()
                // TODO json_object?
        );
    }

    @Override
    protected List<StreamingMode> streamingModes() {
        return List.of(StreamingMode.HANDLER, StreamingMode.PUBLISHER);
    }

    @Override
    protected List<StreamingChatModel> baseModelsSupportingTools() {
        return List.of(
                defaultStreamingModelBuilder()
                        .modelName(GPT_5_MINI)
                        .build(),
                defaultStreamingModelBuilder()
                        .modelName(GPT_5_MINI)
                        .strictTools(true)
                        .build()
        );
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .defaultRequestParameters(adjustForGpt5(parameters))
                .modelName(getOrDefault(parameters.modelName(), GPT_5_MINI.toString()))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Override
    protected int maxOutputTokens() {
        return 1000;
    }

    @Override
    protected ChatRequestParameters createParameters(int maxOutputTokens) {
        // GPT-5 does not support maxOutputTokens, need to use maxCompletionTokens instead
        return createIntegrationSpecificParameters(maxOutputTokens);
    }

    @Override
    protected void assertOutputTokenCount(TokenUsage tokenUsage, Integer maxOutputTokens) {
        OpenAiTokenUsage openAiTokenUsage = (OpenAiTokenUsage) tokenUsage;
        assertThat(tokenUsage.outputTokenCount() - openAiTokenUsage.outputTokensDetails().reasoningTokens())
                .isLessThanOrEqualTo(maxOutputTokens);
    }

    @Override
    protected Set<FinishReason> finishReasonForMaxOutputTokens() {
        return Set.of(LENGTH, STOP); // It is hard to make GPT-5 to hit LENGTH because of unpredictable reasoning
    }

    @Override
    protected String customModelName() {
        return "gpt-5-nano-2025-08-07";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return OpenAiChatRequestParameters.builder()
                .maxCompletionTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(StreamingChatModel streamingChatModel) {
        return OpenAiChatResponseMetadata.class;
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(StreamingChatModel streamingChatModel) {
        return OpenAiTokenUsage.class;
    }

    @Override
    protected boolean supportsStopSequencesParameter() {
        return false; // GPT-5 does not support stop sequences
    }

    @Override
    protected void should_fail_if_stopSequences_parameter_is_not_supported(StreamingChatModel model) {
        // GPT-5 does not support stop sequences, but other models do support
    }

    @Override
    protected String catImageUrl() {
        return "https://images.all-free-download.com/images/graphicwebp/cat_hangover_relax_213869.webp";
    }

    @Override
    protected String diceImageUrl() {
        return "https://images.all-free-download.com/images/graphicwebp/double_six_dice_196084.webp";
    }

    @Override
    protected ChatRequestParameters saveTokens(ChatRequestParameters parameters) {
        return parameters.overrideWith(OpenAiChatRequestParameters.builder().reasoningEffort("low").build());
    }

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        return defaultStreamingModelBuilder()
                .listeners(List.of(listener))
                .build();
    }
}
