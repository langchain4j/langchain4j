package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.bedrock.AbstractBedrockChatModel.validate;
import static dev.langchain4j.model.bedrock.BedrockBatchConverseMapper.fromJsonLine;
import static dev.langchain4j.model.bedrock.BedrockBatchConverseMapper.integer;
import static dev.langchain4j.model.bedrock.BedrockBatchConverseMapper.map;
import static dev.langchain4j.model.bedrock.BedrockBatchConverseMapper.string;
import static dev.langchain4j.model.bedrock.BedrockBatchConverseMapper.toJsonLine;
import static java.util.Objects.isNull;

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
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * record to Amazon S3, a model invocation job is submitted, and the results are read back from the S3 output
 * location. All requests in a batch run against the single model configured on this batch model, because Bedrock
 * runs one model per job.</p>
 *
 * <p>Bedrock batch inference does not support tool calling, structured output or prompt caching, so a
 * {@link ChatRequest} that specifies tools, a JSON response format or cache points is rejected with an
 * {@link UnsupportedFeatureException}.</p>
 *
 * @see BatchChatModel
 * @see BatchResponse
 */
@Experimental
public final class BedrockBatchChatModel implements BatchChatModel {

    private static final Logger log = LoggerFactory.getLogger(BedrockBatchChatModel.class);

    private static final String RECORD_ID_PREFIX = "r";
    private static final String INPUT_FILE_NAME = "input.jsonl";
    private static final String RESULT_FILE_SUFFIX = ".jsonl.out";
    private static final String MANIFEST_FILE_SUFFIX = "manifest.json.out";
    static final String PROMPT_CACHING_NOT_SUPPORTED = "Prompt caching is not supported by Bedrock batch inference";
    private static final int NO_ERROR_CODE = 0;
    private static final String MISSING_RESULT_MESSAGE = "No result was returned for this request";
    private static final String UNREADABLE_RESULT_MESSAGE = "The result line could not be parsed";
    private static final String UNKNOWN_ERROR_MESSAGE = "The record failed without a model output";

    private final BedrockClient bedrockClient;
    private final S3Client s3Client;
    private final String modelId;
    private final String roleArn;
    private final S3Location outputLocation;
    private final S3Location inputLocation;
    private final ChatRequestParameters defaultRequestParameters;
    private final Duration jobTimeout;
    private final int maxRetries;
    private final boolean returnThinking;
    private final boolean sendThinking;

    private BedrockBatchChatModel(Builder builder) {
        if (builder.defaultRequestParameters != null) {
            validate(builder.defaultRequestParameters);
        }
        ChatRequestParameters configuredDefaults =
                getOrDefault(builder.defaultRequestParameters, DefaultChatRequestParameters.EMPTY);
        this.modelId = ensureNotBlank(getOrDefault(builder.modelId, configuredDefaults.modelName()), "modelId");
        this.defaultRequestParameters = configuredDefaults.overrideWith(
                ChatRequestParameters.builder().modelName(modelId).build());
        this.roleArn = ensureNotBlank(builder.roleArn, "roleArn");
        this.outputLocation = S3Location.parse(ensureNotBlank(builder.outputS3Uri, "outputS3Uri"));
        this.inputLocation = builder.inputS3Uri != null ? S3Location.parse(builder.inputS3Uri) : outputLocation;
        this.jobTimeout = builder.jobTimeout;
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.returnThinking = getOrDefault(builder.returnThinking, false);
        this.sendThinking = getOrDefault(builder.sendThinking, true);

        Region region = getOrDefault(builder.region, Region.US_EAST_1);
        AwsCredentialsProvider credentials =
                getOrDefault(builder.credentialsProvider, DefaultCredentialsProvider.create());
        boolean logRequests = getOrDefault(builder.logRequests, false);
        boolean logResponses = getOrDefault(builder.logResponses, false);
        Consumer<ClientOverrideConfiguration.Builder> commonConfiguration = config -> {
            if (builder.timeout != null) {
                config.apiCallTimeout(builder.timeout);
            }
            if (logRequests || logResponses) {
                config.addExecutionInterceptor(new AwsLoggingInterceptor(logRequests, logResponses, builder.logger));
            }
        };
        this.bedrockClient = isNull(builder.bedrockClient)
                ? BedrockClient.builder()
                        .region(region)
                        .credentialsProvider(credentials)
                        .overrideConfiguration(config -> {
                            commonConfiguration.accept(config);
                            if (builder.customHeadersSupplier != null) {
                                config.addExecutionInterceptor(
                                        new BedrockCustomHeadersInterceptor(builder.customHeadersSupplier));
                            }
                        })
                        .build()
                : builder.bedrockClient;
        this.s3Client = isNull(builder.s3Client)
                ? S3Client.builder()
                        .region(region)
                        .credentialsProvider(credentials)
                        .overrideConfiguration(commonConfiguration)
                        .build()
                : builder.s3Client;
    }

