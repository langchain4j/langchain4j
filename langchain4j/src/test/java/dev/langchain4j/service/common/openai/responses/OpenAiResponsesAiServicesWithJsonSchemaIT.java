package dev.langchain4j.service.common.openai.responses;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModelAdapter;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithJsonSchemaIT;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

// TODO move to langchain4j-open-ai module once dependency cycle is resolved
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiResponsesAiServicesWithJsonSchemaIT extends AbstractAiServiceWithJsonSchemaIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(
                syncModel(true),
                syncModel(false),
                streamingModel(true),
                streamingModel(false)
        );
    }

    private static ChatModel syncModel(boolean strictTools) {
        return OpenAiResponsesChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-5.4-mini")
                .temperature(0.0)
                .strictTools(strictTools)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    private static ChatModel streamingModel(boolean strictJsonSchema) {
        StreamingChatModel streamingModel = OpenAiResponsesStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-5.4-mini")
                .temperature(0.0)
                .strictJsonSchema(strictJsonSchema)
                .logRequests(true)
                .logResponses(true)
                .build();
        return StreamingChatModelAdapter.adapt(streamingModel);
    }

    @Override
    protected boolean supportsRecursion() {
        return true;
    }

    @Override
    protected boolean isStrictJsonSchemaEnabled(ChatModel model) {
        return model.defaultRequestParameters() instanceof OpenAiResponsesChatRequestParameters parameters
                && Boolean.TRUE.equals(parameters.strictJsonSchema());
    }
}
