package dev.langchain4j.service.common.openai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithToolsIT;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

// TODO move to langchain4j-open-ai module once cyclic dependency is resolved
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiAiServiceWithToolsIT extends AbstractAiServiceWithToolsIT {

    @Override
    protected List<ChatModel> models() {
        return asList(
                OpenAiChatModel.builder()
                        .baseUrl(System.getenv("OPENAI_BASE_URL"))
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                        .modelName(GPT_4_O_MINI)
                        .temperature(0.0)
                        .logRequests(true)
                        .logResponses(true)
                        .build(),
                OpenAiChatModel.builder()
                        .baseUrl(System.getenv("OPENAI_BASE_URL"))
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                        .modelName(GPT_4_O_MINI)
                        .strictTools(true)
                        .temperature(0.0)
                        .logRequests(true)
                        .logResponses(true)
                        .build()
        );
    }

    @Override
    protected List<ChatModel> modelsSupportingMapParametersInTools() {
        return singletonList(models().get(0)); // second model (with strictTools(true)) goes into an endless loop
    }

    @Override
    protected boolean supportsRecursion() {
        return true;
    }

    @Override
    protected boolean verifyModelInteractions() {
        return true;
    }
}
