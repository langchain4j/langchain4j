package dev.langchain4j.model.azure;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.*;
import com.azure.core.credential.KeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.http.ProxyOptions;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.azure.spi.AzureOpenAiStreamingLanguageModelBuilderFactory;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.language.TokenCountEstimator;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.internal.Utils.getOrDefault;

import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.contentFilterManagement;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.setupOpenAIClient;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * Represents an OpenAI language model, hosted on Azure, such as gpt-3.5-turbo-instruct.
 * The LLM's response is streamed token by token and should be handled with {@link StreamingResponseHandler}.
 * <p>
 * Mandatory parameters for initialization are: endpoint and apikey (or an alternate authentication method, see below for more information).
 * Optionally you can set serviceVersion (if not, the latest version is used) and deploymentName (if not, a default name is used).
 * You can also provide your own OpenAIClient instance, if you need more flexibility.
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
public class AzureOpenAiStreamingLanguageModel implements StreamingLanguageModel, TokenCountEstimator {

    private static final Logger logger = LoggerFactory.getLogger(AzureOpenAiStreamingLanguageModel.class);

    private OpenAIClient client;
    private final String deploymentName;
    private final Tokenizer tokenizer;
    private final Integer maxTokens;
    private final Double temperature;
    private final Double topP;
    private final Map<String, Integer> logitBias;
    private final String user;
    private final Integer n;
    private final Integer logprobs;
    private final Boolean echo;
    private final List<String> stop;
    private final Double presencePenalty;
    private final Double frequencyPenalty;


    public AzureOpenAiStreamingLanguageModel(OpenAIClient client,
                                             String deploymentName,
                                             Tokenizer tokenizer,
                                             Integer maxTokens,
                                             Double temperature,
                                             Double topP,
                                             Map<String, Integer> logitBias,
                                             String user,
                                             Integer n,
                                             Integer logprobs,
                                             Boolean echo,
                                             List<String> stop,
                                             Double presencePenalty,
                                             Double frequencyPenalty) {

        this(deploymentName, tokenizer, maxTokens, temperature, topP, logitBias, user, n, logprobs, echo, stop, presencePenalty, frequencyPenalty);
        this.client = client;
    }

    public AzureOpenAiStreamingLanguageModel(String endpoint,
                                             String serviceVersion,
                                             String apiKey,
                                             String deploymentName,
                                             Tokenizer tokenizer,
                                             Integer maxTokens,
                                             Double temperature,
                                             Double topP,
                                             Map<String, Integer> logitBias,
                                             String user,
                                             Integer n,
                                             Integer logprobs,
                                             Boolean echo,
                                             List<String> stop,
                                             Double presencePenalty,
                                             Double frequencyPenalty,
                                             Duration timeout,
                                             Integer maxRetries,
                                             ProxyOptions proxyOptions,
                                             boolean logRequestsAndResponses) {

        this(deploymentName, tokenizer, maxTokens, temperature, topP, logitBias, user, n, logprobs, echo, stop, presencePenalty, frequencyPenalty);
        this.client = setupOpenAIClient(endpoint, serviceVersion, apiKey, timeout, maxRetries, proxyOptions, logRequestsAndResponses);
    }

    public AzureOpenAiStreamingLanguageModel(String endpoint,
                                             String serviceVersion,
                                             KeyCredential keyCredential,
                                             String deploymentName,
                                             Tokenizer tokenizer,
                                             Integer maxTokens,
                                             Double temperature,
                                             Double topP,
                                             Map<String, Integer> logitBias,
                                             String user,
                                             Integer n,
                                             Integer logprobs,
                                             Boolean echo,
                                             List<String> stop,
                                             Double presencePenalty,
                                             Double frequencyPenalty,
                                             Duration timeout,
                                             Integer maxRetries,
                                             ProxyOptions proxyOptions,
                                             boolean logRequestsAndResponses) {

        this(deploymentName, tokenizer, maxTokens, temperature, topP, logitBias, user, n, logprobs, echo, stop, presencePenalty, frequencyPenalty);
        this.client = setupOpenAIClient(endpoint, serviceVersion, keyCredential, timeout, maxRetries, proxyOptions, logRequestsAndResponses);
    }

