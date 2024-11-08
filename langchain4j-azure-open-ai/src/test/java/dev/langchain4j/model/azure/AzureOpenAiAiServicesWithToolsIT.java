package dev.langchain4j.model.azure;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesWithNewToolsIT;

import java.util.List;

import static java.util.Collections.singletonList;

class AzureOpenAiAiServicesWithToolsIT extends AiServicesWithNewToolsIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return singletonList(
                AzureOpenAiChatModel.builder()
                        .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                        .deploymentName("gpt-4o-mini")
                        .temperature(0.0)
                        .logRequestsAndResponses(true)
                        .build()
        );
    }
}