    /**
     * Writes the requests as one JSONL file to Amazon S3 and creates a Bedrock model invocation job for it.
     *
     * <p>The input file is uploaded under {@link Builder#inputS3Uri(String)} and is not deleted afterwards, so it
     * is stored and billed by S3 until you remove it; the same applies to the output Bedrock writes. Bedrock
     * enforces a minimum and a maximum number of records per job as service quotas, and rejects a job that falls
     * outside them.</p>
     */
    @Override
    public BatchResponse<ChatResponse> submit(BatchRequest<ChatRequest> request) {
        List<ChatRequest> requests = request.requests();
        List<ChatRequest> effectiveRequests = requests.stream()
                .map(chatRequest -> ChatRequest.builder()
                        .messages(chatRequest.messages())
                        .parameters(defaultRequestParameters.overrideWith(chatRequest.parameters()))
                        .build())
                .toList();
        effectiveRequests.forEach(this::validateSupported);

        StringBuilder jsonl = new StringBuilder();
        for (int i = 0; i < requests.size(); i++) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("recordId", recordId(i));
            record.put(
                    "modelInput",
                    BedrockBatchConverseMapper.toModelInput(
                            effectiveRequests.get(i),
                            additionalModelRequestFields(requests.get(i).parameters()),
                            sendThinking));
            jsonl.append(toJsonLine(record)).append('\n');
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
                        .timeoutDurationInHours(jobTimeoutInHours())
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

    /**
     * Retrieves the job and, once it has ended, its results.
     *
     * <p>Results are read for every ended job that produced output, which includes the partial results of a
     * stopped or expired job, since Bedrock charges for the records it processed. Every parsed result is held
     * in memory.</p>
     *
     * <p>Results are correlated by {@code recordId}, using the identifiers that {@link #submit(BatchRequest)}
     * generates, and a request that produced no result is represented by a failure. If any identifier is missing,
     * malformed, duplicated or outside the submitted range - which happens for a job that was not submitted
     * through this model - correlation is abandoned for the whole job and the results are returned in the order
     * the output files list them, with a warning logged. Results are never discarded.</p>
     *
     * <p>{@link BatchError#code()} holds the {@code errorCode} Bedrock reports for a record that failed, and
     * {@code 0} for a request that produced no result or for a job-level failure.</p>
     */
    @Override
    public BatchResponse<ChatResponse> retrieve(String batchId) {
        GetModelInvocationJobResponse job = withRetryMappingExceptions(
                () -> bedrockClient.getModelInvocationJob(b -> b.jobIdentifier(batchId)),
                maxRetries,
                BedrockExceptionMapper.INSTANCE);

        return BatchResponse.<ChatResponse>builder()
                .batchId(job.jobArn())
                .state(toBatchState(job.status()))
                .results(hasEnded(job.status()) ? readResults(job) : List.of())
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

    private void validateSupported(ChatRequest request) {
        validate(request.parameters());
        if (!isNullOrEmpty(request.toolSpecifications())) {
            throw new UnsupportedFeatureException("Tool calling is not supported by Bedrock batch inference");
        }
        if (request.responseFormat() != null && request.responseFormat().type() == ResponseFormatType.JSON) {
            throw new UnsupportedFeatureException("Structured output is not supported by Bedrock batch inference");
        }
        if (request.parameters() instanceof BedrockChatRequestParameters parameters) {
            if (parameters.cachePointPlacement() != null || parameters.cacheTtl() != null) {
                throw new UnsupportedFeatureException(PROMPT_CACHING_NOT_SUPPORTED);
            }
            if (parameters.serviceTier() != null) {
                throw new UnsupportedFeatureException("serviceTier is not supported by BedrockBatchChatModel");
            }
        }
        String modelName = request.parameters().modelName();
        if (modelName != null && !modelName.equals(modelId)) {
            throw new UnsupportedFeatureException("A Bedrock batch job runs every request against one model, '"
                    + modelId + "', so a request cannot use '" + modelName + "'");
        }
    }

    private Map<String, Object> additionalModelRequestFields(@Nullable ChatRequestParameters requestParameters) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (defaultRequestParameters instanceof BedrockChatRequestParameters defaults
                && defaults.additionalModelRequestFields() != null) {
            fields.putAll(defaults.additionalModelRequestFields());
        }
        if (requestParameters instanceof BedrockChatRequestParameters parameters
                && parameters.additionalModelRequestFields() != null) {
            fields.putAll(parameters.additionalModelRequestFields());
        }
        return fields;
    }

    private @Nullable Integer jobTimeoutInHours() {
        if (jobTimeout == null) {
            return null;
        }
        long hours = jobTimeout.toHours();
        return (int) (jobTimeout.equals(Duration.ofHours(hours)) ? hours : hours + 1);
    }

    private List<BatchItemResult<ChatResponse>> readResults(GetModelInvocationJobResponse job) {
        String jobId = job.jobArn().substring(job.jobArn().lastIndexOf('/') + 1);
        String prefix = joinKey(outputLocation.key(), jobId) + "/";
        List<String> keys =
                s3Client.listObjectsV2(b -> b.bucket(outputLocation.bucket()).prefix(prefix)).contents().stream()
                        .map(S3Object::key)
                        .toList();

        List<ResultLine> resultLines = new ArrayList<>();
        Integer totalRecordCount = null;
        for (String key : keys) {
            if (key.endsWith(MANIFEST_FILE_SUFFIX)) {
                totalRecordCount = totalRecordCount(key);
            } else if (key.endsWith(RESULT_FILE_SUFFIX)) {
                for (String line : read(key).split("\n")) {
                    if (!line.isBlank()) {
                        resultLines.add(toResultLine(line));
                    }
                }
            }
        }

        if (resultLines.isEmpty()) {
            return job.status() == ModelInvocationJobStatus.FAILED
                    ? List.of(failure(NO_ERROR_CODE, getOrDefault(job.message(), UNKNOWN_ERROR_MESSAGE)))
                    : List.of();
        }

        int requestCount = totalRecordCount != null ? totalRecordCount : highestIndex(resultLines) + 1;
        List<BatchItemResult<ChatResponse>> correlated = correlateByRequestIndex(resultLines, requestCount);
        if (correlated != null) {
            return correlated;
        }

        log.warn(
                "Bedrock batch job {} returned recordId values that this model did not generate, so its {} result(s) "
                        + "cannot be matched to the submitted requests and are returned in output file order",
                job.jobArn(),
                resultLines.size());
        return resultLines.stream().map(ResultLine::result).toList();
    }

    private @Nullable List<BatchItemResult<ChatResponse>> correlateByRequestIndex(
            List<ResultLine> resultLines, int requestCount) {
        List<BatchItemResult<ChatResponse>> results = new ArrayList<>(Collections.nCopies(requestCount, null));
        for (ResultLine resultLine : resultLines) {
            Integer index = resultLine.index();
            if (index == null || index >= requestCount || results.get(index) != null) {
                return null;
            }
            results.set(index, resultLine.result());
        }
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i) == null) {
                results.set(i, failure(NO_ERROR_CODE, MISSING_RESULT_MESSAGE));
            }
        }
        return results;
    }

    private ResultLine toResultLine(String line) {
        Map<String, Object> record;
        try {
            record = fromJsonLine(line);
        } catch (RuntimeException e) {
            return new ResultLine(null, failure(NO_ERROR_CODE, UNREADABLE_RESULT_MESSAGE));
        }
        Integer index = recordIdIndex(string(record, "recordId"));
        Map<String, Object> modelOutput = map(record, "modelOutput");
        if (!modelOutput.isEmpty()) {
            return new ResultLine(
                    index,
                    BatchItemResult.success(
                            BedrockBatchConverseMapper.toChatResponse(modelOutput, modelId, returnThinking)));
        }
        Map<String, Object> error = map(record, "error");
        return new ResultLine(
                index,
                failure(
                        getOrDefault(integer(error, "errorCode"), NO_ERROR_CODE),
                        getOrDefault(string(error, "errorMessage"), UNKNOWN_ERROR_MESSAGE)));
    }

    private static BatchItemResult<ChatResponse> failure(int code, String message) {
        return BatchItemResult.failure(new BatchError(code, message, null));
    }

    private @Nullable Integer totalRecordCount(String manifestKey) {
        String manifest = read(manifestKey);
        try {
            return integer(fromJsonLine(manifest), "totalRecordCount");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String read(String key) {
        return s3Client.getObjectAsBytes(b -> b.bucket(outputLocation.bucket()).key(key))
                .asUtf8String();
    }

    private static int highestIndex(List<ResultLine> resultLines) {
        int highest = -1;
        for (ResultLine resultLine : resultLines) {
            if (resultLine.index() != null) {
                highest = Math.max(highest, resultLine.index());
            }
        }
        return highest;
    }

    private static String recordId(int index) {
        return RECORD_ID_PREFIX + String.format("%010d", index);
    }

    private static @Nullable Integer recordIdIndex(@Nullable String recordId) {
        if (recordId == null || !recordId.startsWith(RECORD_ID_PREFIX)) {
            return null;
        }
        try {
            int index = Integer.parseInt(recordId.substring(RECORD_ID_PREFIX.length()));
            return index < 0 ? null : index;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean hasEnded(@Nullable ModelInvocationJobStatus status) {
        return status == ModelInvocationJobStatus.COMPLETED
                || status == ModelInvocationJobStatus.PARTIALLY_COMPLETED
                || status == ModelInvocationJobStatus.STOPPED
                || status == ModelInvocationJobStatus.EXPIRED
                || status == ModelInvocationJobStatus.FAILED;
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

    private record ResultLine(@Nullable Integer index, BatchItemResult<ChatResponse> result) {}

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
        private Duration jobTimeout;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean returnThinking;
        private Boolean sendThinking;
        private Supplier<Map<String, String>> customHeadersSupplier;
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
         * Sets the model that every request in the batch runs against. Defaults to the model name of
         * {@link #defaultRequestParameters(ChatRequestParameters)}.
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
         * Sets how long the job may run before its unprocessed records expire. Bedrock accepts whole hours, so a
         * value that is not a whole number of hours is rounded up. If not set, Bedrock applies its default.
         *
         * @return {@code this}
         */
        public Builder jobTimeout(Duration jobTimeout) {
            this.jobTimeout = jobTimeout;
            return this;
        }

        /**
         * Sets the timeout of each control-plane and S3 call. If not set, the AWS SDK default applies.
         *
         * @return {@code this}
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
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
         * Controls whether the thinking of a model with reasoning enabled is returned in
         * {@link dev.langchain4j.data.message.AiMessage#thinking()}. Defaults to {@code false}.
         *
         * @return {@code this}
         */
        public Builder returnThinking(Boolean returnThinking) {
            this.returnThinking = returnThinking;
            return this;
        }

        /**
         * Controls whether the thinking of an {@link dev.langchain4j.data.message.AiMessage} in the conversation is
         * sent back to the model. Defaults to {@code true}.
         *
         * @return {@code this}
         */
        public Builder sendThinking(Boolean sendThinking) {
            this.sendThinking = sendThinking;
            return this;
        }

        /**
         * Sets headers added to every control-plane call.
         *
         * @return {@code this}
         */
        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeadersSupplier = () -> customHeaders;
            return this;
        }

        /**
         * Sets a supplier of headers added to every control-plane call.
         *
         * @return {@code this}
         */
        public Builder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
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
