package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.request.EmbeddingInput;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.request.EmbeddingParameter;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.embedding.response.EmbeddingResponseMetadata;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiBatchEmbeddingRequest;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiBatchEmbeddingResponse;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiEmbeddingRequest;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiEmbeddingResponse;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class GoogleAiEmbeddingModel extends DimensionAwareEmbeddingModel {

    private static final int MAX_NUMBER_OF_SEGMENTS_PER_BATCH = 100;

    private final GeminiService geminiService;
    private final String modelName;
    private final Integer maxRetries;
    private final TaskType taskType;
    private final String titleMetadataKey;
    private final Integer outputDimensionality;
    private final List<EmbeddingModelListener> listeners;

    public GoogleAiEmbeddingModel(GoogleAiEmbeddingModelBuilder builder) {
        this.geminiService = new GeminiService(
                builder.httpClientBuilder,
                builder.apiKey,
                builder.baseUrl,
                getOrDefault(builder.logRequestsAndResponses, false),
                getOrDefault(builder.logRequests, false),
                getOrDefault(builder.logResponses, false),
                builder.logger,
                builder.timeout,
                null);
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.taskType = builder.taskType;
        this.titleMetadataKey = getOrDefault(builder.titleMetadataKey, "title");
        this.outputDimensionality = builder.outputDimensionality;
        this.listeners = copy(builder.listeners);
    }

    @Override
    public List<EmbeddingModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.GOOGLE_AI_GEMINI;
    }

    public static GoogleAiEmbeddingModelBuilder builder() {
        return new GoogleAiEmbeddingModelBuilder();
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        GeminiEmbeddingRequest embeddingRequest = getGoogleAiEmbeddingRequest(textSegment);

        GeminiEmbeddingResponse geminiResponse =
                withRetryMappingExceptions(() -> geminiService.embed(modelName, embeddingRequest), maxRetries);

        return Response.from(Embedding.from(geminiResponse.embedding().values()));
    }

    @Override
    public Response<Embedding> embed(String text) {
        return embed(TextSegment.from(text));
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<GeminiEmbeddingRequest> embeddingRequests =
                textSegments.stream().map(this::getGoogleAiEmbeddingRequest).collect(Collectors.toList());
        return Response.from(batchEmbed(embeddingRequests));
    }

    @Override
    public Set<EmbeddingParameter<?>> supportedParameters() {
        return Set.of(EmbeddingRequestParameters.INPUT_TYPE);
    }

    @Override
    public EmbeddingResponse doEmbed(EmbeddingRequest request) {
        EmbeddingInputType inputType = request.inputType();

        boolean embedding2 = isMultimodalModel(modelName);
        TaskType taskType = embedding2 ? null : toTaskType(inputType);

        List<GeminiEmbeddingRequest> embeddingRequests = request.inputs().stream()
                .map(input -> buildEmbeddingRequest(embedding2 ? withTaskInstruction(input, inputType) : input, taskType))
                .collect(Collectors.toList());

        return EmbeddingResponse.builder()
                .embeddings(batchEmbed(embeddingRequests))
                .metadata(EmbeddingResponseMetadata.builder().modelName(modelName).build())
                .build();
    }

    @Override
    public Set<ContentType> supportedContentTypes() {
        return isMultimodalModel(modelName)
                ? Set.of(ContentType.TEXT, ContentType.IMAGE)
                : Set.of(ContentType.TEXT);
    }

    private static boolean isMultimodalModel(String modelName) {
        return modelName != null && modelName.contains("embedding-2");
    }

    @Override
    public String modelName() {
        return this.modelName;
    }

    /**
     * Maps the common {@link EmbeddingInputType} onto Google's richer {@link TaskType}. When no input type is
     * requested, falls back to the model's configured {@link #taskType}.
     */
    private TaskType toTaskType(EmbeddingInputType inputType) {
        if (inputType == null) {
            return this.taskType;
        }
        return switch (inputType) {
            case QUERY -> TaskType.RETRIEVAL_QUERY;
            case DOCUMENT -> TaskType.RETRIEVAL_DOCUMENT;
        };
    }

    /**
     * Rewrites a text-only input with Gemini Embedding 2's recommended task instruction, since that model does
     * not accept the {@code task_type} parameter. Multimodal inputs are left unchanged, as the instruction
     * templates are defined for text only.
     */
    private static EmbeddingInput withTaskInstruction(EmbeddingInput input, EmbeddingInputType inputType) {
        if (inputType == null) {
            return input;
        }
        boolean textOnly = input.contents().stream().allMatch(content -> content instanceof TextContent);
        if (!textOnly) {
            return input;
        }
        return EmbeddingInput.from(applyTaskInstruction(input.text(), inputType));
    }

    private static String applyTaskInstruction(String text, EmbeddingInputType inputType) {
        return switch (inputType) {
            case QUERY -> "task: search result | query: " + text;
            case DOCUMENT -> "title: none | text: " + text;
        };
    }

    private List<Embedding> batchEmbed(List<GeminiEmbeddingRequest> embeddingRequests) {
        List<Embedding> allEmbeddings = new ArrayList<>();
        int numberOfEmbeddings = embeddingRequests.size();
        int numberOfBatches = 1 + numberOfEmbeddings / MAX_NUMBER_OF_SEGMENTS_PER_BATCH;

        for (int i = 0; i < numberOfBatches; i++) {
            int startIndex = MAX_NUMBER_OF_SEGMENTS_PER_BATCH * i;
            int lastIndex = Math.min(startIndex + MAX_NUMBER_OF_SEGMENTS_PER_BATCH, numberOfEmbeddings);

            if (startIndex >= numberOfEmbeddings) break;

            GeminiBatchEmbeddingRequest batchEmbeddingRequest =
                    new GeminiBatchEmbeddingRequest(embeddingRequests.subList(startIndex, lastIndex));

            GeminiBatchEmbeddingResponse geminiResponse =
                    withRetryMappingExceptions(() -> geminiService.batchEmbed(modelName, batchEmbeddingRequest));

            allEmbeddings.addAll(geminiResponse.embeddings().stream()
                    .map(values -> Embedding.from(values.values()))
                    .toList());
        }

        return allEmbeddings;
    }

    private GeminiEmbeddingRequest getGoogleAiEmbeddingRequest(TextSegment textSegment) {
        String title = null;
        if (TaskType.RETRIEVAL_DOCUMENT.equals(this.taskType)) {
            if (textSegment.metadata() != null && textSegment.metadata().getString(this.titleMetadataKey) != null) {
                title = textSegment.metadata().getString(this.titleMetadataKey);
            }
        }

        return buildEmbeddingRequest(textSegment.text(), this.taskType, title);
    }

    private GeminiEmbeddingRequest buildEmbeddingRequest(String text, TaskType taskType, String title) {
        GeminiContent.GeminiPart geminiPart =
                GeminiContent.GeminiPart.builder().text(text).build();

        GeminiContent content = new GeminiContent(Collections.singletonList(geminiPart), null);

        return new GeminiEmbeddingRequest(
                "models/" + this.modelName, content, taskType, title, this.outputDimensionality);
    }

    /**
     * Builds a request from an {@link EmbeddingInput}, whose (possibly interleaved) text and image parts are
     * fused into a single embedding — the multimodal path for Gemini Embedding 2.
     */
    private GeminiEmbeddingRequest buildEmbeddingRequest(EmbeddingInput input, TaskType taskType) {
        List<GeminiContent.GeminiPart> parts = new ArrayList<>();
        for (Content content : input.contents()) {
            if (content instanceof TextContent textContent) {
                parts.add(GeminiContent.GeminiPart.builder()
                        .text(textContent.text())
                        .build());
            } else if (content instanceof ImageContent imageContent) {
                var image = imageContent.image();
                if (image.base64Data() == null) {
                    throw new UnsupportedFeatureException(
                            "Gemini requires base64 image data (a plain URL is not supported)");
                }
                parts.add(GeminiContent.GeminiPart.builder()
                        .inlineData(new GeminiContent.GeminiPart.GeminiBlob(
                                getOrDefault(image.mimeType(), "image/png"), image.base64Data()))
                        .build());
            } else {
                throw new UnsupportedFeatureException("Unsupported content type: " + content.type());
            }
        }
        GeminiContent content = new GeminiContent(parts, null);
        return new GeminiEmbeddingRequest(
                "models/" + this.modelName, content, taskType, null, this.outputDimensionality);
    }

    @Override
    public Integer knownDimension() {
        return outputDimensionality;
    }

    public enum TaskType {
        RETRIEVAL_QUERY,
        RETRIEVAL_DOCUMENT,
        SEMANTIC_SIMILARITY,
        CLASSIFICATION,
        CLUSTERING,
        QUESTION_ANSWERING,
        FACT_VERIFICATION
    }

    public static class GoogleAiEmbeddingModelBuilder
            extends BaseGoogleAiEmbeddingModelBuilder<GoogleAiEmbeddingModelBuilder> {
        public GoogleAiEmbeddingModel build() {
            return new GoogleAiEmbeddingModel(this);
        }
    }

    abstract static class BaseGoogleAiEmbeddingModelBuilder<B extends BaseGoogleAiEmbeddingModelBuilder<B>> {
        HttpClientBuilder httpClientBuilder;
        String modelName;
        String apiKey;
        String baseUrl;
        Integer maxRetries;
        TaskType taskType;
        String titleMetadataKey;
        Integer outputDimensionality;
        List<EmbeddingModelListener> listeners;
        Duration timeout;
        Boolean logRequestsAndResponses;
        Boolean logRequests;
        Boolean logResponses;
        Logger logger;

        public B httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return builder();
        }

        @SuppressWarnings("unchecked")
        protected B builder() {
            return (B) this;
        }

        public B modelName(String modelName) {
            this.modelName = modelName;
            return builder();
        }

        public B apiKey(String apiKey) {
            this.apiKey = apiKey;
            return builder();
        }

        public B baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return builder();
        }

        public B maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return builder();
        }

        public B taskType(TaskType taskType) {
            this.taskType = taskType;
            return builder();
        }

        public B titleMetadataKey(String titleMetadataKey) {
            this.titleMetadataKey = titleMetadataKey;
            return builder();
        }

        public B outputDimensionality(Integer outputDimensionality) {
            this.outputDimensionality = outputDimensionality;
            return builder();
        }

        public B listeners(List<EmbeddingModelListener> listeners) {
            this.listeners = listeners;
            return builder();
        }

        public B timeout(Duration timeout) {
            this.timeout = timeout;
            return builder();
        }

        public B logRequestsAndResponses(Boolean logRequestsAndResponses) {
            this.logRequestsAndResponses = logRequestsAndResponses;
            return builder();
        }

        public B logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return builder();
        }

        public B logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return builder();
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public B logger(Logger logger) {
            this.logger = logger;
            return builder();
        }
    }
}
