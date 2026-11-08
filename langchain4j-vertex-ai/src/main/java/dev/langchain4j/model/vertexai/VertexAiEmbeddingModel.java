package dev.langchain4j.model.vertexai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.vertexai.Json.toJson;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.stream.Collectors.toList;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.aiplatform.v1beta1.*;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.vertexai.spi.VertexAiEmbeddingModelBuilderFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Represents a Google Vertex AI embedding model, such as textembedding-gecko.
 * See details <a href="https://cloud.google.com/vertex-ai/docs/generative-ai/embeddings/get-text-embeddings">here</a>.
 * <br>
 * <br>
 * This embedding model transparently handles call batching, however the underlying API has imposes
 * a maximum of 250 embeddings per call, with a max of 20,000 tokens per call.
 * You can tweak those two parameters with the <code>maxSegmentsPerBatch()</code> and
 * <code>maxTokensPerBatch()</code> builder methods.
 * For example, if you hit the 20,000 error, set <code>maxTokensPerBatch(18_000)</code>.
 * <br>
 * <br>
 * For authentication and authorization, please follow these steps before using this model:
 * <br>
 * 1. <a href="https://github.com/googleapis/java-aiplatform?tab=readme-ov-file#authentication">Authentication</a>
 * <br>
 * When developing locally, you can use one of:
 * <br>
 * a) <a href="https://github.com/googleapis/google-cloud-java?tab=readme-ov-file#local-developmenttesting">Google Cloud SDK</a>
 * <br>
 * b) <a href="https://github.com/googleapis/google-cloud-java?tab=readme-ov-file#using-a-service-account-recommended">Service account</a>
 * When using service account, ensure that <code>GOOGLE_APPLICATION_CREDENTIALS</code> environment variable points to your JSON service account key.
 * <br>
 * 2. <a href="https://github.com/googleapis/java-aiplatform?tab=readme-ov-file#authorization">Authorization</a>
 * <br>
 * 3. <a href="https://github.com/googleapis/java-aiplatform?tab=readme-ov-file#prerequisites">Prerequisites</a>
 */
public class VertexAiEmbeddingModel extends DimensionAwareEmbeddingModel {

    private static final String DEFAULT_GOOGLEAPIS_ENDPOINT_SUFFIX = "-aiplatform.googleapis.com:443";

    private static final int COMPUTE_TOKENS_MAX_INPUTS_PER_REQUEST = 2_048;
    private static final int DEFAULT_MAX_SEGMENTS_PER_BATCH = 250;
    private static final int DEFAULT_MAX_TOKENS_PER_BATCH = 20_000;

    private final PredictionServiceSettings settings;
    private final LlmUtilityServiceSettings llmUtilitySettings;
    private final EndpointName endpointName;
    private final Integer maxRetries;
    private final Integer maxSegmentsPerBatch;
    private final Integer maxTokensPerBatch;
    private final TaskType taskType;
    private final String titleMetadataKey;
    private final Integer outputDimensionality;
    private final Boolean autoTruncate;
    private final String modelName;

    public enum TaskType {
        RETRIEVAL_QUERY,
        RETRIEVAL_DOCUMENT,
        SEMANTIC_SIMILARITY,
        CLASSIFICATION,
        CLUSTERING,
        QUESTION_ANSWERING,
        FACT_VERIFICATION,
        CODE_RETRIEVAL_QUERY
    }

