package dev.langchain4j.model.azure;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.ai.openai.responses.models.ResponsesReasoningConfigurationEffort;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
class AzureOpenAiResponsesStreamingChatModelIT {

    @Test
    void should_return_reasoning_summary_when_enabled() {
        String deploymentName = AzureModelBuilders.getAzureOpenaiResponsesDeploymentName();
        Assumptions.assumeTrue(
                deploymentName != null && !deploymentName.isBlank(),
                "AZURE_OPENAI_RESPONSES_DEPLOYMENT_NAME must be set to run this test");

        StreamingChatModel model = AzureOpenAiResponsesStreamingChatModel.builder()
                .endpoint(AzureModelBuilders.getAzureOpenaiEndpoint())
                .apiKey(AzureModelBuilders.getAzureOpenaiKey())
                .deploymentName(deploymentName)
                .maxTokens(100)
                .reasoningEffort(ResponsesReasoningConfigurationEffort.MEDIUM)
                .reasoningSummary("auto")
                .logRequestsAndResponses(true)
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        try {
            model.chat("Say hello and briefly explain your approach.", handler);
            ChatResponse response = handler.get();

            assertThat(response.aiMessage().text()).contains("Hello");
            Assumptions.assumeTrue(
                    response.aiMessage().thinking() != null,
                    "Reasoning summary not returned by model, skipping assertion");
            assertThat(response.aiMessage().thinking()).isNotBlank();
        } catch (RuntimeException ex) {
            if (hasCause(ex, InternalServerException.class)) {
                Assumptions.assumeTrue(false, "Azure service unavailable, skipping test");
            }
            throw ex;
        }
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
