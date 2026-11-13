package dev.langchain4j.model.watsonx.it;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.watsonx.WatsonxChatResponseMetadata;
import dev.langchain4j.model.watsonx.WatsonxGatewayChatModel;
import dev.langchain4j.model.watsonx.WatsonxGatewayChatRequestParameters;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_GATEWAY_MODEL", matches = ".+")
public class WatsonxGatewayChatModelIT extends AbstractChatModelIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String URL = System.getenv("WATSONX_URL");
    static final String MODEL = System.getenv("WATSONX_GATEWAY_MODEL");
    static final String CUSTOM_MODEL = getOrDefault(System.getenv("WATSONX_GATEWAY_CUSTOM_MODEL"), "claude-sonnet-5");
    static final String STOP_SEQUENCES_MODEL =
            getOrDefault(System.getenv("WATSONX_GATEWAY_STOP_SEQUENCES_MODEL"), "gemini-3.6-flash");

    @Override
    protected List<ChatModel> models() {
        return List.of(createChatModel(MODEL).build());
    }

    @Override
    protected String customModelName() {
        return CUSTOM_MODEL;
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        String defaultModel = isNullOrEmpty(parameters.stopSequences()) ? MODEL : STOP_SEQUENCES_MODEL;
        return createChatModel(getOrDefault(parameters.modelName(), defaultModel))
                .defaultRequestParameters(parameters)
                .build();
    }

    @Override
    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsStopSequencesParameter")
    protected void should_respect_stopSequences_in_chat_request(ChatModel model) {
        super.should_respect_stopSequences_in_chat_request(
                createChatModel(STOP_SEQUENCES_MODEL).build());
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return WatsonxGatewayChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(ChatModel model) {
        return WatsonxChatResponseMetadata.class;
    }

    @Override
    public boolean supportsSingleImageInputAsPublicURL() {
        // Watsonx does not support images as URLs, only as Base64-encoded strings
        return false;
    }

    private WatsonxGatewayChatModel.Builder createChatModel(String model) {
        return WatsonxGatewayChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .modelName(model)
                .timeout(Duration.ofSeconds(120));
    }
}
