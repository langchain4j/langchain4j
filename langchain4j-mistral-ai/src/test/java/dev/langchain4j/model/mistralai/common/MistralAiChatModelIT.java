package dev.langchain4j.model.mistralai.common;

import static dev.langchain4j.model.mistralai.MistralAiChatModelName.MISTRAL_SMALL_LATEST;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.OPEN_MISTRAL_7B;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.OPEN_MIXTRAL_8X22B;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import java.util.List;

class MistralAiChatModelIT extends AbstractChatModelIT {

    static final ChatModel MISTRAL_CHAT_MODEL = MistralAiChatModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .modelName(MISTRAL_SMALL_LATEST)
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
        return "open-mixtral-8x22b";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return ChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }
}
