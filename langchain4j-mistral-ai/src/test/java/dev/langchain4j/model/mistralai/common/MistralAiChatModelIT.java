package dev.langchain4j.model.mistralai.common;

import static dev.langchain4j.model.mistralai.MistralAiChatModelName.MISTRAL_MEDIUM_LATEST;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.OPEN_MISTRAL_7B;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatResponseMetadata;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
class MistralAiChatModelIT extends AbstractChatModelIT {

    static final ChatModel MISTRAL_CHAT_MODEL = MistralAiChatModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .modelName(MISTRAL_MEDIUM_LATEST)
            .temperature(0.0)
            .logRequests(false) // images are huge in logs
            .logResponses(true)
            .build();

    @Override
    protected List<ChatModel> models() {
        return List.of(MISTRAL_CHAT_MODEL);
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        var mistralAiChatModelBuilder = MistralAiChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .defaultRequestParameters(parameters)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true);

        if (parameters.modelName() == null) {
            mistralAiChatModelBuilder.modelName(OPEN_MISTRAL_7B);
        }
        return mistralAiChatModelBuilder.build();
    }

    @Override
    protected String customModelName() {
        return "mistral-small-latest";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(ChatModel model) {
        return MistralAiChatResponseMetadata.class;
    }

    @Override
    protected String catImageUrl() {
        return "https://images.all-free-download.com/images/graphicwebp/cat_hangover_relax_213869.webp";
    }

    @Override
    protected String diceImageUrl() {
        return "https://images.all-free-download.com/images/graphicwebp/double_six_dice_196084.webp";
    }
}
