package dev.langchain4j.model.github;

import static com.azure.ai.inference.ModelServiceVersion.V2024_08_01_PREVIEW;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.github.GitHubModelsChatModelName.GPT_4_O_MINI;

import java.util.List;
import java.util.Set;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithJsonSchemaIT;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;


@EnabledIfEnvironmentVariable(named = "GITHUB_TOKEN", matches = ".+")
public class GitHubModelsChatModelWithJsonSchemaIT extends AbstractAiServiceWithJsonSchemaIT {

    GitHubModelsChatModel modelWithStrictJsonSchema = GitHubModelsChatModel.builder()
            .endpoint("https://models.inference.ai.azure.com")
            .gitHubToken(System.getenv("GITHUB_TOKEN"))
            .modelName(GPT_4_O_MINI)
            .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
            .strictJsonSchema(true)
            .serviceVersion(V2024_08_01_PREVIEW)
            .build();


    @Override
    protected List<ChatModel> models() {
        return List.of(
                modelWithStrictJsonSchema,
                GitHubModelsChatModel.builder()
                        .endpoint("https://models.inference.ai.azure.com")
                        .gitHubToken(System.getenv("GITHUB_TOKEN"))
                        .modelName(GPT_4_O_MINI)
                        .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
                        .serviceVersion(V2024_08_01_PREVIEW)
                        .build()
        );
    }

    @Override
    protected boolean supportsRecursion() {
        return true;
    }

    @Override
    protected boolean isStrictJsonSchemaEnabled(ChatModel model) {
        return model == modelWithStrictJsonSchema;
    }

}
