package dev.langchain4j.model.openaiofficial.setup;

import static dev.langchain4j.model.openaiofficial.azureopenai.InternalAzureOpenAiOfficialTestHelper.CHAT_MODEL_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.client.OpenAIClient;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

public class OpenAiOfficialSetupIT {

    @Test
    void detectModelProvider_returnsAzureOpenAI_whenAzureFlagIsTrue() {
        ModelProvider result = OpenAiOfficialSetup.detectModelProvider(true, false, null, null, null);

        assertEquals(ModelProvider.AZURE_OPEN_AI, result);
    }

    @Test
    void detectModelProvider_returnsGitHubModels_whenGitHubFlagIsTrue() {
        ModelProvider result = OpenAiOfficialSetup.detectModelProvider(false, true, null, null, null);

        assertEquals(ModelProvider.GITHUB_MODELS, result);
    }

    @Test
    void detectModelProvider_returnsAzureOpenAI_whenBaseUrlMatchesAzure() {
        ModelProvider result =
                OpenAiOfficialSetup.detectModelProvider(false, false, "https://example.openai.azure.com", null, null);

        assertEquals(ModelProvider.AZURE_OPEN_AI, result);
    }

    @Test
    void detectModelProvider_returnsGitHubModels_whenBaseUrlMatchesGitHub() {
        ModelProvider result =
                OpenAiOfficialSetup.detectModelProvider(false, false, "https://models.github.ai/inference", null, null);

        assertEquals(ModelProvider.GITHUB_MODELS, result);
    }

    @Test
    void detectModelProvider_returnsOpenAI_whenNoConditionsMatch() {
        ModelProvider result = OpenAiOfficialSetup.detectModelProvider(false, false, null, null, null);

        assertEquals(ModelProvider.OPEN_AI, result);
    }

    @Test
    void setupSyncClient_returnsClient_whenValidApiKeyProvided() {
        OpenAIClient client = OpenAiOfficialSetup.setupSyncClient(
                null,
                "valid-api-key",
                null,
                null,
                null,
                null,
                false,
                false,
                null,
                Duration.ofSeconds(30),
                2,
                null,
                null);

        assertNotNull(client);
    }

    @Test
    void setupSyncClient_appliesCustomHeaders_whenProvided() {
        Map<String, String> customHeaders = Collections.singletonMap("X-Custom-Header", "value");

        OpenAIClient client = OpenAiOfficialSetup.setupSyncClient(
                null,
                "valid-api-key",
                null,
                null,
                null,
                null,
                false,
                false,
                null,
                Duration.ofSeconds(30),
                2,
                null,
                customHeaders);

        assertNotNull(client);
    }

    @Test
    void calculateBaseUrl_returnsDefaultOpenAIUrl_whenBaseUrlIsNull() {
        String result = OpenAiOfficialSetup.calculateBaseUrl(null, ModelProvider.OPEN_AI, null, null);

        assertEquals(OpenAiOfficialSetup.OPENAI_URL, result);
    }

    @Test
    void calculateBaseUrl_returnsGitHubUrl_whenModelHostIsGitHub() {
        String result = OpenAiOfficialSetup.calculateBaseUrl(null, ModelProvider.GITHUB_MODELS, null, null);

        assertEquals(OpenAiOfficialSetup.GITHUB_MODELS_URL, result);
    }

    @Test
    void should_not_append_api_version_to_baseUrl_when_azureOpenAIServiceVersion_is_set() {
        // Given: Azure OpenAI configuration with service version
        String baseUrl = "https://test.openai.azure.com";
        String modelName = CHAT_MODEL_NAME.asString();
        String azureDeploymentName = null;
        ModelProvider modelProvider = ModelProvider.AZURE_OPEN_AI;

        // When: Calculate base URL
        String calculatedUrl =
                OpenAiOfficialSetup.calculateBaseUrl(baseUrl, modelProvider, modelName, azureDeploymentName);

        assertThat(calculatedUrl)
                .as("URL should NOT contain ?api-version= - it should be added only by SDK")
                .doesNotContain("?api-version=");
        assertThat(calculatedUrl).isEqualTo("https://test.openai.azure.com");
    }

    @Test
    void should_not_append_api_version_to_baseUrl_with_deployment_name() {
        // Given: Azure OpenAI configuration with service version and deployment name
        String baseUrl = "https://test.openai.azure.com";
        String modelName = CHAT_MODEL_NAME.asString();
        String azureDeploymentName = "my-deployment";
        ModelProvider modelProvider = ModelProvider.AZURE_OPEN_AI;

        // When: Calculate base URL
        String calculatedUrl =
                OpenAiOfficialSetup.calculateBaseUrl(baseUrl, modelProvider, modelName, azureDeploymentName);

        assertThat(calculatedUrl)
                .as("URL should contain deployment path but NOT ?api-version=")
                .isEqualTo("https://test.openai.azure.com/openai/deployments/my-deployment")
                .doesNotContain("?api-version=");
    }

