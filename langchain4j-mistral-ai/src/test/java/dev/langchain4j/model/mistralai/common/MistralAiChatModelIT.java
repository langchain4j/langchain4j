package dev.langchain4j.model.mistralai.common;

import static dev.langchain4j.model.mistralai.MistralAiChatModelName.MISTRAL_LARGE_LATEST;
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
            .modelName(MISTRAL_LARGE_LATEST)
            // Without a cap, some tool-calling prompts send the model into a repetition loop that runs
            // until it errors out, burning thousands of output tokens. 2000 is far above what any
            // passing test needs.
            .maxTokens(2000)
            .temperature(0.0)
            .strictJsonSchema(true)
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
        // Must differ from the model above, and must be a name Mistral echoes back unchanged:
        // retired ids such as mistral-medium-2508 are silently served by their successor.
        return "ministral-8b-latest";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(ChatModel model) {
        return MistralAiChatResponseMetadata.class;
    }
}
