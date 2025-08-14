package dev.langchain4j.model.azure;

import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.*;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.AudioTranscription;
import com.azure.ai.openai.models.AudioTranscriptionFormat;
import com.azure.ai.openai.models.AudioTranscriptionOptions;
import com.azure.core.credential.KeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.core.http.HttpClientProvider;
import com.azure.core.http.ProxyOptions;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.model.azure.spi.AzureOpenAiAudioTranscriptionModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an OpenAI audio model, hosted on Azure, such as whisper.
 * <p>
 * You can find a tutorial on using Azure OpenAI for speech to text at: https://learn.microsoft.com/azure/ai-services/openai/whisper-quickstart
 * <p>
 * Mandatory parameters for initialization are: endpoint and apikey (or an alternate authentication method, see below for more information).
 * Optionally you can set serviceVersion (if not, the latest version is used) and deploymentName (if not, a default name is used).
 * You can also provide your own OpenAIClient instance, if you need more flexibility.
 * <p>
 * By default, the response type will be json with the raw text included.
 * You can change the response format using the responseFormat() method in the Builder, or the responseFormat parameter in the constructor.
 * <p>
 * There are 3 authentication methods:
 * <p>
 * 1. Azure OpenAI API Key Authentication: this is the most common method, using an Azure OpenAI API key.
 * You need to provide the OpenAI API Key as a parameter, using the apiKey() method in the Builder, or the apiKey parameter in the constructor:
 * For example, you would use `builder.apiKey("{key}")`.
 * <p>
 * 2. non-Azure OpenAI API Key Authentication: this method allows to use the OpenAI service, instead of Azure OpenAI.
 * You can use the nonAzureApiKey() method in the Builder, which will also automatically set the endpoint to "https://api.openai.com/v1".
 * For example, you would use `builder.nonAzureApiKey("{key}")`.
 * The constructor requires a KeyCredential instance, which can be created using `new AzureKeyCredential("{key}")`, and doesn't set up the endpoint.
 * <p>
 * 3. Azure OpenAI client with Microsoft Entra ID (formerly Azure Active Directory) credentials.
 * - This requires to add the `com.azure:azure-identity` dependency to your project, which is an optional dependency to this library.
 * - You need to provide a TokenCredential instance, using the tokenCredential() method in the Builder, or the tokenCredential parameter in the constructor.
 * As an example, DefaultAzureCredential can be used to authenticate the client: Set the values of the client ID, tenant ID, and
 * client secret of the AAD application as environment variables: AZURE_CLIENT_ID, AZURE_TENANT_ID, AZURE_CLIENT_SECRET.
 * Then, provide the DefaultAzureCredential instance to the builder: `builder.tokenCredential(new DefaultAzureCredentialBuilder().build())`.
 */
public class AzureOpenAiAudioTranscriptionModel implements AudioModel {
    private static final Logger logger = LoggerFactory.getLogger(AzureOpenAiAudioTranscriptionModel.class);
    private OpenAIClient client;
    private String deploymentName;
    private AudioTranscriptionFormat responseFormat = AudioTranscriptionFormat.JSON;
    private String language;

    public AzureOpenAiAudioTranscriptionModel(
            OpenAIClient client, String deploymentName, String language, String responseFormat) {

        this(deploymentName, language, responseFormat);
        this.client = client;
    }

    public AzureOpenAiAudioTranscriptionModel(
            String endpoint,
            String serviceVersion,
            String apiKey,
            String deploymentName,
            String language,
            String responseFormat,
            Duration timeout,
            Integer maxRetries,
            HttpClientProvider httpClientProvider,
            ProxyOptions proxyOptions,
            boolean logRequestsAndResponses,
            String userAgentSuffix,
            Map<String, String> customHeaders) {

        this(deploymentName, language, responseFormat);
        this.client = setupSyncClient(
                endpoint,
                serviceVersion,
                apiKey,
                timeout,
                maxRetries,
                httpClientProvider,
                proxyOptions,
                logRequestsAndResponses,
                userAgentSuffix,
                customHeaders);
    }

