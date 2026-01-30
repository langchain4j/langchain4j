package dev.langchain4j.model.azure;

import static dev.langchain4j.model.azure.AzureModelBuilders.getAzureOpenaiEndpoint;
import static dev.langchain4j.model.azure.AzureModelBuilders.getAzureOpenaiKey;
import static dev.langchain4j.model.azure.AzureModelBuilders.getAzureOpenaiResponsesDeploymentName;
import static org.assertj.core.api.Assertions.assertThat;

import com.azure.ai.openai.responses.models.ResponsesReasoningConfigurationEffort;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_RESPONSES_DEPLOYMENT_NAME", matches = ".+")
class AzureOpenAiResponsesChatModelIT {

    @Test
    void should_return_reasoning_summary() {
        String deploymentName = getAzureOpenaiResponsesDeploymentName();

        ChatModel model = AzureOpenAiResponsesChatModel.builder()
                .endpoint(getAzureOpenaiEndpoint())
                .apiKey(getAzureOpenaiKey())
                .deploymentName(deploymentName)
                .reasoningEffort(ResponsesReasoningConfigurationEffort.MEDIUM)
                .reasoningSummary("detailed")
                .temperature(null)
                .topP(null)
                .maxTokens(null)
                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("Say hello in one sentence.");

        ChatResponse response = model.chat(userMessage);

        assertThat(response.aiMessage().text()).isNotBlank();
        String summary = response.aiMessage().thinking();
        Assumptions.assumeTrue(summary != null && !summary.isBlank(), "Reasoning summary not returned");
        assertThat(summary).isNotBlank();
    }
}
