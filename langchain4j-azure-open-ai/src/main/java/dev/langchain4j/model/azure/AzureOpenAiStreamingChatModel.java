package dev.langchain4j.model.azure;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatChoice;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.FunctionCallConfig;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.http.HttpClient;
import com.azure.core.http.ProxyOptions;
import com.azure.core.http.netty.NettyAsyncHttpClientProvider;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.HttpLogOptions;
import com.azure.core.util.HttpClientOptions;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiTokenizer;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.azure.AzureOpenAiModelName.GPT_3_5_TURBO;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.getOpenAIServiceVersion;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.singletonList;

/**
 * Represents an OpenAI language model, hosted on Azure, that has a chat completion interface, such as gpt-3.5-turbo.
 * The model's response is streamed token by token and should be handled with {@link StreamingResponseHandler}.
 * <p>
 * Mandatory parameters for initialization are: endpoint, serviceVersion, apiKey and deploymentName.
 * <p>
 * There are two primary authentication methods to access Azure OpenAI:
 * <p>
 * 1. API Key Authentication: For this type of authentication, HTTP requests must include the
 * API Key in the "api-key" HTTP header as follows: `api-key: OPENAI_API_KEY`
 * <p>
 * 2. Azure Active Directory Authentication: For this type of authentication, HTTP requests must include the
 * authentication/access token in the "Authorization" HTTP header.
 * <p>
 * <a href="https://learn.microsoft.com/en-us/azure/ai-services/openai/reference">More information</a>
 * <p>
 * Please note, that currently, only API Key authentication is supported by this class,
 * second authentication option will be supported later.
 */
public class AzureOpenAiStreamingChatModel implements StreamingChatLanguageModel, TokenCountEstimator {

    private final OpenAIClient client;
    private final String deploymentName;
    private final String modelName;
    private final Tokenizer tokenizer;
    private final Double temperature;
    private final Double topP;
    private final Integer maxTokens;
    private final Double presencePenalty;
    private final Double frequencyPenalty;

