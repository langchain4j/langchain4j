package dev.langchain4j.model.vertexai;

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

import static dev.langchain4j.internal.Json.toJson;
import static dev.langchain4j.model.chat.policy.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.stream.Collectors.toList;

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

    public enum TaskType {
        RETRIEVAL_QUERY, RETRIEVAL_DOCUMENT, SEMANTIC_SIMILARITY, CLASSIFICATION,
        CLUSTERING, QUESTION_ANSWERING, FACT_VERIFICATION, CODE_RETRIEVAL_QUERY
    }

    public VertexAiEmbeddingModel(String endpoint,
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

        String regionWithBaseAPI = endpoint != null ? endpoint :
            ensureNotBlank(location, "location") + DEFAULT_GOOGLEAPIS_ENDPOINT_SUFFIX;

        this.endpointName = EndpointName.ofProjectLocationPublisherModelName(
                ensureNotBlank(project, "project"),
                location,
                ensureNotBlank(publisher, "publisher"),
                ensureNotBlank(modelName, "modelName")
        );

        try {
            this.settings = PredictionServiceSettings.newBuilder()
                    .setEndpoint(regionWithBaseAPI)
                    .build();

            this.llmUtilitySettings = LlmUtilityServiceSettings.newBuilder()
                    .setEndpoint(settings.getEndpoint())
                    .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.maxRetries = getOrDefault(maxRetries, 3);

        this.maxSegmentsPerBatch = ensureGreaterThanZero(
                getOrDefault(maxSegmentsPerBatch, DEFAULT_MAX_SEGMENTS_PER_BATCH), "maxSegmentsPerBatch");
        this.maxTokensPerBatch = ensureGreaterThanZero(
                getOrDefault(maxTokensPerBatch, DEFAULT_MAX_TOKENS_PER_BATCH), "maxTokensPerBatch");

        this.taskType = taskType;
        this.titleMetadataKey = getOrDefault(titleMetadataKey, "title");

        this.outputDimensionality = outputDimensionality;
        this.autoTruncate = getOrDefault(autoTruncate, false);
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

                VertexAiEmbeddingParameters parameters = new VertexAiEmbeddingParameters(
                    outputDimensionality, getOrDefault(autoTruncate, false));
                Value.Builder parameterBuilder = Value.newBuilder();
                JsonFormat.parser().merge(toJson(parameters), parameterBuilder);

                PredictResponse response = withRetry(() -> client.predict(endpointName, instances, parameterBuilder.build()), maxRetries);

                embeddings.addAll(response.getPredictionsList().stream()
                        .map(VertexAiEmbeddingModel::toEmbedding)
                        .collect(toList()));

                for (Value prediction : response.getPredictionsList()) {
                    inputTokenCount += extractTokenCount(prediction);
                }
            }

            return Response.from(
                    embeddings,
                    new TokenUsage(inputTokenCount)
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
                List<TextSegment> batch = segments.subList(i,
                        Math.min(i + COMPUTE_TOKENS_MAX_INPUTS_PER_REQUEST, segments.size()));

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

                tokensCounts.addAll(computeTokensResponse
                        .getTokensInfoList()
                        .stream()
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
            if (currentBatchSum + tokensCount <= maxTokensPerBatch &&
                    currentBatch.size() < maxSegmentsPerBatch) {
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

        return batches.stream()
                .mapToInt(List::size)
                .boxed()
                .collect(toList());
    }

    private static Embedding toEmbedding(Value prediction) {

        List<Float> vector = prediction.getStructValue()
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
        return (int) prediction.getStructValue()
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

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder project(String project) {
            this.project = project;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder publisher(String publisher) {
            this.publisher = publisher;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder maxSegmentsPerBatch(Integer maxBatchSize) {
            this.maxSegmentsPerBatch = maxBatchSize;
            return this;
        }

        public Builder maxTokensPerBatch(Integer maxTokensPerBatch) {
            this.maxTokensPerBatch = maxTokensPerBatch;
            return this;
        }

        public Builder taskType(TaskType taskType) {
            this.taskType = taskType;
            return this;
        }

        public Builder titleMetadataKey(String titleMetadataKey) {
            this.titleMetadataKey = titleMetadataKey;
            return this;
        }

        public Builder autoTruncate(Boolean autoTruncate) {
            this.autoTruncate = autoTruncate;
            return this;
        }

        public Builder outputDimensionality(Integer outputDimensionality) {
            this.outputDimensionality = outputDimensionality;
            return this;
        }

        public VertexAiEmbeddingModel build() {
            return new VertexAiEmbeddingModel(
                    endpoint,
                    project,
                    location,
                    publisher,
                    modelName,
                    maxRetries,
                    maxSegmentsPerBatch,
                    maxTokensPerBatch,
                    taskType,
                    titleMetadataKey,
                    outputDimensionality,
                    autoTruncate
                );
        }
    }
}
