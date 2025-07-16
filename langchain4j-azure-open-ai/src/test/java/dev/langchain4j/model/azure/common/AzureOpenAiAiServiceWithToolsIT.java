package dev.langchain4j.model.azure.common;

import static java.util.Collections.singletonList;

import dev.langchain4j.model.azure.AzureModelBuilders;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithToolsIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
class AzureOpenAiAiServiceWithToolsIT extends AbstractAiServiceWithToolsIT {

    @Override
    protected List<ChatModel> models() {
        return singletonList(
                AzureModelBuilders.chatModelBuilder().temperature(0.0).build());
    }
}