    @Test
    void should_handle_baseUrl_with_trailing_slash() {
        // Given: Azure OpenAI configuration with trailing slash in base URL
        String baseUrl = "https://test.openai.azure.com/";
        String modelName = CHAT_MODEL_NAME.asString();
        String azureDeploymentName = null;
        ModelProvider modelProvider = ModelProvider.AZURE_OPEN_AI;

        // When: Calculate base URL
        String calculatedUrl =
                OpenAiOfficialSetup.calculateBaseUrl(baseUrl, modelProvider, modelName, azureDeploymentName);

        // Then: The trailing slash should be removed
        assertThat(calculatedUrl)
                .startsWith("https://test.openai.azure.com")
                .doesNotContain("openai.azure.com//")
                .doesNotContain("openai.azure.com/?");
    }

    @Test
    void should_detect_azure_openai_when_azureOpenAIServiceVersion_is_set() {
        // Given: Configuration with only azureOpenAIServiceVersion set
        boolean isAzure = false;
        boolean isGitHubModels = false;
        String baseUrl = null;
        String azureDeploymentName = null;
        AzureOpenAIServiceVersion azureOpenAIServiceVersion = AzureOpenAIServiceVersion.getV2024_10_21();

        // When: Detect model host
        ModelProvider modelProvider = OpenAiOfficialSetup.detectModelProvider(
                isAzure, isGitHubModels, baseUrl, azureDeploymentName, azureOpenAIServiceVersion);

        // Then: Should detect Azure OpenAI
        assertThat(modelProvider).isEqualTo(ModelProvider.AZURE_OPEN_AI);
    }

    @Test
    void should_detect_azure_openai_from_baseUrl() {
        // Given: Azure OpenAI base URLs
        String[] azureUrls = {
            "https://test.openai.azure.com",
            "https://test.openai.azure.com/",
            "https://test.cognitiveservices.azure.com",
            "https://test.cognitiveservices.azure.com/"
        };

        for (String azureUrl : azureUrls) {
            // When: Detect model host
            ModelProvider modelProvider = OpenAiOfficialSetup.detectModelProvider(false, false, azureUrl, null, null);

            // Then: Should detect Azure OpenAI
            assertThat(modelProvider)
                    .as("Should detect Azure OpenAI from URL: " + azureUrl)
                    .isEqualTo(ModelProvider.AZURE_OPEN_AI);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
    void should_work_with_explicit_azureOpenAIServiceVersion() {
        // Given: Create a chat model with explicit azureOpenAIServiceVersion
        ChatModel model = OpenAiOfficialChatModel.builder()
                .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .modelName(CHAT_MODEL_NAME)
                .azureOpenAIServiceVersion(AzureOpenAIServiceVersion.getV2024_10_21())
                .build();

        // When: Make a simple chat request
        UserMessage userMessage = UserMessage.from("What is 2+2? Answer with just the number.");

        // Then: The request should succeed without 404 errors
        // If the bug exists, this will fail with a 404 error due to duplicate api-version parameters
        AiMessage aiMessage = model.chat(userMessage).aiMessage();

        // Verify we got a valid response
        assertThat(aiMessage).isNotNull();
        assertThat(aiMessage.text()).isNotBlank();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
    void should_work_with_explicit_azureOpenAIServiceVersion_and_azureDeploymentName() {
        // Given: Create a chat model with both azureOpenAIServiceVersion and azureDeploymentName
        ChatModel model = OpenAiOfficialChatModel.builder()
                .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .modelName(CHAT_MODEL_NAME)
                .azureDeploymentName(CHAT_MODEL_NAME.toString())
                .azureOpenAIServiceVersion(AzureOpenAIServiceVersion.getV2024_10_21())
                .build();

        // When: Make a simple chat request
        UserMessage userMessage = UserMessage.from("What is 3+3? Answer with just the number.");

        // Then: The request should succeed without 404 errors
        AiMessage aiMessage = model.chat(userMessage).aiMessage();

        // Verify we got a valid response
        assertThat(aiMessage).isNotNull();
        assertThat(aiMessage.text()).isNotBlank();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
    void should_work_with_isAzure_flag_and_azureOpenAIServiceVersion() {
        // Given: Create a chat model with isAzure flag explicitly set
        ChatModel model = OpenAiOfficialChatModel.builder()
                .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .modelName(CHAT_MODEL_NAME)
                .isAzure(true)
                .azureOpenAIServiceVersion(AzureOpenAIServiceVersion.getV2024_10_21())
                .build();

        // When: Make a simple chat request
        UserMessage userMessage = UserMessage.from("What is 5+5? Answer with just the number.");

        // Then: The request should succeed without 404 errors
        AiMessage aiMessage = model.chat(userMessage).aiMessage();

        // Verify we got a valid response
        assertThat(aiMessage).isNotNull();
        assertThat(aiMessage.text()).isNotBlank();
    }
}
