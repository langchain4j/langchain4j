package dev.langchain4j.model.azure;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.EmbeddingItem;
import com.azure.ai.openai.models.Embeddings;
import com.azure.ai.openai.models.EmbeddingsOptions;
import com.azure.core.credential.KeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.core.http.ProxyOptions;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.azure.spi.AzureOpenAiEmbeddingModelBuilderFactory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.spi.ServiceHelper;

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
 * Mandatory parameters for initialization are: endpoint, serviceVersion, apikey (or an alternate authentication method, see below for more information) and deploymentName.
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

    public AzureOpenAiEmbeddingModel(String endpoint,
                                     String serviceVersion,
                                     KeyCredential keyCredential,
                                     String deploymentName,
                                     Tokenizer tokenizer,
                                     Duration timeout,
                                     Integer maxRetries,
                                     ProxyOptions proxyOptions,
                                     boolean logRequestsAndResponses) {

        this(deploymentName, tokenizer);
        this.client = setupOpenAIClient(endpoint, serviceVersion, keyCredential, timeout, maxRetries, proxyOptions, logRequestsAndResponses);
    }

    public AzureOpenAiEmbeddingModel(String endpoint,
                                     String serviceVersion,
                                     TokenCredential tokenCredential,
                                     String deploymentName,
                                     Tokenizer tokenizer,
                                     Duration timeout,
                                     Integer maxRetries,
                                     ProxyOptions proxyOptions,
                                     boolean logRequestsAndResponses) {

        this(deploymentName, tokenizer);
        this.client = setupOpenAIClient(endpoint, serviceVersion, tokenCredential, timeout, maxRetries, proxyOptions, logRequestsAndResponses);
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
                Embedding embedding = from(embeddingItem.getEmbedding());
                embeddings.add(embedding);
            }

            inputTokenCount += response.getUsage().getPromptTokens();
        }

        return Response.from(
                embeddings,
                new TokenUsage(inputTokenCount)
        );
    }

    private static Embedding from(List<Double> vector) {
        float[] langChainVector = new float[vector.size()];
        for (int index = 0; index < vector.size(); index++) {
            langChainVector[index] = vector.get(index).floatValue();
        }
        return Embedding.from(langChainVector);
    }

    @Override
    public int estimateTokenCount(String text) {
        return tokenizer.estimateTokenCountInText(text);
    }

    public static Builder builder() {
        return ServiceHelper.loadFactoryService(
                AzureOpenAiEmbeddingModelBuilderFactory.class,
                Builder::new
        );
    }

    public static class Builder {

        private String endpoint;
        private String serviceVersion;
        private String apiKey;
        private KeyCredential keyCredential;
        private TokenCredential tokenCredential;
        private String deploymentName;
        private Tokenizer tokenizer;
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
                if (tokenCredential != null) {
                    return new AzureOpenAiEmbeddingModel(
                            endpoint,
                            serviceVersion,
                            tokenCredential,
                            deploymentName,
                            tokenizer,
                            timeout,
                            maxRetries,
                            proxyOptions,
                            logRequestsAndResponses
                    );
                } else if (keyCredential != null) {
                    return new AzureOpenAiEmbeddingModel(
                            endpoint,
                            serviceVersion,
                            keyCredential,
                            deploymentName,
                            tokenizer,
                            timeout,
                            maxRetries,
                            proxyOptions,
                            logRequestsAndResponses
                    );
                }
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
