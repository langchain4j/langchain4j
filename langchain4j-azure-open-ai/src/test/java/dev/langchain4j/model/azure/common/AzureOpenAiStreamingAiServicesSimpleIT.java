package dev.langchain4j.model.azure.common;

import dev.langchain4j.model.azure.AzureOpenAiStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.common.StreamingAiServicesSimpleIT;

import java.util.List;

class AzureOpenAiStreamingAiServicesSimpleIT extends StreamingAiServicesSimpleIT {

    @Override
    protected List<StreamingChatLanguageModel> models() {
        return List.of(
                AzureOpenAiStreamingChatModel.builder()
                        .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                        .deploymentName("gpt-4o-mini")
                        .build()
        );
    }
}
