package dev.langchain4j.model.azure.common;

import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.common.AbstractAiServiceWithJsonSchemaIT;
import org.junit.jupiter.api.AfterEach;

import java.util.List;
import java.util.Set;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

class AzureOpenAiAiServiceWithJsonSchemaIT extends AbstractAiServiceWithJsonSchemaIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(
                AzureOpenAiChatModel.builder()
                        .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                        .deploymentName("gpt-4o-mini")
                        .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
                        .strictJsonSchema(true)
                        .temperature(0.0)
                        .logRequestsAndResponses(true)
                        .build(),
                AzureOpenAiChatModel.builder()
                        .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                        .deploymentName("gpt-4o-mini")
                        .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
                        .strictJsonSchema(false)
                        .temperature(0.0)
                        .logRequestsAndResponses(true)
                        .build()
        );
    }

    @Override
    protected boolean supportsRecursion() {
        return true;
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_AZURE_OPENAI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