    public VertexAiEmbeddingModel(Builder builder) {

        String regionWithBaseAPI = builder.endpoint != null
                ? builder.endpoint
                : ensureNotBlank(builder.location, "location") + DEFAULT_GOOGLEAPIS_ENDPOINT_SUFFIX;

        this.endpointName = EndpointName.ofProjectLocationPublisherModelName(
                ensureNotBlank(builder.project, "project"),
                builder.location,
                ensureNotBlank(builder.publisher, "publisher"),
                ensureNotBlank(builder.modelName, "modelName"));

        try {
            Optional<CredentialsProvider> credentialsProvider = Optional.empty();

            if (builder.credentials != null) {
                credentialsProvider = Optional.of(FixedCredentialsProvider.create(
                        builder.credentials.createScoped("https://www.googleapis.com/auth/cloud-platform")));
            }
            PredictionServiceSettings.Builder settingsBuilder =
                    PredictionServiceSettings.newBuilder().setEndpoint(regionWithBaseAPI);
            credentialsProvider.ifPresent(settingsBuilder::setCredentialsProvider);
            this.settings = settingsBuilder.build();

            LlmUtilityServiceSettings.Builder utilBuilder =
                    LlmUtilityServiceSettings.newBuilder().setEndpoint(settings.getEndpoint());
            credentialsProvider.ifPresent(utilBuilder::setCredentialsProvider);
            this.llmUtilitySettings = utilBuilder.build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.maxRetries = getOrDefault(builder.maxRetries, 2);

        this.maxSegmentsPerBatch = ensureGreaterThanZero(
                getOrDefault(builder.maxSegmentsPerBatch, DEFAULT_MAX_SEGMENTS_PER_BATCH), "maxSegmentsPerBatch");
        this.maxTokensPerBatch = ensureGreaterThanZero(
                getOrDefault(builder.maxTokensPerBatch, DEFAULT_MAX_TOKENS_PER_BATCH), "maxTokensPerBatch");

        this.taskType = builder.taskType;
        this.titleMetadataKey = getOrDefault(builder.titleMetadataKey, "title");

        this.outputDimensionality = builder.outputDimensionality;
        this.autoTruncate = getOrDefault(builder.autoTruncate, false);

        this.modelName = builder.modelName;
    }

    /**
     * @deprecated Please use {@link #VertexAiEmbeddingModel(Builder)} instead
     */
    @Deprecated(forRemoval = true, since = "1.2.0")
    public VertexAiEmbeddingModel(
            String endpoint,
            String project,
            String location,
            String publisher,
            String modelName,
            Integer maxRetries,
            Integer maxSegmentsPerBatch,
            Integer maxTokensPerBatch,
            TaskType taskType,
            String titleMetadataKey,
            Integer outputDimensionality,
            Boolean autoTruncate) {
        this(builder()
                .endpoint(endpoint)
                .project(project)
                .location(location)
                .publisher(publisher)
                .modelName(modelName)
                .maxRetries(maxRetries)
                .maxSegmentsPerBatch(maxSegmentsPerBatch)
                .maxTokensPerBatch(maxTokensPerBatch)
                .taskType(taskType)
                .titleMetadataKey(titleMetadataKey)
                .outputDimensionality(outputDimensionality)
                .autoTruncate(autoTruncate));
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {

        try (PredictionServiceClient client = PredictionServiceClient.create(settings)) {

            List<Embedding> embeddings = new ArrayList<>();
            int inputTokenCount = 0;

            List<Integer> tokensCounts = this.calculateTokensCounts(segments);
            List<Integer> batchSizes = groupByBatches(tokensCounts);

            for (int i = 0, j = 0; i < segments.size() && j < batchSizes.size(); i += batchSizes.get(j), j++) {
                List<TextSegment> batch = segments.subList(i, i + batchSizes.get(j));

                List<Value> instances = new ArrayList<>();
                for (TextSegment segment : batch) {
                    VertexAiEmbeddingInstance embeddingInstance = new VertexAiEmbeddingInstance(segment.text());
                    // Specify the type of embedding task when specified
                    if (this.taskType != null) {
                        embeddingInstance.setTaskType(taskType);
                        if (this.taskType.equals(TaskType.RETRIEVAL_DOCUMENT)) {
                            // Title metadata is used for calculating embeddings for document retrieval
                            embeddingInstance.setTitle(segment.metadata().getString(titleMetadataKey));
                        }
                    }

                    Value.Builder instanceBuilder = Value.newBuilder();
                    JsonFormat.parser().merge(toJson(embeddingInstance), instanceBuilder);
                    instances.add(instanceBuilder.build());
                }

                VertexAiEmbeddingParameters parameters =
                        new VertexAiEmbeddingParameters(outputDimensionality, getOrDefault(autoTruncate, false));
                Value.Builder parameterBuilder = Value.newBuilder();
                JsonFormat.parser().merge(toJson(parameters), parameterBuilder);

                PredictResponse response = withRetryMappingExceptions(
                        () -> client.predict(endpointName, instances, parameterBuilder.build()), maxRetries);

                embeddings.addAll(response.getPredictionsList().stream()
                        .map(VertexAiEmbeddingModel::toEmbedding)
                        .collect(toList()));

                for (Value prediction : response.getPredictionsList()) {
                    inputTokenCount += extractTokenCount(prediction);
                }
            }

            return Response.from(embeddings, new TokenUsage(inputTokenCount));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String modelName() {
        return this.modelName;
    }

    /**
     * Calculates the number of tokens for each segment in the input list.
     *
     * @param segments a list of TextSegments
     * @return a list of tokens counts for each segment
     */
    public List<Integer> calculateTokensCounts(List<TextSegment> segments) {
        try (LlmUtilityServiceClient utilClient = LlmUtilityServiceClient.create(this.llmUtilitySettings)) {
            List<Integer> tokensCounts = new ArrayList<>();

            // The computeTokens endpoint has a limit of up to 2048 input texts per request
            for (int i = 0; i < segments.size(); i += COMPUTE_TOKENS_MAX_INPUTS_PER_REQUEST) {
                List<TextSegment> batch =
                        segments.subList(i, Math.min(i + COMPUTE_TOKENS_MAX_INPUTS_PER_REQUEST, segments.size()));

                List<Value> instances = new ArrayList<>();
                for (TextSegment segment : batch) {
                    Value.Builder instanceBuilder = Value.newBuilder();
                    JsonFormat.parser().merge(toJson(new VertexAiEmbeddingInstance(segment.text())), instanceBuilder);
                    instances.add(instanceBuilder.build());
                }

                ComputeTokensRequest computeTokensRequest = ComputeTokensRequest.newBuilder()
                        .setEndpoint(endpointName.toString())
                        .addAllInstances(instances)
                        .build();

                ComputeTokensResponse computeTokensResponse = utilClient.computeTokens(computeTokensRequest);

                tokensCounts.addAll(computeTokensResponse.getTokensInfoList().stream()
                        .map(TokensInfo::getTokensCount)
                        .collect(toList()));
            }

            return tokensCounts;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Integer knownDimension() {
        return VertexAiEmbeddingModelName.knownDimension(endpointName.getModel());
    }

    private List<Integer> groupByBatches(List<Integer> tokensCounts) {
        // create a list of sublists of tokens counts
        // where the maximum number of text segments per sublist is 250
        // and the sum of the tokens counts in each sublist is less than 20_000

        List<List<Integer>> batches = new ArrayList<>();

        List<Integer> currentBatch = new ArrayList<>();
        int currentBatchSum = 0;
        for (Integer tokensCount : tokensCounts) {
            if (currentBatchSum + tokensCount <= maxTokensPerBatch && currentBatch.size() < maxSegmentsPerBatch) {
                currentBatch.add(tokensCount);
                currentBatchSum += tokensCount;
            } else {
                batches.add(currentBatch);
                currentBatch = new ArrayList<>();
                currentBatch.add(tokensCount);
                currentBatchSum = tokensCount;
            }
        }
        if (!currentBatch.isEmpty()) {
            batches.add(currentBatch);
        }

        // returns the list of number of text segments for each batch of embedding calculations

        return batches.stream().mapToInt(List::size).boxed().collect(toList());
    }

    private static Embedding toEmbedding(Value prediction) {

        List<Float> vector = prediction
                .getStructValue()
                .getFieldsMap()
                .get("embeddings")
                .getStructValue()
                .getFieldsOrThrow("values")
                .getListValue()
                .getValuesList()
                .stream()
                .map(v -> (float) v.getNumberValue())
                .collect(toList());

        return Embedding.from(vector);
    }

    private static int extractTokenCount(Value prediction) {
        return (int) prediction
                .getStructValue()
                .getFieldsMap()
                .get("embeddings")
                .getStructValue()
                .getFieldsMap()
                .get("statistics")
                .getStructValue()
                .getFieldsMap()
                .get("token_count")
                .getNumberValue();
    }

    public static Builder builder() {
        for (VertexAiEmbeddingModelBuilderFactory factory : loadFactories(VertexAiEmbeddingModelBuilderFactory.class)) {
            return factory.get();
        }
        return new Builder();
    }

    public static class Builder {

        private String endpoint;
        private String project;
        private String location;
        private String publisher;
        private String modelName;
        private Integer maxRetries;
        private Integer maxSegmentsPerBatch;
        private Integer maxTokensPerBatch;
        private TaskType taskType;
        private String titleMetadataKey;
        private Integer outputDimensionality;
        private Boolean autoTruncate;
        private GoogleCredentials credentials;

        /**
         * Sets an explicit Vertex AI API endpoint, overriding the default location-based endpoint
         * (e.g. {@code "us-central1-aiplatform.googleapis.com:443"}).
         * If not set, the endpoint is derived from the {@code location} field.
         *
         * @param endpoint the API endpoint
         * @return {@code this}
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Sets the Google Cloud project ID.
         *
         * @param project the project ID
         * @return {@code this}
         */
        public Builder project(String project) {
            this.project = project;
            return this;
        }

        /**
         * Sets the Google Cloud region, e.g. {@code "us-central1"}.
         *
         * @param location the cloud region
         * @return {@code this}
         */
        public Builder location(String location) {
            this.location = location;
            return this;
        }

        /**
         * Sets the model publisher. Use {@code "google"} for Vertex AI first-party models.
         *
         * @param publisher the publisher name
         * @return {@code this}
         */
        public Builder publisher(String publisher) {
            this.publisher = publisher;
            return this;
        }

        /**
         * Sets the embedding model name, e.g. {@code "textembedding-gecko"}.
         *
         * @param modelName the model name
         * @return {@code this}
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the maximum number of retries on transient errors. Defaults to {@code 2}.
         *
         * @param maxRetries the maximum number of retries
         * @return {@code this}
         */
        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets the maximum number of text segments sent in a single embedding API call.
         * Defaults to {@code 250} (the API maximum).
         *
         * @param maxBatchSize the maximum number of segments per batch
         * @return {@code this}
         */
        public Builder maxSegmentsPerBatch(Integer maxBatchSize) {
            this.maxSegmentsPerBatch = maxBatchSize;
            return this;
        }

        /**
         * Sets the maximum number of tokens sent in a single embedding API call.
         * Defaults to {@code 20000}. Reduce this value if you encounter token-limit errors.
         *
         * @param maxTokensPerBatch the maximum number of tokens per batch
         * @return {@code this}
         */
        public Builder maxTokensPerBatch(Integer maxTokensPerBatch) {
            this.maxTokensPerBatch = maxTokensPerBatch;
            return this;
        }

        /**
         * Sets the embedding task type, which optimises the embedding for a specific use case
         * such as retrieval, classification, or clustering.
         *
         * @param taskType the embedding task type
         * @return {@code this}
         */
        public Builder taskType(TaskType taskType) {
            this.taskType = taskType;
            return this;
        }

        /**
         * Sets the segment metadata key whose value is used as a document title when the task type is
         * {@code RETRIEVAL_DOCUMENT}. Defaults to {@code "title"}.
         *
         * @param titleMetadataKey the metadata key for the document title
         * @return {@code this}
         */
        public Builder titleMetadataKey(String titleMetadataKey) {
            this.titleMetadataKey = titleMetadataKey;
            return this;
        }

        /**
         * When {@code true}, automatically truncates input text that exceeds the model's token limit.
         * Defaults to {@code false}.
         *
         * @param autoTruncate {@code true} to enable automatic truncation
         * @return {@code this}
         */
        public Builder autoTruncate(Boolean autoTruncate) {
            this.autoTruncate = autoTruncate;
            return this;
        }

        /**
         * Sets the number of output embedding dimensions.
         * When set, the model compresses the embedding to this size.
         *
         * @param outputDimensionality the desired embedding dimension
         * @return {@code this}
         */
        public Builder outputDimensionality(Integer outputDimensionality) {
            this.outputDimensionality = outputDimensionality;
            return this;
        }

        /**
         * Sets the Google credentials used to authenticate API requests.
         * If not provided, Application Default Credentials are used.
         *
         * @param credentials the Google credentials
         * @return {@code this}
         */
        public Builder credentials(GoogleCredentials credentials) {
            this.credentials = credentials;
            return this;
        }

        public VertexAiEmbeddingModel build() {
            return new VertexAiEmbeddingModel(this);
        }
    }
}
