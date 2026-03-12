package dev.langchain4j.model.openaiofficial.setup;

import static java.time.Duration.ofSeconds;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.credential.Credential;
import dev.langchain4j.model.ModelProvider;
import java.net.Proxy;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helps configure the OpenAI Java SDK, depending on the platform used.
 */
public class OpenAiOfficialSetup {

    static final String OPENAI_URL = "https://api.openai.com/v1";
    static final String OPENAI_API_KEY = "OPENAI_API_KEY";
    static final String MICROSOFT_FOUNDRY_KEY = "MICROSOFT_FOUNDRY_KEY";
    static final String AZURE_OPENAI_KEY = "AZURE_OPENAI_KEY";
    static final String GITHUB_MODELS_URL = "https://models.github.ai/inference";
    static final String GITHUB_TOKEN = "GITHUB_TOKEN";
    static final String DEFAULT_USER_AGENT = "langchain4j-openai-official";

    private static final Logger logger = LoggerFactory.getLogger(OpenAiOfficialSetup.class);

    private static final Duration DEFAULT_DURATION = ofSeconds(60);

    private static final int DEFAULT_MAX_RETRIES = 3;

    public static OpenAIClient setupSyncClient(
            String baseUrl,
            String apiKey,
            Credential credential,
            String microsoftFoundryDeploymentName,
            AzureOpenAIServiceVersion azureOpenAiServiceVersion,
            String organizationId,
            boolean isMicrosoftFoundry,
            boolean isGitHubModels,
            String modelName,
            Duration timeout,
            Integer maxRetries,
            Proxy proxy,
            Map<String, String> customHeaders) {

        baseUrl = detectBaseUrlFromEnv(baseUrl);
        var modelProvider = detectModelProvider(
                isMicrosoftFoundry, isGitHubModels, baseUrl, microsoftFoundryDeploymentName, azureOpenAiServiceVersion);
        if (timeout == null) {
            timeout = DEFAULT_DURATION;
        }
        if (maxRetries == null) {
            maxRetries = DEFAULT_MAX_RETRIES;
        }
        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder();
        builder.baseUrl(calculateBaseUrl(baseUrl, modelProvider, modelName, microsoftFoundryDeploymentName));

        String calculatedApiKey = apiKey != null ? apiKey : detectApiKey(modelProvider);
        if (calculatedApiKey != null) {
            builder.apiKey(calculatedApiKey);
        } else {
            if (credential != null) {
                builder.credential(credential);
            } else if (modelProvider == ModelProvider.MICROSOFT_FOUNDRY
                    || modelProvider == ModelProvider.AZURE_OPEN_AI) {
                // If no API key is provided for Microsoft Foundry, we try to use passwordless
                // authentication
                builder.credential(azureAuthentication());
            }
        }
        builder.organization(organizationId);

        if (azureOpenAiServiceVersion != null) {
            builder.azureServiceVersion(azureOpenAiServiceVersion);
        }

        if (proxy != null) {
            builder.proxy(proxy);
        }

        builder.putHeader("User-Agent", DEFAULT_USER_AGENT);
        if (customHeaders != null) {
            builder.putAllHeaders(customHeaders.entrySet().stream()
                    .collect(
                            Collectors.toMap(Map.Entry::getKey, entry -> Collections.singletonList(entry.getValue()))));
        }

        builder.timeout(timeout);
        builder.maxRetries(maxRetries);
        return builder.build();
    }

    /**
     * The asynchronous client setup is the same as the synchronous one in the OpenAI Java
     * SDK, but uses a different client implementation.
     */
    public static OpenAIClientAsync setupAsyncClient(
            String baseUrl,
            String apiKey,
            Credential credential,
            String microsoftFoundryDeploymentName,
            AzureOpenAIServiceVersion azureOpenAiServiceVersion,
            String organizationId,
            boolean isMicrosoftFoundry,
            boolean isGitHubModels,
            String modelName,
            Duration timeout,
            Integer maxRetries,
            Proxy proxy,
            Map<String, String> customHeaders) {

        baseUrl = detectBaseUrlFromEnv(baseUrl);
        var modelProvider = detectModelProvider(
                isMicrosoftFoundry, isGitHubModels, baseUrl, microsoftFoundryDeploymentName, azureOpenAiServiceVersion);
        if (timeout == null) {
            timeout = DEFAULT_DURATION;
        }
        if (maxRetries == null) {
            maxRetries = DEFAULT_MAX_RETRIES;
        }
        OpenAIOkHttpClientAsync.Builder builder = OpenAIOkHttpClientAsync.builder();
        builder.baseUrl(calculateBaseUrl(baseUrl, modelProvider, modelName, microsoftFoundryDeploymentName));

        String calculatedApiKey = apiKey != null ? apiKey : detectApiKey(modelProvider);
        if (calculatedApiKey != null) {
            builder.apiKey(calculatedApiKey);
        } else {
            if (credential != null) {
                builder.credential(credential);
            } else if (modelProvider == ModelProvider.MICROSOFT_FOUNDRY
                    || modelProvider == ModelProvider.AZURE_OPEN_AI) {
                // If no API key is provided for Microsoft Foundry, we try to use passwordless
                // authentication
                builder.credential(azureAuthentication());
            }
        }
        builder.organization(organizationId);

        if (azureOpenAiServiceVersion != null) {
            builder.azureServiceVersion(azureOpenAiServiceVersion);
        }

        if (proxy != null) {
            builder.proxy(proxy);
        }

        builder.putHeader("User-Agent", DEFAULT_USER_AGENT);
        if (customHeaders != null) {
            builder.putAllHeaders(customHeaders.entrySet().stream()
                    .collect(
                            Collectors.toMap(Map.Entry::getKey, entry -> Collections.singletonList(entry.getValue()))));
        }

        builder.timeout(timeout);
        builder.maxRetries(maxRetries);
        return builder.build();
    }

