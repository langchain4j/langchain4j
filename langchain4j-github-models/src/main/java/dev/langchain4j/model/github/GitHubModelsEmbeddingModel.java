package dev.langchain4j.model.github;

import com.azure.ai.inference.EmbeddingsClient;
import com.azure.ai.inference.ModelServiceVersion;
import com.azure.ai.inference.models.EmbeddingItem;
import com.azure.ai.inference.models.EmbeddingsResult;
import com.azure.core.http.ProxyOptions;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.github.spi.GitHubModelsEmbeddingModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.data.embedding.Embedding.from;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.github.InternalGitHubModelHelper.setupEmbeddingsBuilder;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.stream.Collectors.toList;

/**
 * Represents an embedding model, hosted on GitHub Models, such as text-embedding-3-small.
 * <p>
 * Mandatory parameters for initialization are: gitHubToken (the GitHub Token used for authentication) and modelName (the name of the model to use).
 * You can also provide your own EmbeddingsClient instance, if you need more flexibility.
 * <p>
 * The list of models, as well as the documentation and a playground to test them, can be found at https://github.com/marketplace/models
 */
public class GitHubModelsEmbeddingModel extends DimensionAwareEmbeddingModel {

    private static final Logger logger = LoggerFactory.getLogger(GitHubModelsEmbeddingModel.class);

    private static final int BATCH_SIZE = 16;

    private EmbeddingsClient client;
    private final String modelName;
    private final Integer dimensions;

    private GitHubModelsEmbeddingModel(EmbeddingsClient client,
                                       String modelName,
                                       Integer dimensions) {
        this(modelName, dimensions);
        this.client = client;
    }

    private GitHubModelsEmbeddingModel(String endpoint,
                                      ModelServiceVersion serviceVersion,
                                      String gitHubToken,
                                      String modelName,
                                      Duration timeout,
                                      Integer maxRetries,
                                      ProxyOptions proxyOptions,
                                      boolean logRequestsAndResponses,
                                      String userAgentSuffix,
                                      Integer dimensions,
                                      Map<String, String> customHeaders) {

        this(modelName, dimensions);
        this.client = setupEmbeddingsBuilder(endpoint, serviceVersion, gitHubToken, timeout, maxRetries, proxyOptions, logRequestsAndResponses, userAgentSuffix, customHeaders)
                .buildClient();
    }

    private GitHubModelsEmbeddingModel(String modelName, Integer dimensions) {
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.dimensions = dimensions;
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

            EmbeddingsResult result = client.embed(batch, dimensions, null, null, modelName, null);
            for (EmbeddingItem embeddingItem : result.getData()) {
                Embedding embedding = from(embeddingItem.getEmbeddingList());
                embeddings.add(embedding);
            }
            inputTokenCount += result.getUsage().getPromptTokens();
        }

        return Response.from(
                embeddings,
                new TokenUsage(inputTokenCount)
        );
    }

    @Override
    protected Integer knownDimension() {
        if (dimensions != null) {
            return dimensions;
        }
        return GitHubModelsEmbeddingModelName.knownDimension(modelName);
    }

    public static Builder builder() {
        for (GitHubModelsEmbeddingModelBuilderFactory factory : loadFactories(GitHubModelsEmbeddingModelBuilderFactory.class)) {
            return factory.get();
        }
        return new Builder();
    }

    public static class Builder {

        private String endpoint;
        private ModelServiceVersion serviceVersion;
        private String gitHubToken;
        private String modelName;
        private Duration timeout;
        private Integer maxRetries;
        private ProxyOptions proxyOptions;
        private boolean logRequestsAndResponses;
        private EmbeddingsClient embeddingsClient;
        private String userAgentSuffix;
        private Integer dimensions;
        private Map<String, String> customHeaders;

        /**
         * Sets the GitHub Models endpoint. The default endpoint will be used if this isn't set.
         *
         * @param endpoint The GitHub Models endpoint in the format: https://models.inference.ai.azure.com
         * @return builder
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Sets the Azure OpenAI API service version. If left blank, the latest service version will be used.
         *
         * @param serviceVersion The Azure OpenAI API service version in the format: 2023-05-15
         * @return builder
         */
        public Builder serviceVersion(ModelServiceVersion serviceVersion) {
            this.serviceVersion = serviceVersion;
            return this;
        }

        /**
         * Sets the GitHub token to access GitHub Models.
         *
         * @param gitHubToken The GitHub token.
         * @return builder
         */
        public Builder gitHubToken(String gitHubToken) {
            this.gitHubToken = gitHubToken;
            return this;
        }

        /**
         * Sets the model name in Azure AI Inference API. This is a mandatory parameter.
         *
         * @param modelName The Model name.
         * @return builder
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder modelName(GitHubModelsEmbeddingModelName modelName) {
            this.modelName = modelName.toString();
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
         * Sets the Azure AI Inference API client. This is an optional parameter, if you need more flexibility than the common parameters.
         *
         * @param embeddingsClient The Azure AI Inference API client.
         * @return builder
         */
        public Builder embeddingsClient(EmbeddingsClient embeddingsClient) {
            this.embeddingsClient = embeddingsClient;
            return this;
        }

        public Builder userAgentSuffix(String userAgentSuffix) {
            this.userAgentSuffix = userAgentSuffix;
            return this;
        }

        public Builder dimensions(Integer dimensions){
            this.dimensions = dimensions;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public GitHubModelsEmbeddingModel build() {
            if (embeddingsClient == null) {
                return new GitHubModelsEmbeddingModel(
                            endpoint,
                            serviceVersion,
                            gitHubToken,
                            modelName,
                            timeout,
                            maxRetries,
                            proxyOptions,
                            logRequestsAndResponses,
                            userAgentSuffix,
                            dimensions,
                            customHeaders);
            } else {
                return new GitHubModelsEmbeddingModel(
                        embeddingsClient,
                        modelName,
                        dimensions
                );
            }
        }
    }
}
