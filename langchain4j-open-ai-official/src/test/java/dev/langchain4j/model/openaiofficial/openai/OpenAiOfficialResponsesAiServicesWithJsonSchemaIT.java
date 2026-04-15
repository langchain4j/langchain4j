package dev.langchain4j.model.openaiofficial.openai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModelAdapter;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithJsonSchemaIT;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialResponsesAiServicesWithJsonSchemaIT extends AbstractAiServiceWithJsonSchemaIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(
                createModel(true),
                createModel(false)
        );
    }

    private static ChatModel createModel(boolean strictJsonSchema) {
        StreamingChatModel streamingModel = OpenAiOfficialResponsesStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-5.4-mini")
                .temperature(0.0)
                .strictJsonSchema(strictJsonSchema)
                .build();
        return StreamingChatModelAdapter.adapt(streamingModel);
    }

    @Override
    protected boolean supportsRecursion() {
        return true;
    }

    @Override
    protected boolean isStrictJsonSchemaEnabled(ChatModel model) {
        return model.defaultRequestParameters() instanceof OpenAiOfficialResponsesChatRequestParameters parameters
                && Boolean.TRUE.equals(parameters.strictJsonSchema());
    }
}
