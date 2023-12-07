package dev.langchain4j.model.azure;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.EmbeddingItem;
import com.azure.ai.openai.models.Embeddings;
import com.azure.ai.openai.models.EmbeddingsOptions;
import com.azure.core.http.ProxyOptions;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.azure.AzureOpenAiModelName.TEXT_EMBEDDING_ADA_002;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.setupOpenAIClient;
import static java.util.stream.Collectors.toList;

/**
 * Represents an OpenAI embedding model, hosted on Azure, such as text-embedding-ada-002.
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
public class AzureOpenAiEmbeddingModel implements EmbeddingModel, TokenCountEstimator {

    private static final int BATCH_SIZE = 16;

    private OpenAIClient client;
    private final String deploymentName;
    private final Tokenizer tokenizer;

    private AzureOpenAiEmbeddingModel(OpenAIClient client,
                                      String deploymentName,
                                      Tokenizer tokenizer) {
        this(deploymentName, tokenizer);
        this.client = client;
    }

    public AzureOpenAiEmbeddingModel(String endpoint,
                                     String serviceVersion,
                                     String apiKey,
                                     String deploymentName,
                                     Tokenizer tokenizer,
                                     Duration timeout,
                                     Integer maxRetries,
                                     ProxyOptions proxyOptions,
                                     boolean logRequestsAndResponses) {

        this(deploymentName, tokenizer);
        this.client = setupOpenAIClient(endpoint, serviceVersion, apiKey, timeout, maxRetries, proxyOptions, logRequestsAndResponses);
    }

    private AzureOpenAiEmbeddingModel(String deploymentName,
                                      Tokenizer tokenizer) {

        this.deploymentName = getOrDefault(deploymentName, "text-embedding-ada-002");
        this.tokenizer = getOrDefault(tokenizer, new OpenAiTokenizer(TEXT_EMBEDDING_ADA_002));
    }

    /**
     * Embeds the provided text segments, processing a maximum of 16 segments at a time.
     * For more information, refer to the documentation <a href="https://learn.microsoft.com/en-us/azure/ai-services/openai/faq#i-am-trying-to-use-embeddings-and-received-the-error--invalidrequesterror--too-many-inputs--the-max-number-of-inputs-is-1---how-do-i-fix-this-">here</a>.
     *
     * @param textSegments A list of text segments.
     * @return A list of corresponding embeddings.
     */
    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        List<String> texts = textSegments.stream()
                .map(TextSegment::text)
                .collect(toList());

        return embedTexts(texts);
    }

    private Response<List<Embedding>> embedTexts(List<String> texts) {

        List<Embedding> embeddings = new ArrayList<>();

        int inputTokenCount = 0;
        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {

            List<String> batch = texts.subList(i, Math.min(i + BATCH_SIZE, texts.size()));

            EmbeddingsOptions options = new EmbeddingsOptions(batch);
            Embeddings response =  client.getEmbeddings(deploymentName, options);

            for (EmbeddingItem embeddingItem : response.getData()) {
                List<Double> openAiVector = embeddingItem.getEmbedding();
                float[] langChainVector = new float[openAiVector.size()];
                for (int index = 0; index < openAiVector.size(); index++) {
                    langChainVector[index] = openAiVector.get(index).floatValue();
                }
                Embedding langChainEmbedding = Embedding.from(langChainVector);
                embeddings.add(langChainEmbedding);
            }

            inputTokenCount += response.getUsage().getPromptTokens();
        }

        return Response.from(
                embeddings,
                new TokenUsage(inputTokenCount)
        );
    }

    @Override
    public int estimateTokenCount(String text) {
        return tokenizer.estimateTokenCountInText(text);
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
        private Duration timeout;
        private Integer maxRetries;
        private ProxyOptions proxyOptions;
        private boolean logRequestsAndResponses;
        private OpenAIClient openAIClient;

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

        public Builder tokenizer(Tokenizer tokenizer) {
            this.tokenizer = tokenizer;
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

        public Builder ProxyOptions(ProxyOptions proxyOptions) {
            this.proxyOptions = proxyOptions;
            return this;
        }

        public Builder logRequestsAndResponses(boolean logRequestsAndResponses) {
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

        public AzureOpenAiEmbeddingModel build() {
            if (openAIClient == null) {
                return new AzureOpenAiEmbeddingModel(
                        endpoint,
                        serviceVersion,
                        apiKey,
                        deploymentName,
                        tokenizer,
                        timeout,
                        maxRetries,
                        proxyOptions,
                        logRequestsAndResponses
                );
            } else {
                return new AzureOpenAiEmbeddingModel(
                        openAIClient,
                        deploymentName,
                        tokenizer
                );
            }
        }
    }
}
