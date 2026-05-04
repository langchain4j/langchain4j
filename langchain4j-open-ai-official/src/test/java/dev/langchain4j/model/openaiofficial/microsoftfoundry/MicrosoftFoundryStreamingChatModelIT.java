package dev.langchain4j.model.openaiofficial.microsoftfoundry;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatResponseMetadata;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialStreamingChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;

import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@EnabledIfEnvironmentVariable(named = "MICROSOFT_FOUNDRY_API_KEY", matches = ".+")
class MicrosoftFoundryStreamingChatModelIT extends AbstractStreamingChatModelIT {

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(
                OpenAiOfficialStreamingChatModel.builder()
                        .baseUrl(System.getenv("MICROSOFT_FOUNDRY_ENDPOINT"))
                        .apiKey(System.getenv("MICROSOFT_FOUNDRY_API_KEY"))
                        .modelName("gpt-4o-mini")
                        .build()
        );
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        return OpenAiOfficialStreamingChatModel.builder()
                .baseUrl(System.getenv("MICROSOFT_FOUNDRY_ENDPOINT"))
                .apiKey(System.getenv("MICROSOFT_FOUNDRY_API_KEY"))
                .microsoftFoundryDeploymentName("gpt-4o")
                .modelName(getOrDefault(parameters.modelName(), "gpt-4o"))
                .defaultRequestParameters(parameters)
                .build();
    }

    @Override
    protected String customModelName() {
        return "gpt-4o";
    }

    @Override
    protected boolean supportsModelNameParameter() {
        return false; // can't change model name after creating OpenAiOfficialStreamingChatModel instance
    }

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        return OpenAiOfficialStreamingChatModel.builder()
                .baseUrl(System.getenv("MICROSOFT_FOUNDRY_ENDPOINT"))
                .apiKey(System.getenv("MICROSOFT_FOUNDRY_API_KEY"))
                .modelName("gpt-4o-mini")
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return OpenAiOfficialChatRequestParameters.builder()
                .maxCompletionTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(StreamingChatModel streamingChatModel) {
        return OpenAiOfficialChatResponseMetadata.class;
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(StreamingChatModel streamingChatModel) {
        return OpenAiOfficialTokenUsage.class;
    }

    @Override
    protected int maxOutputTokens() {
        return 10;
    }

    @Override
    protected ChatRequestParameters saveTokens(ChatRequestParameters parameters) {
        return parameters.overrideWith(OpenAiOfficialChatRequestParameters.builder()
                .maxCompletionTokens(1)
                .build());
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id) {
        io.verify(handler).onPartialToolCall(eq(partial(0, id, "getWeather", "{\"")), any());
        io.verify(handler).onPartialToolCall(eq(partial(0, id, "getWeather", "city")), any());
        io.verify(handler).onPartialToolCall(eq(partial(0, id, "getWeather", "\":\"")), any());
        io.verify(handler).onPartialToolCall(eq(partial(0, id, "getWeather", "Mun")), any());
        io.verify(handler).onPartialToolCall(eq(partial(0, id, "getWeather", "ich")), any());
        io.verify(handler).onPartialToolCall(eq(partial(0, id, "getWeather", "\"}")), any());
        io.verify(handler).onCompleteToolCall(complete(0, id, "getWeather", "{\"city\":\"Munich\"}"));
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id1, String id2) {
        io.verify(handler).onPartialToolCall(eq(partial(0, id1, "getWeather", "{\"ci")), any());
        io.verify(handler).onPartialToolCall(eq(partial(0, id1, "getWeather", "ty\": ")), any());
        io.verify(handler).onPartialToolCall(eq(partial(0, id1, "getWeather", "\"Munic")), any());
        io.verify(handler).onPartialToolCall(eq(partial(0, id1, "getWeather", "h\"}")), any());
        io.verify(handler).onCompleteToolCall(complete(0, id1, "getWeather", "{\"city\": \"Munich\"}"));

        io.verify(handler).onPartialToolCall(eq(partial(1, id2, "getTime", "{\"co")), any());
        io.verify(handler).onPartialToolCall(eq(partial(1, id2, "getTime", "untry")), any());
        io.verify(handler).onPartialToolCall(eq(partial(1, id2, "getTime", "\": \"Fr")), any());
        io.verify(handler).onPartialToolCall(eq(partial(1, id2, "getTime", "ance")), any());
        io.verify(handler).onPartialToolCall(eq(partial(1, id2, "getTime", "\"}")), any());
        io.verify(handler).onCompleteToolCall(complete(1, id2, "getTime", "{\"country\": \"France\"}"));
    }

    @Disabled(
            "TODO fix: com.openai.errors.RateLimitException: 429: Requests to the ChatCompletions_Create Operation under Microsoft Foundry API version 2024-10-21 have exceeded token rate limit of your current OpenAI S0 pricing tier.")
    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    @EnabledIf("supportsSingleImageInputAsPublicURL")
    protected void should_accept_single_image_as_public_URL(StreamingChatModel model) {}

    @Disabled(
            "TODO fix: com.openai.errors.RateLimitException: 429: Requests to the ChatCompletions_Create Operation under Microsoft Foundry API version 2024-10-21 have exceeded token rate limit of your current OpenAI S0 pricing tier.")
    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    @EnabledIf("supportsMultipleImageInputsAsPublicURLs")
    protected void should_accept_multiple_images_as_public_URLs(StreamingChatModel model) {}

    @Disabled("Unsupported parameter: 'max_tokens' is not supported with this model. Use 'max_completion_tokens' instead.")
    @Override
    protected void should_respect_maxOutputTokens_in_default_model_parameters() {}

    @Disabled("Unsupported parameter: 'stop' is not supported with this model.")
    @Override
    protected void should_respect_stopSequences_in_default_model_parameters() {}
}
