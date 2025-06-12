package dev.langchain4j.service.common.openai;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.common.AbstractAiServiceIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// TODO move to langchain4j-open-ai module once dependency cycle is resolved
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiAiServiceIT extends AbstractAiServiceIT {

    private static OpenAiChatModel.OpenAiChatModelBuilder defaultModelBuilder() {
        return OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .logRequests(true)
                .logResponses(true);
    }

    @Override
    protected List<ChatModel> models() {
        return List.of(
                defaultModelBuilder().build()
                // TODO more configs?
                );
    }

    @Override
    protected List<ChatModel> modelsSupportingToolsAndJsonResponseFormatWithSchema() {
        return List.of(
                defaultModelBuilder()
                        .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                        .strictJsonSchema(true)
                        .build(),
                defaultModelBuilder()
                        .responseFormat("json_schema") // testing backward compatibility
                        .strictJsonSchema(true)
                        .build(),
                defaultModelBuilder()
                        .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                        .strictJsonSchema(false)
                        .build(),
                defaultModelBuilder()
                        .responseFormat("json_schema") // testing backward compatibility
                        .strictJsonSchema(false)
                        .build()
                // TODO more configs?
                );
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(ChatModel chatModel) {
        return OpenAiTokenUsage.class;
    }
}