    static String detectBaseUrlFromEnv(String baseUrl) {
        if (baseUrl == null) {
            var openAiBaseUrl = System.getenv("OPENAI_BASE_URL");
            if (openAiBaseUrl != null) {
                baseUrl = openAiBaseUrl;
                logger.debug("OpenAI Base URL detected from environment variable OPENAI_BASE_URL.");
            }
            var azureOpenAiBaseUrl = System.getenv("AZURE_OPENAI_BASE_URL");
            if (azureOpenAiBaseUrl != null) {
                baseUrl = azureOpenAiBaseUrl;
                logger.debug("Microsoft Foundry Base URL detected from environment variable AZURE_OPENAI_BASE_URL.");
            }
        }
        return baseUrl;
    }

    public static ModelProvider detectModelProvider(
            boolean isMicrosoftFoundry,
            boolean isGitHubModels,
            String baseUrl,
            String microsoftFoundryDeploymentName,
            AzureOpenAIServiceVersion azureOpenAIServiceVersion) {

        if (isMicrosoftFoundry) {
            return ModelProvider.MICROSOFT_FOUNDRY; // Forced by the user
        }
        if (isGitHubModels) {
            return ModelProvider.GITHUB_MODELS; // Forced by the user
        }
        if (baseUrl != null) {
            if (baseUrl.endsWith("openai.azure.com")
                    || baseUrl.endsWith("openai.azure.com/")
                    || baseUrl.endsWith("cognitiveservices.azure.com")
                    || baseUrl.endsWith("cognitiveservices.azure.com/")) {
                return ModelProvider.MICROSOFT_FOUNDRY;
            } else if (baseUrl.startsWith(GITHUB_MODELS_URL)) {
                return ModelProvider.GITHUB_MODELS;
            }
        }
        if (microsoftFoundryDeploymentName != null || azureOpenAIServiceVersion != null) {
            return ModelProvider.MICROSOFT_FOUNDRY;
        }
        return ModelProvider.OPEN_AI;
    }

    static String calculateBaseUrl(
            String baseUrl, ModelProvider modelProvider, String modelName, String microsoftFoundryDeploymentName) {

        if (modelProvider == ModelProvider.OPEN_AI) {
            if (baseUrl == null || baseUrl.isBlank()) {
                return OPENAI_URL;
            }
            return baseUrl;
        } else if (modelProvider == ModelProvider.GITHUB_MODELS) {
            if (baseUrl == null || baseUrl.isBlank()) {
                return GITHUB_MODELS_URL;
            }
            if (baseUrl.startsWith(GITHUB_MODELS_URL)) { // To support GitHub Models for specific orgs
                return baseUrl;
            }
            return GITHUB_MODELS_URL;
        } else if (modelProvider == ModelProvider.MICROSOFT_FOUNDRY) {
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalArgumentException("Base URL must be provided for Microsoft Foundry.");
            }
            String tmpUrl = baseUrl;
            if (baseUrl.endsWith("/") || baseUrl.endsWith("?")) {
                tmpUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            // If the MicrosoftFoundry deployment name is not configured, the model name will be used
            // by default by the OpenAI Java
            // SDK
            if (microsoftFoundryDeploymentName != null && !microsoftFoundryDeploymentName.equals(modelName)) {
                tmpUrl += "/openai/deployments/" + microsoftFoundryDeploymentName;
            }
            return tmpUrl;
        } else {
            throw new IllegalArgumentException("Unknown model provider: " + modelProvider);
        }
    }

    static Credential azureAuthentication() {
        try {
            return AzureInternalOpenAiOfficialHelper.getAzureCredential();
        } catch (NoClassDefFoundError e) {
            throw new IllegalArgumentException(
                    "Microsoft Foundry was detected, but no credential was provided. "
                            + "If you want to use passwordless authentication, you need to add the Azure Identity library (groupId=`com.azure`, artifactId=`azure-identity`) to your classpath.");
        }
    }

    static String detectApiKey(ModelProvider modelProvider) {
        if (modelProvider == ModelProvider.OPEN_AI && System.getenv(OPENAI_API_KEY) != null) {
            return System.getenv(OPENAI_API_KEY);
        } else if (modelProvider == ModelProvider.MICROSOFT_FOUNDRY && System.getenv(MICROSOFT_FOUNDRY_KEY) != null) {
            return System.getenv(MICROSOFT_FOUNDRY_KEY);
        } else if (modelProvider == ModelProvider.MICROSOFT_FOUNDRY && System.getenv(AZURE_OPENAI_KEY) != null) {
            return System.getenv(AZURE_OPENAI_KEY); // Azure OpenAi is deprecated in favor of Microsoft Foundry
        } else if (modelProvider == ModelProvider.MICROSOFT_FOUNDRY && System.getenv(OPENAI_API_KEY) != null) {
            return System.getenv(OPENAI_API_KEY);
        } else if (modelProvider == ModelProvider.GITHUB_MODELS && System.getenv(GITHUB_TOKEN) != null) {
            return System.getenv(GITHUB_TOKEN);
        }
        return null;
    }
}
