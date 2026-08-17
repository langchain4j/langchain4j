package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialBatchHelper.toBatchState;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialBatchHelper.toJsonl;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialBatchHelper.toResultLines;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.aiMessageFrom;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.finishReasonFrom;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.toOpenAiChatCompletionCreateParams;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.tokenUsageFrom;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.core.MultipartField;
import com.openai.core.http.HttpResponse;
import com.openai.credential.Credential;
import com.openai.models.batches.Batch;
import com.openai.models.batches.BatchCreateParams;
import com.openai.models.batches.BatchListPage;
import com.openai.models.batches.BatchListParams;
import com.openai.models.batches.BatchRequestCounts;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.files.FileCreateParams;
import com.openai.models.files.FileObject;
import com.openai.models.files.FilePurpose;
import dev.langchain4j.Experimental;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.batch.BatchError;
import dev.langchain4j.model.batch.BatchItemResult;
import dev.langchain4j.model.batch.BatchPage;
import dev.langchain4j.model.batch.BatchPagination;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.chat.BatchChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openaiofficial.setup.OpenAiOfficialSetup;
import dev.langchain4j.model.output.FinishReason;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Submits chat requests to the
 * <a href="https://platform.openai.com/docs/guides/batch">OpenAI Batch API</a>, which processes them
 * asynchronously at a reduced cost compared to real-time requests.
 *
 * <p>Requests are serialized to a JSONL file, uploaded through the Files API with the {@code batch} purpose,
 * and submitted against the chat completions endpoint. Results are correlated back to the submitted requests
 * by {@code custom_id}, so {@link BatchResponse#results()} is always in submission order even though OpenAI
 * does not guarantee the order of the output file.</p>
 *
 * <p>Every request in a batch must use the same model, so submitting requests that resolve to different
 * models fails fast with an {@link IllegalArgumentException}.</p>
 *
 * <p>Azure OpenAI is supported. Its batch and file operations are scoped to the resource rather than to a
 * deployment, it expects {@code /chat/completions} as the batch endpoint, and it identifies the model by
 * deployment name, all of which are handled here. GitHub Models has no Batch API and is rejected with an
 * {@link UnsupportedFeatureException}.</p>
 *
 * @see BatchChatModel
 * @see BatchResponse
 */
@Experimental
public final class OpenAiOfficialBatchChatModel extends OpenAiOfficialBaseChatModel implements BatchChatModel {

    private static final String DEFAULT_COMPLETION_WINDOW = "24h";
    private static final String MICROSOFT_FOUNDRY_CHAT_COMPLETIONS_ENDPOINT = "/chat/completions";
    private static final String INPUT_FILE_NAME = "batch.jsonl";
    private static final String INPUT_FILE_CONTENT_TYPE = "application/jsonl";
    private static final String EXPIRES_AFTER_ANCHOR = "created_at";
    private static final String UNKNOWN_ERROR_MESSAGE = "unknown";
    private static final String MISSING_RESULT_MESSAGE = "No result was returned for this request";

    private final BatchCreateParams.CompletionWindow completionWindow;
    private final BatchCreateParams.Endpoint endpoint;
    private final Map<String, String> batchMetadata;

    @Nullable
    private final String microsoftFoundryDeploymentName;

    @Nullable
    private final Long inputFileExpiresAfterSeconds;

    @Nullable
    private final Long outputExpiresAfterSeconds;

    public OpenAiOfficialBatchChatModel(Builder builder) {
        ModelProvider detectedProvider = OpenAiOfficialSetup.detectModelProvider(
                builder.isMicrosoftFoundry,
                builder.isGitHubModels,
                builder.baseUrl,
                builder.microsoftFoundryDeploymentName,
                builder.azureOpenAIServiceVersion);
        if (ModelProvider.GITHUB_MODELS.equals(detectedProvider)) {
            throw new UnsupportedFeatureException("The Batch API is not supported by GitHub Models");
        }

        this.client = builder.openAIClient != null
                ? builder.openAIClient
                : OpenAiOfficialSetup.setupSyncClient(
                        builder.baseUrl,
                        builder.apiKey,
                        builder.credential,
                        null,
                        builder.azureOpenAIServiceVersion,
                        builder.organizationId,
                        ModelProvider.MICROSOFT_FOUNDRY.equals(detectedProvider),
                        false,
                        builder.modelName,
                        builder.timeout,
                        builder.maxRetries,
                        builder.proxy,
                        builder.customHeaders);
        init(
                builder.baseUrl,
                builder.apiKey,
                builder.credential,
                builder.microsoftFoundryDeploymentName,
                builder.azureOpenAIServiceVersion,
                builder.organizationId,
                builder.isMicrosoftFoundry,
                builder.isGitHubModels,
                builder.defaultRequestParameters,
                builder.modelName,
                builder.temperature,
                builder.topP,
                builder.stop,
                builder.maxCompletionTokens,
                builder.presencePenalty,
                builder.frequencyPenalty,
                builder.logitBias,
                builder.responseFormat,
                builder.strictJsonSchema,
                builder.seed,
                builder.user,
                builder.strictTools,
                builder.parallelToolCalls,
                builder.store,
                builder.metadata,
                builder.serviceTier,
                builder.timeout,
                builder.maxRetries,
                builder.proxy,
                null,
                builder.customHeaders,
                null,
                null,
                false);
        this.modelName = defaultRequestParameters.modelName();
        this.completionWindow = BatchCreateParams.CompletionWindow.of(
                getOrDefault(builder.completionWindow, DEFAULT_COMPLETION_WINDOW));
        boolean isMicrosoftFoundry = ModelProvider.MICROSOFT_FOUNDRY.equals(this.modelProvider);
        this.endpoint = isMicrosoftFoundry
                ? BatchCreateParams.Endpoint.of(MICROSOFT_FOUNDRY_CHAT_COMPLETIONS_ENDPOINT)
                : BatchCreateParams.Endpoint.V1_CHAT_COMPLETIONS;
        this.microsoftFoundryDeploymentName =
                isMicrosoftFoundry ? getOrDefault(builder.microsoftFoundryDeploymentName, modelName) : null;
        this.batchMetadata = copy(builder.batchMetadata);
        this.inputFileExpiresAfterSeconds = builder.inputFileExpiresAfterSeconds;
        this.outputExpiresAfterSeconds = builder.outputExpiresAfterSeconds;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Submitting a batch uploads a JSONL input file through the provider's Files API before creating the
     * batch, so that API's storage, retention and quota limits apply in addition to the batch limits, and may
     * change independently of them. Uploaded input files are retained until they expire or are deleted, which
     * {@link Builder#inputFileExpiresAfterSeconds(Long)} controls.</p>
     *
     * <p>OpenAI currently limits a batch to 50,000 requests and its input file to 200 MB; exceeding either is
     * rejected by the API.</p>
     *
     * @throws IllegalArgumentException if the batch is empty, or if the requests do not all resolve to the
     *                                  same model.
     */
    @Override
    public BatchResponse<ChatResponse> submit(BatchRequest<ChatRequest> request) {
        List<ChatRequest> chatRequests = ensureNotEmpty(request.requests(), "requests");

        Set<String> modelNames = new LinkedHashSet<>();
        List<ChatCompletionCreateParams> createParams = new ArrayList<>();
        for (ChatRequest chatRequest : chatRequests) {
            OpenAiOfficialChatRequestParameters parameters =
                    defaultRequestParameters.overrideWith(chatRequest.parameters());
            InternalOpenAiOfficialHelper.validate(parameters);

            String effectiveModelName = resolveModelName(parameters);
            modelNames.add(effectiveModelName);
            createParams.add(toOpenAiChatCompletionCreateParams(chatRequest, parameters, strictTools, strictJsonSchema)
                    .model(effectiveModelName)
                    .build());
        }
        if (modelNames.size() > 1) {
            throw new IllegalArgumentException("Batch requests cannot use different models; "
                    + "all requests must use the same model: " + modelNames);
        }

        FileCreateParams.Builder fileCreateParams = FileCreateParams.builder()
                .file(MultipartField.<InputStream>builder()
                        .value(new ByteArrayInputStream(toJsonl(createParams)))
                        .filename(INPUT_FILE_NAME)
                        .contentType(INPUT_FILE_CONTENT_TYPE)
                        .build())
                .purpose(FilePurpose.BATCH);
        if (inputFileExpiresAfterSeconds != null) {
            fileCreateParams.expiresAfter(FileCreateParams.ExpiresAfter.builder()
                    .anchor(JsonValue.from(EXPIRES_AFTER_ANCHOR))
                    .seconds(inputFileExpiresAfterSeconds)
                    .build());
        }
        FileObject inputFile = client.files().create(fileCreateParams.build());

        BatchCreateParams.Builder batchCreateParams = BatchCreateParams.builder()
                .inputFileId(inputFile.id())
                .endpoint(endpoint)
                .completionWindow(completionWindow);

        if (!batchMetadata.isEmpty()) {
            BatchCreateParams.Metadata.Builder metadataBuilder = BatchCreateParams.Metadata.builder();
            batchMetadata.forEach((key, value) -> metadataBuilder.putAdditionalProperty(key, JsonValue.from(value)));
            batchCreateParams.metadata(metadataBuilder.build());
        }
        if (outputExpiresAfterSeconds != null) {
            batchCreateParams.outputExpiresAfter(BatchCreateParams.OutputExpiresAfter.builder()
                    .anchor(JsonValue.from(EXPIRES_AFTER_ANCHOR))
                    .seconds(outputExpiresAfterSeconds)
                    .build());
        }

        return toBatchResponse(client.batches().create(batchCreateParams.build()), List.of());
    }

    private String resolveModelName(OpenAiOfficialChatRequestParameters parameters) {
        if (microsoftFoundryDeploymentName == null) {
            return ensureNotBlank(parameters.modelName(), "modelName");
        }
        if (parameters.modelName() != null && !parameters.modelName().equals(modelName)) {
            throw new UnsupportedFeatureException("Microsoft Foundry batch requests do not support overriding "
                    + "modelName per request; configure the batch deployment when building the model");
        }
        return microsoftFoundryDeploymentName;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Both the output file and the error file are streamed, but every parsed result is held in memory, so
     * a batch close to OpenAI's 200 MB limit requires a correspondingly large heap. Results are returned as soon as OpenAI has
     * produced them, which includes the partial results of an expired or cancelled batch.</p>
     *
     * <p>Results are correlated by {@code custom_id}. Because those identifiers are generated by
     * {@link #submit(BatchRequest)}, a returned identifier that is malformed, duplicated, or outside the
     * submitted range is an invariant violation rather than a normal outcome, and fails with an
     * {@link IllegalStateException} instead of being reported as an extra result.</p>
     *
     * <p>{@link BatchError#code()} holds the HTTP status code for a request that OpenAI attempted and
     * rejected, and {@code 0} for a request that never ran (for example an expired one) or for a
     * batch-level failure, because OpenAI reports those with a string code instead. That string code is
     * available under the {@code "code"} key of {@link BatchError#details()}.</p>
     */
    @Override
    public BatchResponse<ChatResponse> retrieve(String batchId) {
        Batch batch = client.batches().retrieve(batchId);
        return toBatchResponse(batch, readResults(batch));
    }

    @Override
    public void cancel(String batchId) {
        client.batches().cancel(batchId);
    }

    @Override
    public BatchPage<ChatResponse> list(@Nullable BatchPagination pagination) {
        BatchListParams.Builder listParams = BatchListParams.builder();
        if (pagination != null) {
            if (pagination.pageSize() != null) {
                listParams.limit(pagination.pageSize().longValue());
            }
            if (pagination.pageToken() != null) {
                listParams.after(pagination.pageToken());
            }
        }

        BatchListPage page = client.batches().list(listParams.build());
        List<Batch> batches = page.data();

        List<BatchResponse<ChatResponse>> batchResponses = new ArrayList<>();
        for (Batch batch : batches) {
            batchResponses.add(toBatchResponse(batch, List.of()));
        }

        String nextPageToken = page.hasMore().orElse(false) && !batches.isEmpty()
                ? batches.get(batches.size() - 1).id()
                : null;
        return new BatchPage<>(batchResponses, nextPageToken);
    }

    /**
     * @return a new {@link Builder} for {@link OpenAiOfficialBatchChatModel}.
     */
    public static Builder builder() {
        return new Builder();
    }

    private List<BatchItemResult<ChatResponse>> readResults(Batch batch) {
        List<InternalOpenAiOfficialBatchHelper.ResultLine> resultLines = new ArrayList<>();
        Optional<String> outputFileId = batch.outputFileId();
        if (outputFileId.isPresent()) {
            resultLines.addAll(readResultFile(outputFileId.get()));
        }
        Optional<String> errorFileId = batch.errorFileId();
        if (errorFileId.isPresent()) {
            resultLines.addAll(readResultFile(errorFileId.get()));
        }

        if (resultLines.isEmpty()) {
            return toBatchLevelFailures(batch);
        }

        int requestCount = requestCount(batch, resultLines);
        List<BatchItemResult<ChatResponse>> results = new ArrayList<>(Collections.nCopies(requestCount, null));
        for (InternalOpenAiOfficialBatchHelper.ResultLine resultLine : resultLines) {
            int requestIndex = resultLine.requestIndex();
            if (requestIndex >= requestCount) {
                throw new IllegalStateException("Batch result refers to request " + requestIndex + ", but only "
                        + requestCount + " request(s) were submitted");
            }
            if (results.get(requestIndex) != null) {
                throw new IllegalStateException("Duplicate custom_id in batch result: "
                        + InternalOpenAiOfficialBatchHelper.toCustomId(requestIndex));
            }
            results.set(requestIndex, toBatchItemResult(resultLine));
        }
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i) == null) {
                results.set(i, BatchItemResult.failure(new BatchError(0, MISSING_RESULT_MESSAGE, null)));
            }
        }
        return results;
    }

    private static int requestCount(Batch batch, List<InternalOpenAiOfficialBatchHelper.ResultLine> resultLines) {
        Optional<BatchRequestCounts> requestCounts = batch.requestCounts();
        if (requestCounts.isPresent()) {
            return (int) requestCounts.get().total();
        }
        int highestIndex = -1;
        for (InternalOpenAiOfficialBatchHelper.ResultLine resultLine : resultLines) {
            highestIndex = Math.max(highestIndex, resultLine.requestIndex());
        }
        return highestIndex + 1;
    }

    private List<InternalOpenAiOfficialBatchHelper.ResultLine> readResultFile(String fileId) {
        try (HttpResponse content = client.files().content(fileId)) {
            return toResultLines(content.body());
        }
    }

    private static List<BatchItemResult<ChatResponse>> toBatchLevelFailures(Batch batch) {
        List<com.openai.models.batches.BatchError> errors =
                batch.errors().flatMap(Batch.Errors::data).orElse(List.of());

        List<BatchItemResult<ChatResponse>> failures = new ArrayList<>();
        for (com.openai.models.batches.BatchError error : errors) {
            failures.add(BatchItemResult.failure(InternalOpenAiOfficialBatchHelper.toBatchError(error)));
        }
        return failures;
    }

    private BatchItemResult<ChatResponse> toBatchItemResult(InternalOpenAiOfficialBatchHelper.ResultLine resultLine) {
        ChatCompletion completion = resultLine.completion();
        if (completion != null) {
            return BatchItemResult.success(toChatResponse(completion));
        }
        BatchError error = resultLine.error();
        return BatchItemResult.failure(error != null ? error : new BatchError(0, UNKNOWN_ERROR_MESSAGE, null));
    }

    private ChatResponse toChatResponse(ChatCompletion chatCompletion) {
        OpenAiOfficialChatResponseMetadata.Builder responseMetadataBuilder =
                OpenAiOfficialChatResponseMetadata.builder()
                        .id(chatCompletion.id())
                        .modelName(chatCompletion.model())
                        .created(chatCompletion.created());

        if (!chatCompletion.choices().isEmpty()) {
            ChatCompletion.Choice choice = chatCompletion.choices().get(0);
            responseMetadataBuilder.finishReason(finishReasonFrom(choice.finishReason()));

            if (choice.message().toolCalls().isPresent()
                    && choice.finishReason().equals(ChatCompletion.Choice.FinishReason.STOP)) {
                responseMetadataBuilder.finishReason(FinishReason.TOOL_EXECUTION);
            }
        }
        if (chatCompletion.usage().isPresent()) {
            responseMetadataBuilder.tokenUsage(
                    tokenUsageFrom(chatCompletion.usage().get()));
        }
        if (chatCompletion.serviceTier().isPresent()) {
            responseMetadataBuilder.serviceTier(
                    chatCompletion.serviceTier().get().toString());
        }
        if (chatCompletion.systemFingerprint().isPresent()) {
            responseMetadataBuilder.systemFingerprint(
                    chatCompletion.systemFingerprint().get());
        }

        return ChatResponse.builder()
                .aiMessage(aiMessageFrom(chatCompletion))
                .metadata(responseMetadataBuilder.build())
                .build();
    }

    private static BatchResponse<ChatResponse> toBatchResponse(
            Batch batch, List<BatchItemResult<ChatResponse>> results) {
        return BatchResponse.<ChatResponse>builder()
                .batchId(batch.id())
                .state(toBatchState(batch.status()))
                .results(results)
                .build();
    }

    public static class Builder {

        private String baseUrl;
        private String apiKey;
        private Credential credential;
        private String microsoftFoundryDeploymentName;
        private AzureOpenAIServiceVersion azureOpenAIServiceVersion;
        private String organizationId;
        private boolean isMicrosoftFoundry;
        private boolean isGitHubModels;
        private OpenAIClient openAIClient;

        private ChatRequestParameters defaultRequestParameters;
        private String modelName;
        private Double temperature;
        private Double topP;
        private List<String> stop;
        private Integer maxCompletionTokens;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private Map<String, Integer> logitBias;
        private String responseFormat;
        private Boolean strictJsonSchema;
        private Integer seed;
        private String user;
        private Boolean strictTools;
        private Boolean parallelToolCalls;
        private Boolean store;
        private Map<String, String> metadata;
        private String serviceTier;

        private String completionWindow;
        private Map<String, String> batchMetadata;
        private Long inputFileExpiresAfterSeconds;
        private Long outputExpiresAfterSeconds;

        private Duration timeout;
        private Integer maxRetries;
        private Proxy proxy;
        private Map<String, String> customHeaders;

        /**
         * Sets default common {@link ChatRequestParameters} or OpenAI-specific {@link OpenAiOfficialChatRequestParameters}.
         * <br>
         * When a parameter is set via an individual builder method (e.g., {@link #modelName(String)}),
         * its value takes precedence over the same parameter set via {@link ChatRequestParameters}.
         */
        public Builder defaultRequestParameters(ChatRequestParameters parameters) {
            this.defaultRequestParameters = parameters;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder modelName(com.openai.models.ChatModel modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder credential(Credential credential) {
            this.credential = credential;
            return this;
        }

        public Builder microsoftFoundryDeploymentName(String microsoftFoundryDeploymentName) {
            this.microsoftFoundryDeploymentName = microsoftFoundryDeploymentName;
            return this;
        }

        public Builder azureOpenAIServiceVersion(AzureOpenAIServiceVersion azureOpenAIServiceVersion) {
            this.azureOpenAIServiceVersion = azureOpenAIServiceVersion;
            return this;
        }

        public Builder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public Builder isGitHubModels(boolean isGitHubModels) {
            this.isGitHubModels = isGitHubModels;
            return this;
        }

        public Builder isMicrosoftFoundry(boolean isMicrosoftFoundry) {
            this.isMicrosoftFoundry = isMicrosoftFoundry;
            return this;
        }

        public Builder openAIClient(OpenAIClient openAIClient) {
            this.openAIClient = openAIClient;
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

        public Builder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public Builder maxCompletionTokens(Integer maxCompletionTokens) {
            this.maxCompletionTokens = maxCompletionTokens;
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

        public Builder logitBias(Map<String, Integer> logitBias) {
            this.logitBias = logitBias;
            return this;
        }

        public Builder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public Builder strictJsonSchema(Boolean strictJsonSchema) {
            this.strictJsonSchema = strictJsonSchema;
            return this;
        }

        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder strictTools(Boolean strictTools) {
            this.strictTools = strictTools;
            return this;
        }

        public Builder parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        public Builder store(Boolean store) {
            this.store = store;
            return this;
        }

        /**
         * Sets the metadata sent with every chat request in the batch.
         *
         * @see #batchMetadata(Map)
         */
        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder serviceTier(String serviceTier) {
            this.serviceTier = serviceTier;
            return this;
        }

        /**
         * Sets the time frame within which the batch should be processed. Defaults to {@code 24h},
         * which is currently the only value accepted by OpenAI.
         */
        public Builder completionWindow(String completionWindow) {
            this.completionWindow = completionWindow;
            return this;
        }

        /**
         * Sets the metadata attached to the batch itself, which OpenAI returns when the batch is retrieved
         * or listed. This is distinct from {@link #metadata(Map)}, which is sent with every chat request
         * inside the batch.
         */
        public Builder batchMetadata(Map<String, String> batchMetadata) {
            this.batchMetadata = batchMetadata;
            return this;
        }

        /**
         * Sets the number of seconds after upload at which the JSONL input file expires. Not set by default,
         * in which case the provider's own retention for batch input files applies. The accepted range is
         * defined by the provider and differs between OpenAI and Azure OpenAI.
         */
        public Builder inputFileExpiresAfterSeconds(Long inputFileExpiresAfterSeconds) {
            this.inputFileExpiresAfterSeconds = inputFileExpiresAfterSeconds;
            return this;
        }

        /**
         * Sets the number of seconds after the output file is created at which it, and the error file,
         * expire. Not set by default, in which case the provider's own retention applies. The accepted
         * range is defined by the provider and differs between OpenAI and Azure OpenAI.
         */
        public Builder outputExpiresAfterSeconds(Long outputExpiresAfterSeconds) {
            this.outputExpiresAfterSeconds = outputExpiresAfterSeconds;
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

        public Builder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public OpenAiOfficialBatchChatModel build() {
            return new OpenAiOfficialBatchChatModel(this);
        }
    }
}
