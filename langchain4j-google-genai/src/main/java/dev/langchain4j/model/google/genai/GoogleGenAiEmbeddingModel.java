package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import com.google.genai.types.Part;
import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.request.EmbeddingInput;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.request.EmbeddingParameter;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Experimental
public class GoogleGenAiEmbeddingModel extends DimensionAwareEmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(GoogleGenAiEmbeddingModel.class);

    public enum TaskTypeEnum {
        TASK_TYPE_UNSPECIFIED("TASK_TYPE_UNSPECIFIED"),
        RETRIEVAL_QUERY("RETRIEVAL_QUERY"),
        RETRIEVAL_DOCUMENT("RETRIEVAL_DOCUMENT"),
        SEMANTIC_SIMILARITY("SEMANTIC_SIMILARITY"),
        CLASSIFICATION("CLASSIFICATION"),
        CLUSTERING("CLUSTERING"),
        QUESTION_ANSWERING("QUESTION_ANSWERING"),
        FACT_VERIFICATION("FACT_VERIFICATION"),
        CODE_RETRIEVAL_QUERY("CODE_RETRIEVAL_QUERY");

        private final String sdkTaskType;

        TaskTypeEnum(String sdkTaskType) {
            this.sdkTaskType = sdkTaskType;
        }

        public String getSdkTaskType() {
            return sdkTaskType;
        }
    }

    private final Client client;
    private final String modelName;
    private final Integer outputDimensionality;
    private final TaskTypeEnum taskType;
    private final String titleMetadataKey;
    private final Integer maxSegmentsPerBatch;
    private final Integer maxRetries;
    private final boolean logRequests;
    private final boolean logResponses;
    private final List<EmbeddingModelListener> listeners;

    public GoogleGenAiEmbeddingModel(Builder builder) {
        this.client = builder.client != null
                ? builder.client
                : GoogleGenAiClientFactory.createClient(
                        builder.apiKey,
                        builder.googleCredentials,
                        builder.projectId,
                        builder.location,
                        builder.timeout,
                        builder.customHeaders,
                        builder.apiEndpoint);
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.outputDimensionality = builder.outputDimensionality;
        this.taskType = builder.taskType;
        this.titleMetadataKey = getOrDefault(builder.titleMetadataKey, "title");
        this.maxRetries = getOrDefault(builder.maxRetries, 3);

        this.maxSegmentsPerBatch = getOrDefault(builder.maxSegmentsPerBatch, 100);
        this.logRequests = getOrDefault(builder.logRequests, false);
        this.logResponses = getOrDefault(builder.logResponses, false);
        this.listeners = copy(builder.listeners);
    }

    @Override
    public List<EmbeddingModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.GOOGLE_GENAI;
    }

    @Override
    public Set<EmbeddingParameter<?>> supportedParameters() {
        return Set.of(EmbeddingRequestParameters.INPUT_TYPE, EmbeddingRequestParameters.DIMENSIONS);
    }

    @Override
    public Set<ContentType> supportedContentTypes() {
        return isEmbedding2(modelName) ? Set.of(ContentType.TEXT, ContentType.IMAGE) : Set.of(ContentType.TEXT);
    }

    @Override
    public EmbeddingResponse doEmbed(EmbeddingRequest request) {
        EmbeddingInputType inputType = request.inputType();
        boolean embedding2 = isEmbedding2(modelName);

        String effectiveTaskType = embedding2 ? null : toSdkTaskType(inputType);
        Integer effectiveDimensions = getOrDefault(request.dimensions(), outputDimensionality);

        EmbedContentConfig.Builder configBuilder = EmbedContentConfig.builder();
        if (effectiveTaskType != null) {
            configBuilder.taskType(effectiveTaskType);
        }
        if (effectiveDimensions != null) {
            configBuilder.outputDimensionality(effectiveDimensions);
        }
        EmbedContentConfig config = configBuilder.build();

        boolean multimodal = request.inputs().stream()
                .flatMap(input -> input.contentTypes().stream())
                .anyMatch(type -> type != ContentType.TEXT);

        List<EmbedContentResponse> responses = new ArrayList<>();
        if (multimodal) {
            // Each input (its possibly interleaved text + image parts) is fused into a single embedding.
            for (EmbeddingInput input : request.inputs()) {
                Content content = toContent(input, inputType);
                responses.add(withRetryMappingExceptions(
                        () -> client.models.embedContent(modelName, content, config), maxRetries));
            }
        } else {
            List<String> texts = request.inputs().stream()
                    .map(input -> embedding2 ? applyTaskInstruction(input.text(), inputType) : input.text())
                    .collect(Collectors.toList());
            for (int i = 0; i < texts.size(); i += maxSegmentsPerBatch) {
                List<String> batch = texts.subList(i, Math.min(i + maxSegmentsPerBatch, texts.size()));
                responses.add(withRetryMappingExceptions(
                        () -> client.models.embedContent(modelName, batch, config), maxRetries));
            }
        }

        List<Embedding> embeddings = new ArrayList<>();
        int tokenCount = 0;
        boolean tokenCountReported = false;
        for (EmbedContentResponse response : responses) {
            if (response.embeddings().isEmpty()) {
                continue;
            }
            for (ContentEmbedding embedding : response.embeddings().get()) {
                if (embedding.values().isPresent()) {
                    embeddings.add(Embedding.from(embedding.values().get()));
                }
                if (embedding.statistics().isPresent()
                        && embedding.statistics().get().tokenCount().isPresent()) {
                    tokenCount += Math.round(embedding.statistics().get().tokenCount().get());
                    tokenCountReported = true;
                }
            }
        }

        EmbeddingResponse.Builder responseBuilder =
                EmbeddingResponse.builder().embeddings(embeddings).modelName(modelName);
        if (tokenCountReported) {
            responseBuilder.tokenUsage(new TokenUsage(tokenCount));
        }
        return responseBuilder.build();
    }

    private static boolean isEmbedding2(String modelName) {
        return modelName != null && modelName.contains("embedding-2");
    }

    /**
     * Builds a Gemini {@link Content} from an {@link EmbeddingInput}, mapping text parts to text and image parts
     * to inline (base64) data. For a text-only input the Gemini Embedding 2 task instruction is applied.
     */
    private Content toContent(EmbeddingInput input, EmbeddingInputType inputType) {
        boolean textOnly = input.contents().stream().allMatch(content -> content instanceof TextContent);
        List<Part> parts = new ArrayList<>();
        for (var content : input.contents()) {
            if (content instanceof TextContent textContent) {
                String text = textOnly ? applyTaskInstruction(textContent.text(), inputType) : textContent.text();
                parts.add(Part.fromText(text));
            } else if (content instanceof ImageContent imageContent) {
                var image = imageContent.image();
                if (image.base64Data() == null) {
                    throw new UnsupportedFeatureException(
                            "Gemini requires base64 image data (a plain URL is not supported)");
                }
                parts.add(Part.fromBytes(
                        Base64.getDecoder().decode(image.base64Data()),
                        getOrDefault(image.mimeType(), "image/png")));
            } else {
                throw new UnsupportedFeatureException("Unsupported content type: " + content.type());
            }
        }
        return Content.fromParts(parts.toArray(new Part[0]));
    }

    /**
     * Maps the common {@link EmbeddingInputType} onto the SDK task type, falling back to the model's configured
     * {@link #taskType} when no input type is requested.
     */
    private String toSdkTaskType(EmbeddingInputType inputType) {
        if (inputType == null) {
            return taskType != null ? taskType.getSdkTaskType() : null;
        }
        return switch (inputType) {
            case QUERY -> TaskTypeEnum.RETRIEVAL_QUERY.getSdkTaskType();
            case DOCUMENT -> TaskTypeEnum.RETRIEVAL_DOCUMENT.getSdkTaskType();
        };
    }

    /**
     * Rewrites text with Gemini Embedding 2's recommended task instruction, since that model does not accept the
     * task type parameter. Returns the text unchanged when no input type is requested.
     */
    private static String applyTaskInstruction(String text, EmbeddingInputType inputType) {
        if (inputType == null) {
            return text;
        }
        return switch (inputType) {
            case QUERY -> "task: search result | query: " + text;
            case DOCUMENT -> "title: none | text: " + text;
        };
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        return Response.from(embedAll(List.of(textSegment)).content().get(0));
    }

    @Override
    public Response<Embedding> embed(String text) {
        return embed(TextSegment.from(text));
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        if (textSegments == null || textSegments.isEmpty()) {
            return Response.from(new ArrayList<>());
        }

        if (logRequests) {
            log.info(
                    "Request:\n- model: {}\n- texts: {}",
                    modelName,
                    textSegments.stream().map(TextSegment::text).collect(Collectors.toList()));
        }

        // Group IndexedSegment objects by their segment title (or null key if none).
        // Rationale: In document ingestion pipelines, multiple text segments often share the same document title.
        // Since the google-genai SDK's embedContent method with list parameters shares a single EmbedContentConfig
        // (which only supports a single title), grouping segments by their title allows us to batch texts sharing
        // the same title together in one API request. This maximizes API throughput and fully preserves distinct
        // titles.
        Map<String, List<IndexedSegment>> grouped = new LinkedHashMap<>();
        for (int i = 0; i < textSegments.size(); i++) {
            TextSegment segment = textSegments.get(i);
            String title = null;
            if (TaskTypeEnum.RETRIEVAL_DOCUMENT.equals(taskType) && segment.metadata() != null) {
                title = segment.metadata().getString(titleMetadataKey);
            }
            grouped.computeIfAbsent(title, k -> new ArrayList<>()).add(new IndexedSegment(i, segment));
        }

        Embedding[] embeddingsArray = new Embedding[textSegments.size()];

        for (Map.Entry<String, List<IndexedSegment>> entry : grouped.entrySet()) {
            String title = entry.getKey();
            List<IndexedSegment> indexedSegments = entry.getValue();

            int size = indexedSegments.size();
            for (int i = 0; i < size; i += maxSegmentsPerBatch) {
                List<IndexedSegment> batch = indexedSegments.subList(i, Math.min(i + maxSegmentsPerBatch, size));
                List<String> texts = batch.stream().map(is -> is.segment.text()).collect(Collectors.toList());

                EmbedContentConfig.Builder configBuilder = EmbedContentConfig.builder();
                if (taskType != null) {
                    configBuilder.taskType(taskType.getSdkTaskType());
                }
                if (outputDimensionality != null) {
                    configBuilder.outputDimensionality(outputDimensionality);
                }
                if (title != null) {
                    configBuilder.title(title);
                }

                EmbedContentResponse response = withRetryMappingExceptions(
                        () -> client.models.embedContent(modelName, texts, configBuilder.build()), maxRetries);

                if (response.embeddings().isPresent()) {
                    var embeddings = response.embeddings().get();
                    for (int j = 0; j < batch.size(); j++) {
                        if (j < embeddings.size() && embeddings.get(j).values().isPresent()) {
                            embeddingsArray[batch.get(j).index] =
                                    Embedding.from(embeddings.get(j).values().get());
                        }
                    }
                }
            }
        }

        Response<List<Embedding>> response = Response.from(Arrays.asList(embeddingsArray));
        if (logResponses) {
            log.info("Response:\n- model: {}\n- response: {}", modelName, response);
        }
        return response;
    }

    private static class IndexedSegment {
        final int index;
        final TextSegment segment;

        IndexedSegment(int index, TextSegment segment) {
            this.index = index;
            this.segment = segment;
        }
    }

    @Override
    public String modelName() {
        return this.modelName;
    }

    @Override
    public Integer knownDimension() {
        return outputDimensionality;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Client client;
        private String modelName;
        private String apiKey;
        private GoogleCredentials googleCredentials;
        private String projectId;
        private String location;
        private Boolean logRequests;
        private Boolean logResponses;
        private Duration timeout;
        private Integer outputDimensionality;
        private TaskTypeEnum taskType;
        private String titleMetadataKey;
        private String apiEndpoint;
        private Map<String, String> customHeaders;
        private Integer maxSegmentsPerBatch = 100;
        private Integer maxRetries = 3;
        private List<EmbeddingModelListener> listeners;

        public Builder client(Client client) {
            this.client = client;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = ensureNotBlank(modelName, "modelName");
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder googleCredentials(GoogleCredentials googleCredentials) {
            this.googleCredentials = googleCredentials;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder logRequestsAndResponses(Boolean logRequestsAndResponses) {
            this.logRequests = logRequestsAndResponses;
            this.logResponses = logRequestsAndResponses;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder outputDimensionality(Integer outputDimensionality) {
            this.outputDimensionality = outputDimensionality;
            return this;
        }

        public Builder taskType(TaskTypeEnum taskType) {
            this.taskType = taskType;
            return this;
        }

        public Builder titleMetadataKey(String titleMetadataKey) {
            this.titleMetadataKey = titleMetadataKey;
            return this;
        }

        public Builder apiEndpoint(String apiEndpoint) {
            this.apiEndpoint = apiEndpoint;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public Builder maxSegmentsPerBatch(Integer maxSegmentsPerBatch) {
            this.maxSegmentsPerBatch = maxSegmentsPerBatch;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets the {@link EmbeddingModelListener}s notified around each embedding call.
         */
        public Builder listeners(List<EmbeddingModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public GoogleGenAiEmbeddingModel build() {
            return new GoogleGenAiEmbeddingModel(this);
        }
    }
}
