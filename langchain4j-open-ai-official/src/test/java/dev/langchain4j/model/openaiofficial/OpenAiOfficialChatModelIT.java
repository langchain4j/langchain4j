package dev.langchain4j.model.openaiofficial;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.azure.credential.AzureApiKeyCredential;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatCompletionCreateParams;
import com.openai.models.ChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
public class OpenAiOfficialChatModelIT {

    OpenAiOfficialChatModel model = OpenAiOfficialChatModel.builder()
            .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
            .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
            .azureDeploymentName(ChatModel.GPT_4O_MINI.toString())
            .azureOpenAIServiceVersion(AzureOpenAIServiceVersion.getV2024_02_15_PREVIEW())
            .modelName(ChatModel.GPT_4O_MINI)
            .temperature(0.0)
            .build();

    /**
     * This uses the OpenAI SDK directly as in https://github.com/openai/openai-java/blob/main/README.md
     * It is used to check if the underlying API is working correctly, as this module is built on it.
     */
    @Test
    void openai_sdk_direct_usage_should_work_with_azure_openai() {

        String baseUrl = System.getenv("AZURE_OPENAI_ENDPOINT") + "/openai/deployments/gpt-4o-mini?api-version=2024-08-01-preview";

        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl)
                .credential(AzureApiKeyCredential.create(System.getenv("AZURE_OPENAI_KEY")))
                .build();

        ChatCompletionCreateParams createParams = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_4O_MINI)
                .maxCompletionTokens(2048)
                .addUserMessage("What is the capital of France?")
                .build();

        client.chat().completions().create(createParams).choices().stream()
                .flatMap(choice -> choice.message().content().stream())
                .forEach(System.out::println);
    }

    @Test
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop() {

        // given
        UserMessage userMessage = userMessage("What is the capital of France?");

        // when
        Response<AiMessage> response = model.generate(userMessage);

        // then
        assertThat(response.content().text()).contains("Paris");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(14);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_respect_maxCompletionTokens() {

        // given
        int maxCompletionTokens = 1;

        ChatLanguageModel model = OpenAiOfficialChatModel.builder()
                .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
                .azureDeploymentName(ChatModel.GPT_4O_MINI.toString())
                .azureOpenAIServiceVersion(AzureOpenAIServiceVersion.getV2024_02_15_PREVIEW())
                .modelName(ChatModel.GPT_4O_MINI)
                .maxCompletionTokens(maxCompletionTokens)
                .temperature(0.0)
                .build();

        UserMessage userMessage = userMessage("Tell me a long story");

        // when
        Response<AiMessage> response = model.generate(userMessage);

        // then
        assertThat(response.content().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(maxCompletionTokens);

        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }
}
