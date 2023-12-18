package dev.langchain4j.model.azure;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.FunctionCallConfig;
import com.azure.core.http.ProxyOptions;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.model.output.Response;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.azure.AzureOpenAiModelName.GPT_3_5_TURBO;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.*;
import static java.util.Collections.singletonList;

/**
 * Represents an OpenAI language model, hosted on Azure, that has a chat completion interface, such as gpt-3.5-turbo.
 * <p>
 * Mandatory parameters for initialization are: endpoint, serviceVersion, apiKey and deploymentName.
 * You can also provide your own OpenAIClient instance, if you need more flexibility.
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
public class AzureOpenAiChatModel implements ChatLanguageModel, TokenCountEstimator {

    private OpenAIClient client;
    private final String deploymentName;
    private final Tokenizer tokenizer;
    private final Double temperature;
    private final Double topP;
    private final Integer maxTokens;
    private final Double presencePenalty;
    private final Double frequencyPenalty;

    public AzureOpenAiChatModel(OpenAIClient client,
                                String deploymentName,
                                Tokenizer tokenizer,
                                Double temperature,
                                Double topP,
                                Integer maxTokens,
                                Double presencePenalty,
                                Double frequencyPenalty) {

        this(deploymentName, tokenizer, temperature, topP, maxTokens, presencePenalty, frequencyPenalty);
        this.client = client;
    }

    public AzureOpenAiChatModel(String endpoint,
                                String serviceVersion,
                                String apiKey,
                                String deploymentName,
                                Tokenizer tokenizer,
                                Double temperature,
                                Double topP,
                                Integer maxTokens,
                                Double presencePenalty,
                                Double frequencyPenalty,
                                Duration timeout,
                                Integer maxRetries,
                                ProxyOptions proxyOptions,
                                boolean logRequestsAndResponses) {

        this(deploymentName, tokenizer, temperature, topP, maxTokens, presencePenalty, frequencyPenalty);
        this.client = setupOpenAIClient(endpoint, serviceVersion, apiKey, timeout, maxRetries, proxyOptions, logRequestsAndResponses);
    }



    private AzureOpenAiChatModel(String deploymentName,
                                 Tokenizer tokenizer,
                                 Double temperature,
                                 Double topP,
                                 Integer maxTokens,
                                 Double presencePenalty,
                                 Double frequencyPenalty) {

        this.deploymentName = getOrDefault(deploymentName, "gpt-35-turbo-0613");
        this.tokenizer = getOrDefault(tokenizer, new OpenAiTokenizer(GPT_3_5_TURBO));
        this.temperature = getOrDefault(temperature, 0.7);
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.presencePenalty = presencePenalty;
        this.frequencyPenalty = frequencyPenalty;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return generate(messages, null, null);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        return generate(messages, toolSpecifications, null);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages, singletonList(toolSpecification), toolSpecification);
    }

    private Response<AiMessage> generate(List<ChatMessage> messages,
                                         List<ToolSpecification> toolSpecifications,
                                         ToolSpecification toolThatMustBeExecuted
    ) {
        ChatCompletionsOptions options = new ChatCompletionsOptions(toOpenAiMessages(messages))
                .setModel(deploymentName)
                .setTemperature(temperature)
                .setTopP(topP)
                .setMaxTokens(maxTokens)
                .setPresencePenalty(presencePenalty)
                .setFrequencyPenalty(frequencyPenalty);

        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
            options.setFunctions(toFunctions(toolSpecifications));
        }
        if (toolThatMustBeExecuted != null) {
            options.setFunctionCall(new FunctionCallConfig(toolThatMustBeExecuted.name()));
        }

        ChatCompletions chatCompletions = client.getChatCompletions(deploymentName, options);

        return Response.from(
                aiMessageFrom(chatCompletions.getChoices().get(0).getMessage()),
                tokenUsageFrom(chatCompletions.getUsage()),
                finishReasonFrom(chatCompletions.getChoices().get(0).getFinishReason())
        );
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
        private Tokenizer tokenizer;
        private Double temperature;
        private Double topP;
        private Integer maxTokens;
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

        public AzureOpenAiChatModel build() {
            if (openAIClient == null) {
                return new AzureOpenAiChatModel(
                        endpoint,
                        serviceVersion,
                        apiKey,
                        deploymentName,
                        tokenizer,
                        temperature,
                        topP,
                        maxTokens,
                        presencePenalty,
                        frequencyPenalty,
                        timeout,
                        maxRetries,
                        proxyOptions,
                        logRequestsAndResponses
                );
            } else {
                return new AzureOpenAiChatModel(
                        openAIClient,
                        deploymentName,
                        tokenizer,
                        temperature,
                        topP,
                        maxTokens,
                        presencePenalty,
                        frequencyPenalty
                );
            }
        }
    }
}
