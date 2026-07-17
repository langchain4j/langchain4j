package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static java.util.Objects.isNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.Experimental;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.batch.BatchError;
import dev.langchain4j.model.batch.BatchItemResult;
import dev.langchain4j.model.batch.BatchPage;
import dev.langchain4j.model.batch.BatchPagination;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.batch.BatchState;
import dev.langchain4j.model.chat.BatchChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrock.model.CreateModelInvocationJobResponse;
import software.amazon.awssdk.services.bedrock.model.GetModelInvocationJobResponse;
import software.amazon.awssdk.services.bedrock.model.ModelInvocationJobStatus;
import software.amazon.awssdk.services.bedrock.model.ModelInvocationJobSummary;
import software.amazon.awssdk.services.bedrock.model.S3InputFormat;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * A {@link BatchChatModel} for the Amazon Bedrock
 * <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/batch-inference.html">batch inference API</a>,
 * which processes multiple chat requests asynchronously at a 50% lower price than on-demand for supported models.
 *
 * <p>Jobs are created with the {@code Converse} invocation type. Each {@link ChatRequest} is written as a JSONL
 * record to an Amazon S3 input location, a model invocation job is submitted, and the results are read back from the
 * S3 output location once the job completes. All requests in a batch run against the single model configured on this
 * batch model (via {@link Builder#modelId(String)}), matching the Bedrock one-model-per-job constraint.</p>
 *
 * <p>Bedrock batch inference does not support tool calling or structured output. A {@link ChatRequest} that specifies
 * tools or a JSON response format is rejected with an {@link UnsupportedFeatureException}.</p>
 *
 * @see BatchChatModel
 * @see BatchResponse
 */
@Experimental
public final class BedrockBatchChatModel implements BatchChatModel {

    // Bedrock requires an alphanumeric recordId; the submission index is encoded in it so results, whose order
    // Bedrock does not guarantee, can be restored to request order.
    private static final String RECORD_ID_PREFIX = "r";
    private static final String INPUT_FILE_NAME = "input.jsonl";
    private static final String RESULT_FILE_SUFFIX = ".jsonl.out";
    private static final String MANIFEST_FILE = "manifest.json.out";

    private static final ObjectMapper JSONL_MAPPER =
            new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final BedrockClient bedrockClient;
    private final S3Client s3Client;
    private final String modelId;
    private final String roleArn;
    private final S3Location outputLocation;
    private final S3Location inputLocation;
    private final ChatRequestParameters defaultRequestParameters;
    private final Integer timeoutDurationInHours;
    private final int maxRetries;

    private BedrockBatchChatModel(Builder builder) {
        this.modelId = ensureNotNull(builder.modelId, "modelId");
        this.roleArn = ensureNotNull(builder.roleArn, "roleArn");
        this.outputLocation = S3Location.parse(ensureNotNull(builder.outputS3Uri, "outputS3Uri"));
        this.inputLocation = builder.inputS3Uri != null ? S3Location.parse(builder.inputS3Uri) : outputLocation;
        this.defaultRequestParameters =
                getOrDefault(builder.defaultRequestParameters, DefaultChatRequestParameters.EMPTY);
        this.timeoutDurationInHours = builder.timeoutDurationInHours;
        this.maxRetries = getOrDefault(builder.maxRetries, 2);

        Region region = getOrDefault(builder.region, Region.US_EAST_1);
        AwsCredentialsProvider credentials =
                getOrDefault(builder.credentialsProvider, DefaultCredentialsProvider.create());
        boolean logRequests = getOrDefault(builder.logRequests, false);
        boolean logResponses = getOrDefault(builder.logResponses, false);
        Consumer<ClientOverrideConfiguration.Builder> overrideConfiguration = config -> {
            if (logRequests || logResponses) {
                config.addExecutionInterceptor(new AwsLoggingInterceptor(logRequests, logResponses, builder.logger));
            }
        };
        this.bedrockClient = isNull(builder.bedrockClient)
                ? BedrockClient.builder()
                        .region(region)
                        .credentialsProvider(credentials)
                        .overrideConfiguration(overrideConfiguration)
                        .build()
                : builder.bedrockClient;
        this.s3Client = isNull(builder.s3Client)
                ? S3Client.builder()
                        .region(region)
                        .credentialsProvider(credentials)
                        .overrideConfiguration(overrideConfiguration)
                        .build()
                : builder.s3Client;
    }

    @Override
    public BatchResponse<ChatResponse> submit(BatchRequest<ChatRequest> request) {
        List<ChatRequest> requests = request.requests();
        StringBuilder jsonl = new StringBuilder();
        for (int i = 0; i < requests.size(); i++) {
            ChatRequest effective = effectiveRequest(requests.get(i));
            validateSupported(effective);
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("recordId", recordId(i));
            record.put("modelInput", BedrockBatchConverseMapper.toModelInput(effective));
            jsonl.append(writeLine(record)).append('\n');
        }

        String jobName = "lc4j-batch-" + UUID.randomUUID();
        String inputKey = joinKey(inputLocation.key(), jobName, INPUT_FILE_NAME);
        s3Client.putObject(
                b -> b.bucket(inputLocation.bucket()).key(inputKey), RequestBody.fromString(jsonl.toString()));

        String inputUri = "s3://" + inputLocation.bucket() + "/" + inputKey;
        CreateModelInvocationJobResponse response = withRetryMappingExceptions(
                () -> bedrockClient.createModelInvocationJob(b -> b.jobName(jobName)
                        .roleArn(roleArn)
                        .modelId(modelId)
                        .timeoutDurationInHours(timeoutDurationInHours)
                        .inputDataConfig(in ->
                                in.s3InputDataConfig(s3 -> s3.s3Uri(inputUri).s3InputFormat(S3InputFormat.JSONL)))
                        .outputDataConfig(out -> out.s3OutputDataConfig(s3 -> s3.s3Uri(outputLocation.uri())))),
                maxRetries,
                BedrockExceptionMapper.INSTANCE);

        return BatchResponse.<ChatResponse>builder()
                .batchId(response.jobArn())
                .state(BatchState.PENDING)
                .results(List.of())
                .build();
    }

    @Override
    public BatchResponse<ChatResponse> retrieve(String batchId) {
        GetModelInvocationJobResponse job = withRetryMappingExceptions(
                () -> bedrockClient.getModelInvocationJob(b -> b.jobIdentifier(batchId)),
                maxRetries,
                BedrockExceptionMapper.INSTANCE);

        BatchState state = toBatchState(job.status());
        List<BatchItemResult<ChatResponse>> results = List.of();
        if (job.status() == ModelInvocationJobStatus.COMPLETED
                || job.status() == ModelInvocationJobStatus.PARTIALLY_COMPLETED) {
            results = readResults(batchId);
        }

        return BatchResponse.<ChatResponse>builder()
                .batchId(job.jobArn())
                .state(state)
                .results(results)
                .build();
    }

    @Override
    public void cancel(String batchId) {
        withRetryMappingExceptions(
                () -> bedrockClient.stopModelInvocationJob(b -> b.jobIdentifier(batchId)),
                maxRetries,
                BedrockExceptionMapper.INSTANCE);
    }

    @Override
    public BatchPage<ChatResponse> list(@Nullable BatchPagination pagination) {
        Integer pageSize = pagination != null ? pagination.pageSize() : null;
        String pageToken = pagination != null ? pagination.pageToken() : null;
        var response = withRetryMappingExceptions(
                () -> bedrockClient.listModelInvocationJobs(
                        b -> b.maxResults(pageSize).nextToken(pageToken)),
                maxRetries,
                BedrockExceptionMapper.INSTANCE);

        List<BatchResponse<ChatResponse>> batches = new ArrayList<>();
        for (ModelInvocationJobSummary summary : response.invocationJobSummaries()) {
            batches.add(BatchResponse.<ChatResponse>builder()
                    .batchId(summary.jobArn())
                    .state(toBatchState(summary.status()))
                    .results(List.of())
                    .build());
        }
        return new BatchPage<>(batches, response.nextToken());
    }

    public static Builder builder() {
        return new Builder();
    }

    private ChatRequest effectiveRequest(ChatRequest chatRequest) {
        return ChatRequest.builder()
                .messages(chatRequest.messages())
                .parameters(defaultRequestParameters.overrideWith(chatRequest.parameters()))
                .build();
    }

    private static void validateSupported(ChatRequest request) {
        if (!isNullOrEmpty(request.toolSpecifications())) {
            throw new UnsupportedFeatureException("Tool calling is not supported by Bedrock batch inference");
        }
        if (request.responseFormat() != null) {
            throw new UnsupportedFeatureException("Structured output is not supported by Bedrock batch inference");
        }
    }

    private List<BatchItemResult<ChatResponse>> readResults(String jobArn) {
        String jobId = jobArn.substring(jobArn.lastIndexOf('/') + 1);
        String prefix = joinKey(outputLocation.key(), jobId) + "/";
        List<String> resultKeys =
                s3Client.listObjectsV2(b -> b.bucket(outputLocation.bucket()).prefix(prefix)).contents().stream()
                        .map(S3Object::key)
                        // Skip the manifest.json.out summary; Bedrock writes one *.jsonl.out per input file.
                        .filter(key -> key.endsWith(RESULT_FILE_SUFFIX) && !key.endsWith(MANIFEST_FILE))
                        .toList();

        List<IndexedResult> indexed = new ArrayList<>();
        for (String resultKey : resultKeys) {
            String content = s3Client.getObjectAsBytes(
                            b -> b.bucket(outputLocation.bucket()).key(resultKey))
                    .asUtf8String();
            for (String line : content.split("\n")) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode node = readTree(line);
                String recordId = node.path("recordId").asText("");
                indexed.add(new IndexedResult(recordIdIndex(recordId), toItemResult(node)));
            }
        }
        // Bedrock does not guarantee output order, so restore it from the index encoded in each recordId.
        indexed.sort(Comparator.comparingInt(IndexedResult::index));
        return indexed.stream().map(IndexedResult::result).toList();
    }

    private BatchItemResult<ChatResponse> toItemResult(JsonNode record) {
        JsonNode modelOutput = record.get("modelOutput");
        if (modelOutput == null || modelOutput.isNull()) {
            String message = record.hasNonNull("error")
                    ? record.get("error").toString()
                    : "Record failed without a model output";
            return BatchItemResult.failure(new BatchError(0, message, null));
        }
        return BatchItemResult.success(BedrockBatchConverseMapper.toChatResponse(modelOutput, modelId));
    }

    private static String recordId(int index) {
        return RECORD_ID_PREFIX + String.format("%010d", index);
    }

    private static int recordIdIndex(String recordId) {
        if (recordId.startsWith(RECORD_ID_PREFIX)) {
            try {
                return Integer.parseInt(recordId.substring(RECORD_ID_PREFIX.length()));
            } catch (NumberFormatException ignored) {
                // recordId not produced by this model; keep it last rather than failing
            }
        }
        return Integer.MAX_VALUE;
    }

    static BatchState toBatchState(@Nullable ModelInvocationJobStatus status) {
        if (status == null) {
            return BatchState.UNSPECIFIED;
        }
        return switch (status) {
            case SUBMITTED, VALIDATING, SCHEDULED -> BatchState.PENDING;
            case IN_PROGRESS, STOPPING -> BatchState.RUNNING;
            case COMPLETED, PARTIALLY_COMPLETED -> BatchState.SUCCEEDED;
            case FAILED -> BatchState.FAILED;
            case STOPPED -> BatchState.CANCELLED;
            case EXPIRED -> BatchState.EXPIRED;
            case UNKNOWN_TO_SDK_VERSION -> BatchState.UNSPECIFIED;
        };
    }

    private static String writeLine(Object value) {
        try {
            return JSONL_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize batch record", e);
        }
    }

    private static JsonNode readTree(String json) {
        try {
            return JSONL_MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse batch result line", e);
        }
    }

    private static String joinKey(String... parts) {
        StringBuilder key = new StringBuilder();
        for (String part : parts) {
            String trimmed = part.replaceAll("^/+", "").replaceAll("/+$", "");
            if (trimmed.isEmpty()) {
                continue;
            }
            if (key.length() > 0) {
                key.append('/');
            }
            key.append(trimmed);
        }
        return key.toString();
    }

    private static <T> T ensureNotNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must be set");
        }
        return value;
    }

    private record IndexedResult(int index, BatchItemResult<ChatResponse> result) {}

    private record S3Location(String bucket, String key) {
        static S3Location parse(String uri) {
            if (!uri.startsWith("s3://")) {
                throw new IllegalArgumentException("Not an S3 URI (expected s3://bucket/key): " + uri);
            }
            String withoutScheme = uri.substring("s3://".length());
            int slash = withoutScheme.indexOf('/');
            if (slash < 0) {
                return new S3Location(withoutScheme, "");
            }
            return new S3Location(withoutScheme.substring(0, slash), withoutScheme.substring(slash + 1));
        }

        String uri() {
            return key.isEmpty() ? "s3://" + bucket : "s3://" + bucket + "/" + key;
        }
    }

    public static final class Builder {

        private BedrockClient bedrockClient;
        private S3Client s3Client;
        private Region region;
        private AwsCredentialsProvider credentialsProvider;
        private String modelId;
        private String roleArn;
        private String outputS3Uri;
        private String inputS3Uri;
        private ChatRequestParameters defaultRequestParameters;
        private Integer timeoutDurationInHours;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;

        private Builder() {}

        /**
         * Sets the {@link BedrockClient} used for job control-plane calls. If not set, one is created from
         * {@link #region(Region)} and {@link #credentialsProvider(AwsCredentialsProvider)}.
         *
         * @return {@code this}
         */
        public Builder bedrockClient(BedrockClient bedrockClient) {
            this.bedrockClient = bedrockClient;
            return this;
        }

        /**
         * Sets the {@link S3Client} used to write inputs and read outputs. If not set, one is created from
         * {@link #region(Region)} and {@link #credentialsProvider(AwsCredentialsProvider)}. The bucket must be in
         * the same region as the job.
         *
         * @return {@code this}
         */
        public Builder s3Client(S3Client s3Client) {
            this.s3Client = s3Client;
            return this;
        }

        /**
         * Sets the AWS region for the clients created when none are supplied. Defaults to {@code us-east-1}.
         *
         * @return {@code this}
         */
        public Builder region(Region region) {
            this.region = region;
            return this;
        }

        /**
         * Sets the credentials provider for the clients created when none are supplied.
         * Defaults to {@link DefaultCredentialsProvider}.
         *
         * @return {@code this}
         */
        public Builder credentialsProvider(AwsCredentialsProvider credentialsProvider) {
            this.credentialsProvider = credentialsProvider;
            return this;
        }

        /**
         * Sets the model that every request in the batch runs against (one model per job).
         *
         * @return {@code this}
         */
        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        /**
         * Sets the ARN of the service role Bedrock assumes to read the input and write the output in S3.
         *
         * @return {@code this}
         */
        public Builder roleArn(String roleArn) {
            this.roleArn = roleArn;
            return this;
        }

        /**
         * Sets the S3 URI (for example {@code s3://my-bucket/batch-output}) where Bedrock writes the results.
         *
         * @return {@code this}
         */
        public Builder outputS3Uri(String outputS3Uri) {
            this.outputS3Uri = outputS3Uri;
            return this;
        }

        /**
         * Sets the S3 URI under which the generated input file is uploaded. Defaults to {@link #outputS3Uri(String)}.
         *
         * @return {@code this}
         */
        public Builder inputS3Uri(String inputS3Uri) {
            this.inputS3Uri = inputS3Uri;
            return this;
        }

        /**
         * Sets common default {@link ChatRequestParameters}; per-request parameters take precedence over these.
         *
         * @return {@code this}
         */
        public Builder defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return this;
        }

        /**
         * Sets the job timeout in hours, after which unprocessed records expire.
         *
         * @return {@code this}
         */
        public Builder timeoutDurationInHours(Integer timeoutDurationInHours) {
            this.timeoutDurationInHours = timeoutDurationInHours;
            return this;
        }

        /**
         * Sets the number of times to retry a control-plane call on transient errors. Defaults to {@code 2}.
         *
         * @return {@code this}
         */
        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Enables logging of the control-plane and S3 requests. Defaults to {@code false}.
         *
         * @return {@code this}
         */
        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Enables logging of the control-plane and S3 responses. Defaults to {@code false}.
         *
         * @return {@code this}
         */
        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * Sets the {@link Logger} used when {@link #logRequests(Boolean)} or {@link #logResponses(Boolean)} is enabled.
         *
         * @return {@code this}
         */
        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public BedrockBatchChatModel build() {
            return new BedrockBatchChatModel(this);
        }
    }
}
