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
import com.azure.core.http.netty.NettyAsyncHttpClientProvider;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.model.audio.AudioModel;
import dev.langchain4j.model.audio.AudioTranscriptionRequest;
import dev.langchain4j.model.audio.AudioTranscriptionResponse;
import dev.langchain4j.model.azure.spi.AzureOpenAiAudioTranscriptionModelBuilderFactory;
import dev.langchain4j.model.output.Response;

import java.time.Duration;
import java.util.Map;

/**
 * Represents an Azure OpenAI audio transcription model, such as Whisper.
 * <p>
 * You can find a tutorial on using Azure OpenAI for speech to text at: https://learn.microsoft.com/azure/ai-services/openai/whisper-quickstart
 * <p>
 * Mandatory parameters for initialization are:
 * <ul>
 *     <li>endpoint: The Azure OpenAI endpoint URL</li>
 *     <li>authentication: Either apiKey, tokenCredential (Azure Active Directory), or an existing OpenAIClient</li>
 *     <li>deploymentName: The name of your Azure OpenAI audio model deployment</li>
 * </ul>
 * <p>
 * There are 3 authentication methods:
 * <ol>
 *     <li><b>Azure OpenAI API Key:</b> The most common method using an Azure OpenAI API key.
 *     Use the {@code apiKey()} method in the Builder.</li>
 *     <li><b>Non-Azure OpenAI API Key:</b> Use the OpenAI service directly (not Azure OpenAI).
 *     Use the {@code nonAzureApiKey()} method, which will automatically set the endpoint.</li>
 *     <li><b>Microsoft Entra ID (Azure Active Directory):</b> Authenticate using Azure credentials.
 *     Requires the {@code com.azure:azure-identity} dependency. Use the {@code tokenCredential()} method
 *     with an appropriate credential implementation like {@code DefaultAzureCredential}.</li>
 * </ol>
 */
public class AzureOpenAiAudioTranscriptionModel implements AudioModel {
    
    private final OpenAIClient client;
    private final String deploymentName;
    private final AudioTranscriptionFormat responseFormat;

    /**
     * Creates a new AzureOpenAiAudioTranscriptionModel with the provided client and parameters.
     *
     * @param client The Azure OpenAI client
     * @param deploymentName The deployment name of the audio model
     * @param responseFormat The response format (can be null for default JSON format)
     */
    public AzureOpenAiAudioTranscriptionModel(OpenAIClient client, 
                                             String deploymentName, 
                                             AudioTranscriptionFormat responseFormat) {
        if (client == null) {
            throw new IllegalArgumentException("client is required");
        }
        if (deploymentName == null || deploymentName.isBlank()) {
            throw new IllegalArgumentException("deploymentName is required");
        }
        
        this.client = client;
        this.deploymentName = deploymentName;
        this.responseFormat = responseFormat != null ? responseFormat : AudioTranscriptionFormat.JSON;
    }
    
    /**
     * Creates a new AzureOpenAiAudioTranscriptionModel with a newly created client using API key authentication.
     *
     * @param endpoint The Azure OpenAI endpoint
     * @param serviceVersion The Azure OpenAI service version
     * @param apiKey The API key for authentication
     * @param deploymentName The deployment name of the audio model
     * @param responseFormat The response format
     * @param timeout The request timeout duration
     * @param maxRetries The maximum number of retries
     * @param httpClientProvider The HTTP client provider
     * @param proxyOptions The proxy options
     * @param logRequestsAndResponses Whether to log requests and responses
     * @param userAgentSuffix The user agent suffix
     * @param customHeaders Custom HTTP headers
     */
    public AzureOpenAiAudioTranscriptionModel(String endpoint,
                                             String serviceVersion,
                                             String apiKey,
                                             String deploymentName,
                                             AudioTranscriptionFormat responseFormat,
                                             Duration timeout,
                                             Integer maxRetries,
                                             HttpClientProvider httpClientProvider,
                                             ProxyOptions proxyOptions,
                                             boolean logRequestsAndResponses,
                                             String userAgentSuffix,
                                             Map<String, String> customHeaders) {
        if (deploymentName == null || deploymentName.isBlank()) {
            throw new IllegalArgumentException("deploymentName is required");
        }
        
        this.client = setupSyncClient(
                endpoint, 
                serviceVersion, 
                apiKey, 
                timeout, 
                maxRetries, 
                httpClientProvider != null ? httpClientProvider : new NettyAsyncHttpClientProvider(), 
                proxyOptions, 
                logRequestsAndResponses, 
                userAgentSuffix, 
                customHeaders
        );
        this.deploymentName = deploymentName;
        this.responseFormat = responseFormat != null ? responseFormat : AudioTranscriptionFormat.JSON;
    }