    public AzureOpenAiStreamingLanguageModel(String endpoint,
                                             String serviceVersion,
                                             TokenCredential tokenCredential,
                                             String deploymentName,
                                             Tokenizer tokenizer,
                                             Integer maxTokens,
                                             Double temperature,
                                             Double topP,
                                             Map<String, Integer> logitBias,
                                             String user,
                                             Integer n,
                                             Integer logprobs,
                                             Boolean echo,
                                             List<String> stop,
                                             Double presencePenalty,
                                             Double frequencyPenalty,
                                             Duration timeout,
                                             Integer maxRetries,
                                             ProxyOptions proxyOptions,
                                             boolean logRequestsAndResponses) {

        this(deploymentName, tokenizer, maxTokens, temperature, topP, logitBias, user, n, logprobs, echo, stop, presencePenalty, frequencyPenalty);
        this.client = setupOpenAIClient(endpoint, serviceVersion, tokenCredential, timeout, maxRetries, proxyOptions, logRequestsAndResponses);
    }

    private AzureOpenAiStreamingLanguageModel(String deploymentName,
                                              Tokenizer tokenizer,
                                              Integer maxTokens,
                                              Double temperature,
                                              Double topP,
                                              Map<String, Integer> logitBias,
                                              String user,
                                              Integer n,
                                              Integer logprobs,
                                              Boolean echo,
                                              List<String> stop,
                                              Double presencePenalty,
                                              Double frequencyPenalty) {

        this.deploymentName = getOrDefault(deploymentName, "gpt-35-turbo-instruct");
        this.tokenizer = tokenizer;
        this.maxTokens = maxTokens;
        this.temperature = getOrDefault(temperature, 0.7);
        this.topP = topP;
        this.logitBias = logitBias;
        this.user = user;
        this.n = n;
        this.logprobs = logprobs;
        this.echo = echo;
        this.stop = stop;
        this.presencePenalty = presencePenalty;
        this.frequencyPenalty = frequencyPenalty;
    }

    @Override
    public void generate(String prompt, StreamingResponseHandler<String> handler) {
        CompletionsOptions options = new CompletionsOptions(Collections.singletonList(prompt))
                .setStream(true)
                .setModel(deploymentName)
                .setMaxTokens(maxTokens)
                .setTemperature(temperature)
                .setTopP(topP)
                .setLogitBias(logitBias)
                .setUser(user)
                .setN(n)
                .setLogprobs(logprobs)
                .setEcho(echo)
                .setStop(stop)
                .setPresencePenalty(presencePenalty)
                .setFrequencyPenalty(frequencyPenalty);

        Integer inputTokenCount = tokenizer == null ? null : tokenizer.estimateTokenCountInText(prompt);
        AzureOpenAiStreamingResponseBuilder responseBuilder = new AzureOpenAiStreamingResponseBuilder(inputTokenCount);

        try {
            client.getCompletionsStream(deploymentName, options)
                    .stream()
                    .forEach(completions -> {
                        responseBuilder.append(completions);
                        handle(completions, handler);
                    });

            Response<AiMessage> response = responseBuilder.build(tokenizer, false);
            handler.onComplete(Response.from(
                    response.content().text(),
                    response.tokenUsage(),
                    response.finishReason()
            ));
        } catch (HttpResponseException httpResponseException) {
            logger.info("Error generating response, {}", httpResponseException.getValue());
            FinishReason exceptionFinishReason = contentFilterManagement(httpResponseException, "content_filter");
            Response<String> response = Response.from(
                    httpResponseException.getMessage(),
                    null,
                    exceptionFinishReason
            );

            handler.onComplete(response);
        } catch (Exception exception) {
            handler.onError(exception);
        }
    }