    public AzureOpenAiAudioTranscriptionModel(
            String endpoint,
            String serviceVersion,
            KeyCredential keyCredential,
            String language,
            String deploymentName,
            String responseFormat,
            Duration timeout,
            Integer maxRetries,
            HttpClientProvider httpClientProvider,
            ProxyOptions proxyOptions,
            boolean logRequestsAndResponses,
            String userAgentSuffix,
            Map<String, String> customHeaders) {

        this(deploymentName, language, responseFormat);
        this.client = setupSyncClient(
                endpoint,
                serviceVersion,
                keyCredential,
                timeout,
                maxRetries,
                httpClientProvider,
                proxyOptions,
                logRequestsAndResponses,
                userAgentSuffix,
                customHeaders);
    }

    public AzureOpenAiAudioTranscriptionModel(
            String endpoint,
            String serviceVersion,
            TokenCredential tokenCredential,
            String deploymentName,
            String language,
            String responseFormat,
            Duration timeout,
            Integer maxRetries,
            HttpClientProvider httpClientProvider,
            ProxyOptions proxyOptions,
            boolean logRequestsAndResponses,
            String userAgentSuffix,
            Map<String, String> customHeaders) {

        this(deploymentName, language, responseFormat);
        this.client = setupSyncClient(
                endpoint,
                serviceVersion,
                tokenCredential,
                timeout,
                maxRetries,
                httpClientProvider,
                proxyOptions,
                logRequestsAndResponses,
                userAgentSuffix,
                customHeaders);
    }

    private AzureOpenAiAudioTranscriptionModel(String deploymentName, String language, String responseFormat) {
        if (deploymentName != null) {
            this.deploymentName = deploymentName;
        }
        if (language != null) {
            this.language = language;
        }
        if (responseFormat != null) {
            this.responseFormat = AudioTranscriptionFormat.fromString(responseFormat);
        }
    }

    @Override
    public Response<String> transcribe(Audio audio) {
        AudioTranscriptionOptions options = new AudioTranscriptionOptions(null) // audio.audioData()
                .setModel(deploymentName)
                .setLanguage(language)
                .setResponseFormat(responseFormat);
        AudioTranscription audioTranscription =
                client.getAudioTranscription(deploymentName, options.getFilename(), options);
        return Response.from(audioTranscription.getText());
    }

    public static Builder builder() {
        for (AzureOpenAiAudioTranscriptionModelBuilderFactory factory :
                loadFactories(AzureOpenAiAudioTranscriptionModelBuilderFactory.class)) {
            return factory.get();
        }
        return new Builder();
    }

    public static class Builder {

        private String endpoint;
        private String serviceVersion;
        private String apiKey;
        private KeyCredential keyCredential;
        private TokenCredential tokenCredential;
        private HttpClientProvider httpClientProvider;
        private String deploymentName;
        private String user;
        private String responseFormat;
        private Duration timeout;
        private Integer maxRetries;
        private ProxyOptions proxyOptions;
        private boolean logRequestsAndResponses;
        private OpenAIClient openAIClient;
        private String userAgentSuffix;
        private String language;
        private Audio audio;
        private Map<String, String> customHeaders;

        /**
         * Sets the Azure OpenAI endpoint. This is a mandatory parameter.
         *
         * @param endpoint The Azure OpenAI endpoint in the format: https://{resource}.openai.azure.com/
         * @return builder
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Sets the {@code HttpClientProvider} to use for creating the HTTP client to communicate with the OpenAI api.
         *
         * @param httpClientProvider The {@code HttpClientProvider} to use
         * @return builder
         */
        public Builder httpClientProvider(HttpClientProvider httpClientProvider) {
            this.httpClientProvider = httpClientProvider;
            return this;
        }

        /**
         * Sets the Azure OpenAI API service version. This is a mandatory parameter.
         *
         * @param serviceVersion The Azure OpenAI API service version in the format: 2023-05-15
         * @return builder
         */
        public Builder serviceVersion(String serviceVersion) {
            this.serviceVersion = serviceVersion;
            return this;
        }