    /**
     * Creates a new AzureOpenAiAudioTranscriptionModel with a newly created client using KeyCredential authentication.
     *
     * @param endpoint The Azure OpenAI endpoint
     * @param serviceVersion The Azure OpenAI service version
     * @param keyCredential The key credential for authentication
     * @param deploymentName The deployment name of the audio model
     * @param responseFormat The response format
     * @param timeout The request timeout duration
     * @param maxRetries The maximum number of retries
     * @param httpClientProvider The HTTP client provider
     * @param proxyOptions The proxy options
     * @param logRequestsAndResponses Whether to log requests and responses
     * @param userAgentSuffix The user agent suffix
     * @param customHeaders Custom HTTP headers
     */
    public AzureOpenAiAudioTranscriptionModel(String endpoint,
                                             String serviceVersion,
                                             KeyCredential keyCredential,
                                             String deploymentName,
                                             AudioTranscriptionFormat responseFormat,
                                             Duration timeout,
                                             Integer maxRetries,
                                             HttpClientProvider httpClientProvider,
                                             ProxyOptions proxyOptions,
                                             boolean logRequestsAndResponses,
                                             String userAgentSuffix,
                                             Map<String, String> customHeaders) {
        if (deploymentName == null || deploymentName.isBlank()) {
            throw new IllegalArgumentException("deploymentName is required");
        }
        
        this.client = setupSyncClient(
                endpoint, 
                serviceVersion, 
                keyCredential, 
                timeout, 
                maxRetries, 
                httpClientProvider != null ? httpClientProvider : new NettyAsyncHttpClientProvider(), 
                proxyOptions, 
                logRequestsAndResponses, 
                userAgentSuffix, 
                customHeaders
        );
        this.deploymentName = deploymentName;
        this.responseFormat = responseFormat != null ? responseFormat : AudioTranscriptionFormat.JSON;
    }

    /**
     * Creates a new AzureOpenAiAudioTranscriptionModel with a newly created client using TokenCredential authentication.
     *
     * @param endpoint The Azure OpenAI endpoint
     * @param serviceVersion The Azure OpenAI service version
     * @param tokenCredential The token credential for authentication
     * @param deploymentName The deployment name of the audio model
     * @param responseFormat The response format
     * @param timeout The request timeout duration
     * @param maxRetries The maximum number of retries
     * @param httpClientProvider The HTTP client provider
     * @param proxyOptions The proxy options
     * @param logRequestsAndResponses Whether to log requests and responses
     * @param userAgentSuffix The user agent suffix
     * @param customHeaders Custom HTTP headers
     */
    public AzureOpenAiAudioTranscriptionModel(String endpoint,
                                             String serviceVersion,
                                             TokenCredential tokenCredential,
                                             String deploymentName,
                                             AudioTranscriptionFormat responseFormat,
                                             Duration timeout,
                                             Integer maxRetries,
                                             HttpClientProvider httpClientProvider,
                                             ProxyOptions proxyOptions,
                                             boolean logRequestsAndResponses,
                                             String userAgentSuffix,
                                             Map<String, String> customHeaders) {
        if (deploymentName == null || deploymentName.isBlank()) {
            throw new IllegalArgumentException("deploymentName is required");
        }
        
        this.client = setupSyncClient(
                endpoint, 
                serviceVersion, 
                tokenCredential, 
                timeout, 
                maxRetries, 
                httpClientProvider != null ? httpClientProvider : new NettyAsyncHttpClientProvider(), 
                proxyOptions, 
                logRequestsAndResponses, 
                userAgentSuffix, 
                customHeaders
        );
        this.deploymentName = deploymentName;
        this.responseFormat = responseFormat != null ? responseFormat : AudioTranscriptionFormat.JSON;
    }