    private static void handle(Completions completions,
                               StreamingResponseHandler<String> handler) {

        List<Choice> choices = completions.getChoices();
        if (choices == null || choices.isEmpty()) {
            return;
        }
        String content = choices.get(0).getText();
        if (content != null) {
            handler.onNext(content);
        }
    }

    @Override
    public int estimateTokenCount(String prompt) {
        return tokenizer.estimateTokenCountInText(prompt);
    }

    public static Builder builder() {
        for (AzureOpenAiStreamingLanguageModelBuilderFactory factory : loadFactories(AzureOpenAiStreamingLanguageModelBuilderFactory.class)) {
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
        private String deploymentName;
        private Tokenizer tokenizer;
        private Integer maxTokens;
        private Double temperature;
        private Double topP;
        private Map<String, Integer> logitBias;
        private String user;
        private Integer n;
        private Integer logprobs;
        private Boolean echo;
        private List<String> stop;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private Duration timeout;
        private Integer maxRetries;
        private ProxyOptions proxyOptions;
        private boolean logRequestsAndResponses;
        private OpenAIClient openAIClient;

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

        public Builder tokenizer(Tokenizer tokenizer) {
            this.tokenizer = tokenizer;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder logitBias(Map<String, Integer> logitBias) {
            this.logitBias = logitBias;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder n(Integer n) {
            this.n = n;
            return this;
        }

        public Builder logprobs(Integer logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        public Builder echo(Boolean echo) {
            this.echo = echo;
            return this;
        }

        public Builder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public Builder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
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

        /**
         * Sets the Azure OpenAI client. This is an optional parameter, if you need more flexibility than using the endpoint, serviceVersion, apiKey, deploymentName parameters.
         *
         * @param openAIClient The Azure OpenAI client.
         * @return builder
         */
        public Builder openAIClient(OpenAIClient openAIClient) {
            this.openAIClient = openAIClient;
            return this;
        }

        public AzureOpenAiStreamingLanguageModel build() {
            if (openAIClient == null) {
                if (tokenCredential != null) {
                    return new AzureOpenAiStreamingLanguageModel(
                            endpoint,
                            serviceVersion,
                            tokenCredential,
                            deploymentName,
                            tokenizer,
                            maxTokens,
                            temperature,
                            topP,
                            logitBias,
                            user,
                            n,
                            logprobs,
                            echo,
                            stop,
                            presencePenalty,
                            frequencyPenalty,
                            timeout,
                            maxRetries,
                            proxyOptions,
                            logRequestsAndResponses
                    );
                } else if (keyCredential != null) {
                    return new AzureOpenAiStreamingLanguageModel(
                            endpoint,
                            serviceVersion,
                            keyCredential,
                            deploymentName,
                            tokenizer,
                            maxTokens,
                            temperature,
                            topP,
                            logitBias,
                            user,
                            n,
                            logprobs,
                            echo,
                            stop,
                            presencePenalty,
                            frequencyPenalty,
                            timeout,
                            maxRetries,
                            proxyOptions,
                            logRequestsAndResponses
                    );
                }
                return new AzureOpenAiStreamingLanguageModel(
                        endpoint,
                        serviceVersion,
                        apiKey,
                        deploymentName,
                        tokenizer,
                        maxTokens,
                        temperature,
                        topP,
                        logitBias,
                        user,
                        n,
                        logprobs,
                        echo,
                        stop,
                        presencePenalty,
                        frequencyPenalty,
                        timeout,
                        maxRetries,
                        proxyOptions,
                        logRequestsAndResponses
                );
            } else {
                return new AzureOpenAiStreamingLanguageModel(
                        openAIClient,
                        deploymentName,
                        tokenizer,
                        maxTokens,
                        temperature,
                        topP,
                        logitBias,
                        user,
                        n,
                        logprobs,
                        echo,
                        stop,
                        presencePenalty,
                        frequencyPenalty
                );
            }
        }
    }
}