        /**
         * Sets the Azure OpenAI API key.
         *
         * @param apiKey The Azure OpenAI API key.
         * @return builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Used to authenticate with the OpenAI service, instead of Azure OpenAI.
         * This automatically sets the endpoint to https://api.openai.com/v1.
         *
         * @param nonAzureApiKey The non-Azure OpenAI API key
         * @return builder
         */
        public Builder nonAzureApiKey(String nonAzureApiKey) {
            this.keyCredential = new KeyCredential(nonAzureApiKey);
            this.endpoint = "https://api.openai.com/v1";
            return this;
        }

        /**
         * Used to authenticate to Azure OpenAI with Azure Active Directory credentials.
         *
         * @param tokenCredential the credentials to authenticate with Azure Active Directory
         * @return builder
         */
        public Builder tokenCredential(TokenCredential tokenCredential) {
            this.tokenCredential = tokenCredential;
            return this;
        }

        /**
         * Sets the deployment name in Azure OpenAI. This is a mandatory parameter.
         *
         * @param deploymentName The Deployment name.
         * @return builder
         */
        public Builder deploymentName(String deploymentName) {
            this.deploymentName = deploymentName;
            return this;
        }

        /**
         * Sets the user of the audio. This is an optional parameter.
         *
         * @param user The user of the audio.
         * @return builder
         */
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        /**
         * Sets the response format of the audio. This is an optional parameter.
         *
         * @param responseFormat The response format of the audio.
         * @return builder
         */
        public Builder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        /**
         * Sets the response format of the audio, using the AudioResponseFormat enum. This is an optional parameter.
         *
         * @param audioTranscriptionFormat The response format of the audio.
         * @return builder
         */
        public Builder responseFormat(AudioTranscriptionFormat audioTranscriptionFormat) {
            this.responseFormat = audioTranscriptionFormat.toString();
            return this;
        }

        /**
         * Sets the language of the audio. This is an optional parameter.
         *
         * @param language The language of the audio.
         * @return builder
         */
        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder proxyOptions(ProxyOptions proxyOptions) {
            this.proxyOptions = proxyOptions;
            return this;
        }

        public Builder logRequestsAndResponses(Boolean logRequestsAndResponses) {
            this.logRequestsAndResponses = logRequestsAndResponses;
            return this;
        }

        public Builder openAIClient(OpenAIClient openAIClient) {
            this.openAIClient = openAIClient;
            return this;
        }

        public Builder userAgentSuffix(String userAgentSuffix) {
            this.userAgentSuffix = userAgentSuffix;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public AzureOpenAiAudioTranscriptionModel build() {
            if (openAIClient == null) {
                if (tokenCredential != null) {
                    return new AzureOpenAiAudioTranscriptionModel(
                            endpoint,
                            serviceVersion,
                            tokenCredential,
                            deploymentName,
                            language,
                            responseFormat,
                            timeout,
                            maxRetries,
                            httpClientProvider,
                            proxyOptions,
                            logRequestsAndResponses,
                            userAgentSuffix,
                            customHeaders);
                } else if (keyCredential != null) {
                    return new AzureOpenAiAudioTranscriptionModel(
                            endpoint,
                            serviceVersion,
                            keyCredential,
                            deploymentName,
                            language,
                            responseFormat,
                            timeout,
                            maxRetries,
                            httpClientProvider,
                            proxyOptions,
                            logRequestsAndResponses,
                            userAgentSuffix,
                            customHeaders);
                }
                return new AzureOpenAiAudioTranscriptionModel(
                        endpoint,
                        serviceVersion,
                        apiKey,
                        deploymentName,
                        language,
                        responseFormat,
                        timeout,
                        maxRetries,
                        httpClientProvider,
                        proxyOptions,
                        logRequestsAndResponses,
                        userAgentSuffix,
                        customHeaders);
            }
            return new AzureOpenAiAudioTranscriptionModel(openAIClient, deploymentName, language, responseFormat);
        }
    }
}