    public AzureOpenAiStreamingChatModel(String endpoint,
                                         String serviceVersion,
                                         String apiKey,
                                         String deploymentName,
                                         String modelName,
                                         Tokenizer tokenizer,
                                         Double temperature,
                                         Double topP,
                                         Integer maxTokens,
                                         Double presencePenalty,
                                         Double frequencyPenalty,
                                         Duration timeout,
                                         ProxyOptions proxyOptions,
                                         Boolean logRequests) {

        timeout = getOrDefault(timeout, ofSeconds(60));

        HttpClientOptions clientOptions = new HttpClientOptions();
        clientOptions.setConnectTimeout(timeout);
        clientOptions.setResponseTimeout(timeout);
        clientOptions.setReadTimeout(timeout);
        clientOptions.setWriteTimeout(timeout);
        clientOptions.setProxyOptions(proxyOptions);

        HttpClient httpClient = new NettyAsyncHttpClientProvider().createInstance(clientOptions);

        HttpLogOptions httpLogOptions = new HttpLogOptions();
        if (logRequests != null) {
            httpLogOptions.setLogLevel(HttpLogDetailLevel.BODY_AND_HEADERS);
        }

        this.client = new OpenAIClientBuilder()
                .endpoint(ensureNotBlank(endpoint, "endpoint"))
                .credential(new AzureKeyCredential(apiKey))
                .serviceVersion(getOpenAIServiceVersion(serviceVersion))
                .httpClient(httpClient)
                .httpLogOptions(httpLogOptions)
                .buildClient();

        this.deploymentName = getOrDefault(deploymentName, "gpt-35-turbo-0613");
        this.modelName = getOrDefault(modelName, GPT_3_5_TURBO);
        this.tokenizer = getOrDefault(tokenizer, new OpenAiTokenizer(this.modelName));
        this.temperature = getOrDefault(temperature, 0.7);
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.presencePenalty = presencePenalty;
        this.frequencyPenalty = frequencyPenalty;
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, null, null, handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, toolSpecifications, null, handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, ToolSpecification toolSpecification, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, singletonList(toolSpecification), toolSpecification, handler);
    }

    private void generate(List<ChatMessage> messages,
                          List<ToolSpecification> toolSpecifications,
                          ToolSpecification toolThatMustBeExecuted,
                          StreamingResponseHandler<AiMessage> handler
    ) {
        ChatCompletionsOptions options = new ChatCompletionsOptions(InternalAzureOpenAiHelper.toOpenAiMessages(messages))
                .setStream(true)
                .setModel(deploymentName)
                .setTemperature(temperature)
                .setTopP(topP)
                .setMaxTokens(maxTokens)
                .setPresencePenalty(presencePenalty)
                .setFrequencyPenalty(frequencyPenalty);

        Integer inputTokenCount = tokenizer == null ? null : tokenizer.estimateTokenCountInMessages(messages);

        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
            options.setFunctions(InternalAzureOpenAiHelper.toFunctions(toolSpecifications));
            if (tokenizer != null) {
                inputTokenCount += tokenizer.estimateTokenCountInToolSpecifications(toolSpecifications);
            }
        }
        if (toolThatMustBeExecuted != null) {
            options.setFunctionCall(new FunctionCallConfig(toolThatMustBeExecuted.name()));
            if (tokenizer != null) {
                inputTokenCount += tokenizer.estimateTokenCountInToolSpecification(toolThatMustBeExecuted);
            }
        }

        AzureOpenAiStreamingResponseBuilder responseBuilder = new AzureOpenAiStreamingResponseBuilder(inputTokenCount);

        client.getChatCompletionsStream(deploymentName, options)
                .stream()
                .forEach(chatCompletions -> {
                    responseBuilder.append(chatCompletions);
                    handle(chatCompletions, handler);
                });

        handler.onComplete(responseBuilder.build());
    }

    private static void handle(ChatCompletions chatCompletions,
                               StreamingResponseHandler<AiMessage> handler) {

        List<ChatChoice> choices = chatCompletions.getChoices();
        if (choices == null || choices.isEmpty()) {
            return;
        }
        com.azure.ai.openai.models.ChatMessage delta = choices.get(0).getDelta();
        String content = delta.getContent();
        if (content != null) {
            handler.onNext(content);
        }
    }

    @Override
    public int estimateTokenCount(List<ChatMessage> messages) {
        return tokenizer.estimateTokenCountInMessages(messages);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String endpoint;
        private String serviceVersion;
        private String apiKey;
        private String deploymentName;
        private String modelName;
        private Tokenizer tokenizer;
        private Double temperature;
        private Double topP;
        private Integer maxTokens;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private Duration timeout;
        private ProxyOptions proxyOptions;
        private Boolean logRequests;

        /**
         * Sets the Azure OpenAI base URL. This is a mandatory parameter.
         *
         * @param endpoint The Azure OpenAI base URL in the format: https://{resource}.openai.azure.com/
         * @return builder
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Sets the Azure OpenAI API version. This is a mandatory parameter.
         *
         * @param serviceVersion The Azure OpenAI api version in the format: 2023-05-15
         * @return builder
         */
        public Builder serviceVersion(String serviceVersion) {
            this.serviceVersion = serviceVersion;
            return this;
        }

        /**
         * Sets the Azure OpenAI API key. This is a mandatory parameter.
         *
         * @param apiKey The Azure OpenAI API key.
         * @return builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
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
         * Sets the model name in Azure OpenAI. This is a mandatory parameter.
         *
         * @param modelName The model name.
         * @return builder
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder tokenizer(Tokenizer tokenizer) {
            this.tokenizer = tokenizer;
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

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
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

        public Builder ProxyOptions(ProxyOptions proxyOptions) {
            this.proxyOptions = proxyOptions;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public AzureOpenAiStreamingChatModel build() {
            return new AzureOpenAiStreamingChatModel(
                    endpoint,
                    serviceVersion,
                    apiKey,
                    deploymentName,
                    modelName,
                    tokenizer,
                    temperature,
                    topP,
                    maxTokens,
                    presencePenalty,
                    frequencyPenalty,
                    timeout,
                    proxyOptions,
                    logRequests
            );
        }
    }
}