    @Override
    public AudioTranscriptionResponse transcribe(AudioTranscriptionRequest request) {
        if (request == null || request.audio() == null) {
            throw new IllegalArgumentException("Request and audio data are required");
        }
        
        Audio audio = request.audio();
        String filename = audio.getFilename();
        if (filename == null) {
            filename = "audio.mp3"; // Default filename if not provided
        }
        
        // Create transcription options with all the provided parameters
        AudioTranscriptionOptions options = new AudioTranscriptionOptions(audio.audioData())
                .setPrompt(request.prompt())
                .setModel(deploymentName)
                .setFilename(filename)
                .setResponseFormat(responseFormat);
        
        // Only set language if provided
        if (request.language() != null) {
            options.setLanguage(request.language());
        }
        
        // Only set temperature if provided
        if (request.temperature() != null) {
            options.setTemperature(request.temperature());
        }
        
        // Execute the transcription request
        AudioTranscription audioTranscription = client.getAudioTranscription(deploymentName, options.getFilename(), options);
        
        return AudioTranscriptionResponse.from(audioTranscription.getText());
    }
    
    @Override
    @Deprecated
    public Response<String> transcribe(Audio audio) {
        AudioTranscriptionRequest request = AudioTranscriptionRequest.builder(audio).build();
        AudioTranscriptionResponse response = transcribe(request);
        return Response.from(response.text());
    }

    /**
     * Creates a new builder for AzureOpenAiAudioTranscriptionModel.
     * 
     * @return A new builder instance
     */
    public static Builder builder() {
        for (AzureOpenAiAudioTranscriptionModelBuilderFactory factory : loadFactories(AzureOpenAiAudioTranscriptionModelBuilderFactory.class)) {
            return factory.get();
        }
        return new Builder();
    }

    /**
     * Builder for {@link AzureOpenAiAudioTranscriptionModel}.
     */
    public static class Builder {
        private String endpoint;
        private String serviceVersion;
        private String apiKey;
        private KeyCredential keyCredential;
        private TokenCredential tokenCredential;
        private HttpClientProvider httpClientProvider;
        private String deploymentName;
        private AudioTranscriptionFormat responseFormat = AudioTranscriptionFormat.JSON;
        private Duration timeout;
        private Integer maxRetries;
        private ProxyOptions proxyOptions;
        private boolean logRequestsAndResponses;
        private OpenAIClient openAIClient;
        private String userAgentSuffix;
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
         * Sets the response format for the transcription.
         *
         * @param format The response format
         * @return builder
         */
        public Builder responseFormat(AudioTranscriptionFormat format) {
            this.responseFormat = format;
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

        /**
         * Builds a new instance of {@link AzureOpenAiAudioTranscriptionModel}.
         *
         * @return A new model instance
         */
        public AzureOpenAiAudioTranscriptionModel build() {
            if (openAIClient != null) {
                return new AzureOpenAiAudioTranscriptionModel(openAIClient, deploymentName, responseFormat);
            }
            
            // Validate mandatory parameters
            if (deploymentName == null || deploymentName.isBlank()) {
                throw new IllegalArgumentException("deploymentName is required");
            }

            if (tokenCredential != null) {
                return new AzureOpenAiAudioTranscriptionModel(
                        endpoint,
                        serviceVersion,
                        tokenCredential,
                        deploymentName,
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
                        responseFormat,
                        timeout,
                        maxRetries,
                        httpClientProvider,
                        proxyOptions,
                        logRequestsAndResponses,
                        userAgentSuffix,
                        customHeaders);
            }
            
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalArgumentException("Authentication is required: provide either apiKey, tokenCredential, keyCredential, or openAIClient");
            }
            
            return new AzureOpenAiAudioTranscriptionModel(
                    endpoint,
                    serviceVersion,
                    apiKey,
                    deploymentName,
                    responseFormat,
                    timeout,
                    maxRetries,
                    httpClientProvider,
                    proxyOptions,
                    logRequestsAndResponses,
                    userAgentSuffix,
                    customHeaders);
        }
    }
}
