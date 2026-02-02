package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.googleai.GeminiResponseModality.IMAGE;
import static dev.langchain4j.model.googleai.GeminiService.BatchOperationType.BATCH_GENERATE_CONTENT;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.Experimental;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchFileRequest;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchIncomplete;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchList;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchName;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchSuccess;
import dev.langchain4j.model.googleai.GeminiBatchProcessor.ExtractedBatchResults;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart;
import dev.langchain4j.model.googleai.GeminiFiles.GeminiFile;
import dev.langchain4j.model.googleai.GeminiGenerationConfig.GeminiImageConfig;
import dev.langchain4j.model.googleai.GoogleAiGeminiImageModel.GeminiImageGenerationException;
import dev.langchain4j.model.googleai.jsonl.JsonLinesWriter;
import dev.langchain4j.model.output.Response;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Provides an interface for batch image generation using the Gemini Batch API.
 *
 * <p>This is an asynchronous service designed for processing large volumes of image generation
 * requests at a reduced cost (50% of standard pricing). It is ideal for non-urgent, large-scale
 * tasks with a Service Level Objective (SLO) of 24-hour turnaround, though completion is often
 * much quicker.</p>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li><strong>Cost Savings:</strong> 50% reduction compared to real-time image generation</li>
 *   <li><strong>High Throughput:</strong> Process many image generation requests in a single batch</li>
 *   <li><strong>Flexible Input:</strong> Submit requests inline (up to 20MB) or via uploaded files (up to 2GB) using the {@link GeminiFiles} api</li>
 *   <li><strong>Configurable:</strong> Supports aspect ratio, image size, and safety settings</li>
 * </ul>
 *
 * <h2>Workflow</h2>
 * <ol>
 *   <li>Create a batch using {@link #createBatchInline} or {@link #createBatchFromFile}</li>
 *   <li>Poll for completion using {@link #retrieveBatchResults}</li>
 *   <li>Process the generated images from the {@link BatchSuccess} response</li>
 *   <li>Optionally cancel or delete the batch job</li>
 * </ol>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * GoogleAiGeminiBatchImageModel model = GoogleAiGeminiBatchImageModel.builder()
 *     .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
 *     .modelName("gemini-2.5-flash-image")
 *     .aspectRatio("16:9")
 *     .build();
 *
 * // Create batch with image generation requests
 * List<ImageGenerationRequest> requests = List.of(
 *     new ImageGenerationRequest("A serene mountain landscape at sunset"),
 *     new ImageGenerationRequest("A futuristic cityscape at night")
 * );
 *
 * BatchResponse<Response<Image>> response = model.createBatchInline("My Batch", 1L, requests);
 *
 * // Poll for completion
 * BatchName batchName = ((BatchIncomplete<?>) response).batchName();
 * BatchResponse<Response<Image>> result;
 * do {
 *     Thread.sleep(5000);
 *     result = model.retrieveBatchResults(batchName);
 * } while (result instanceof BatchIncomplete);
 *
 * // Process results
 * if (result instanceof BatchSuccess<Response<Image>> success) {
 *     for (Response<Image> imageResponse : success.responses()) {
 *         Image image = imageResponse.content();
 *         // Save or process the generated image
 *     }
 * }
 * }</pre>
 *
 * @see <a href="https://ai.google.dev/gemini-api/docs/batch-api">Gemini Batch API Documentation</a>
 * @see <a href="https://ai.google.dev/gemini-api/docs/image-generation">Gemini Image Generation Documentation</a>
 * @see GoogleAiGeminiImageModel
 */
@Experimental
public final class GoogleAiGeminiBatchImageModel {

    private final GeminiBatchProcessor<
                    ImageGenerationRequest,
                    Response<@NonNull Image>,
                    GeminiGenerateContentRequest,
                    GeminiGenerateContentResponse>
            batchProcessor;
    private final String modelName;
    private final GeminiImageConfig imageConfig;
    private final List<GeminiResponseModality> responseModalities;
    private final List<GeminiSafetySetting> safetySettings;
    private final ImageRequestPreparer preparer;

    GoogleAiGeminiBatchImageModel(GoogleAiGeminiBatchImageModelBuilder builder) {
        this(builder, buildGeminiService(builder));
    }

    GoogleAiGeminiBatchImageModel(GoogleAiGeminiBatchImageModelBuilder builder, GeminiService geminiService) {
        this.modelName = getOrDefault(builder.modelName, "gemini-2.5-flash-preview-image-generation");
        this.responseModalities = List.of(IMAGE);
        this.safetySettings = builder.safetySettings;

        // Build imageConfig if aspectRatio or imageSize is set
        if (builder.aspectRatio != null || builder.imageSize != null) {
            this.imageConfig = GeminiImageConfig.builder()
                    .aspectRatio(builder.aspectRatio)
                    .imageSize(builder.imageSize)
                    .build();
        } else {
            this.imageConfig = null;
        }

        this.preparer = new ImageRequestPreparer();
        this.batchProcessor = new GeminiBatchProcessor<>(geminiService, preparer);
    }

    private static GeminiService buildGeminiService(GoogleAiGeminiBatchImageModelBuilder builder) {
        return new GeminiService(
                builder.httpClientBuilder,
                builder.apiKey,
                builder.baseUrl,
                getOrDefault(builder.logRequestsAndResponses, false),
                getOrDefault(builder.logRequests, false),
                getOrDefault(builder.logResponses, false),
                builder.logger,
                builder.timeout);
    }

    /**
     * Creates and enqueues a batch of image generation requests for asynchronous processing.
     *
     * <p>This method submits multiple image generation requests as a single batch operation.
     * The batch will be processed asynchronously, and the initial response will typically be
     * in a {@link BatchIncomplete} state.</p>
     *
     * <p>Batch processing offers a 50% cost reduction compared to real-time requests and has
     * a 24-hour turnaround SLO, making it ideal for large-scale, non-urgent tasks.</p>
     *
     * <p><strong>Note:</strong> The inline API allows for a total request size of 20MB or under.
     * For larger batches, use {@link #createBatchFromFile}.</p>
     *
     * @param displayName a user-defined name for the batch, used for identification
     * @param priority    optional priority for the batch; batches with higher priority values
     *                    are processed before those with lower values; negative values are allowed;
     *                    defaults to 0 if null
     * @param requests    a list of image generation requests to be processed in the batch
     * @return a {@link BatchResponse} representing the initial state of the batch operation,
     * typically {@link BatchIncomplete}
     */
    public BatchResponse<Response<@NonNull Image>> createBatchInline(
            String displayName, @Nullable Long priority, List<ImageGenerationRequest> requests) {
        return batchProcessor.createBatchInline(displayName, priority, requests, modelName, BATCH_GENERATE_CONTENT);
    }

    /**
     * Creates a batch of image generation requests from an uploaded file.
     *
     * <p>This method allows you to create a batch job using a JSONL file that has been previously
     * uploaded to the Gemini Files API. This is useful for larger batches that exceed the 20MB
     * inline request limit, supporting up to 2GB per file.</p>
     *
     * <p>The file must contain batch requests in JSONL format, where each line is a JSON object
     * with a "key" and "request" field. Use {@link #writeBatchToFile} to create properly
     * formatted JSONL files.</p>
     *
     * @param displayName a user-defined name for the batch, used for identification
     * @param file        the GeminiFile object representing the uploaded file containing batch requests
     * @return a {@link BatchResponse} representing the initial state of the batch operation,
     * typically {@link BatchIncomplete}
     * @see #writeBatchToFile
     * @see GeminiFiles#uploadFile
     */
    public BatchResponse<Response<@NonNull Image>> createBatchFromFile(String displayName, GeminiFile file) {
        return batchProcessor.createBatchFromFile(displayName, file, modelName, BATCH_GENERATE_CONTENT);
    }

    /**
     * Writes a batch of image generation requests to a JSONL file for later upload and processing.
     *
     * <p>This method serializes image generation requests into JSONL (JSON Lines) format, where
     * each line contains a single request wrapped in a {@link BatchFileRequest} with a unique key.
     * The resulting file can be uploaded using the Gemini Files API and then used to create a
     * batch job via {@link #createBatchFromFile}.</p>
     *
     * <p><strong>Example usage:</strong></p>
     * <pre>{@code
     * Path batchFile = Files.createTempFile("image-batch", ".jsonl");
     * try (JsonLinesWriter writer = JsonLinesWriters.streaming(batchFile)) {
     *     List<BatchFileRequest<ImageGenerationRequest>> requests = List.of(
     *         new BatchFileRequest<>("img-1", new ImageGenerationRequest("A sunset over mountains")),
     *         new BatchFileRequest<>("img-2", new ImageGenerationRequest("A cat wearing a hat"))
     *     );
     *     batchModel.writeBatchToFile(writer, requests);
     * }
     * }</pre>
     *
     * @param writer   the JsonLinesWriter to which the batch requests will be written
     * @param requests an iterable collection of BatchFileRequest objects containing
     *                 ImageGenerationRequest instances, each with a unique key identifier
     * @throws IOException if an I/O error occurs while writing to the writer
     * @see #createBatchFromFile
     * @see JsonLinesWriter
     */
    public void writeBatchToFile(JsonLinesWriter writer, Iterable<BatchFileRequest<ImageGenerationRequest>> requests)
            throws IOException {
        for (var request : requests) {
            var inlinedRequest = preparer.createInlinedRequest(request.request());
            writer.write(new BatchFileRequest<>(request.key(), inlinedRequest));
        }
    }

    /**
     * Retrieves the current state and results of a batch operation.
     *
     * <p>This method polls the Gemini API to get the latest state of a previously created batch.
     * The response can be:</p>
     * <ul>
     *   <li>{@link BatchIncomplete} - if the batch is still pending or running</li>
     *   <li>{@link BatchSuccess} - if the batch completed successfully, containing all generated images</li>
     *   <li>{@link BatchRequestResponse.BatchError} - if the batch failed, containing error details</li>
     * </ul>
     *
     * <p>Clients should poll this method at intervals to check the operation status until completion.</p>
     *
     * @param name the name of the batch operation to retrieve, obtained from the initial
     *             {@link #createBatchInline} or {@link #createBatchFromFile} call
     * @return a {@link BatchResponse} representing the current state of the batch operation
     */
    public BatchResponse<Response<@NonNull Image>> retrieveBatchResults(BatchName name) {
        return batchProcessor.retrieveBatchResults(name);
    }

    /**
     * Cancels a batch operation that is currently pending or running.
     *
     * <p>This method attempts to cancel a batch job. Cancellation is only possible for batches
     * that are in PENDING or RUNNING state. Batches that have already completed, failed, or
     * been cancelled cannot be cancelled.</p>
     *
     * @param name the name of the batch operation to cancel
     * @throws dev.langchain4j.exception.HttpException if the batch cannot be cancelled
     *                                                 (e.g., already completed, already cancelled, or does not exist)
     */
    public void cancelBatchJob(BatchName name) {
        batchProcessor.cancelBatchJob(name);
    }

    /**
     * Deletes a batch job from the system.
     *
     * <p>This removes the batch job record but does not cancel it if still running.
     * Use {@link #cancelBatchJob} to cancel a running batch before deletion.</p>
     *
     * @param name the name of the batch job to delete
     * @throws RuntimeException if the batch job cannot be deleted or does not exist
     */
    public void deleteBatchJob(BatchName name) {
        batchProcessor.deleteBatchJob(name);
    }

    /**
     * Lists batch jobs with optional pagination.
     *
     * <p>Returns a paginated list of batch jobs. Use the returned page token to retrieve
     * subsequent pages of results.</p>
     *
     * @param pageSize  the maximum number of batch jobs to return; if null, uses server default
     * @param pageToken token for retrieving a specific page from {@link BatchList#pageToken()};
     *                  if null, returns the first page
     * @return a {@link BatchList} containing batch responses and a token for the next page
     */
    public BatchList<Response<@NonNull Image>> listBatchJobs(@Nullable Integer pageSize, @Nullable String pageToken) {
        return batchProcessor.listBatchJobs(pageSize, pageToken);
    }

    /**
     * Returns a new builder for constructing GoogleAiGeminiBatchImageModel instances.
     *
     * @return a new builder instance
     */
    public static GoogleAiGeminiBatchImageModelBuilder builder() {
        return new GoogleAiGeminiBatchImageModelBuilder();
    }

    /**
     * Represents a request for image generation in batch processing.
     *
     * <p>Each request contains a single text prompt that describes the image to be generated.</p>
     *
     * @param prompt the text description of the image to generate (required, cannot be blank)
     */
    public record ImageGenerationRequest(String prompt) {
        /**
         * Creates a new image generation request.
         *
         * @param prompt the text prompt for image generation
         * @throws IllegalArgumentException if prompt is null or blank
         */
        public ImageGenerationRequest {
            ensureNotBlank(prompt, "prompt");
        }
    }

    /**
     * Builder for constructing {@link GoogleAiGeminiBatchImageModel} instances.
     */
    public static class GoogleAiGeminiBatchImageModelBuilder {

        private HttpClientBuilder httpClientBuilder;
        private String apiKey;
        private String baseUrl;
        private String modelName;
        private String aspectRatio;
        private String imageSize;
        private Duration timeout;
        private Boolean logRequestsAndResponses;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private List<GeminiSafetySetting> safetySettings;

        private GoogleAiGeminiBatchImageModelBuilder() {}

        /**
         * Sets the HTTP client builder for custom HTTP configuration.
         *
         * @param httpClientBuilder the HTTP client builder
         * @return this builder
         */
        public GoogleAiGeminiBatchImageModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * Sets the API key for authenticating with the Gemini API.
         *
         * @param apiKey the Google AI API key (required)
         * @return this builder
         */
        public GoogleAiGeminiBatchImageModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets a custom base URL for the Gemini API.
         *
         * @param baseUrl the base URL (optional, defaults to Google's API endpoint)
         * @return this builder
         */
        public GoogleAiGeminiBatchImageModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the model name to use for image generation.
         *
         * <p>Defaults to "gemini-2.5-flash-preview-image-generation" if not specified.</p>
         *
         * @param modelName the model name
         * @return this builder
         */
        public GoogleAiGeminiBatchImageModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the aspect ratio for generated images.
         *
         * <p>Supported aspect ratios: "1:1", "2:3", "3:2", "3:4", "4:3", "4:5", "5:4",
         * "9:16", "16:9", "21:9"</p>
         *
         * @param aspectRatio the aspect ratio (e.g., "16:9")
         * @return this builder
         */
        public GoogleAiGeminiBatchImageModelBuilder aspectRatio(String aspectRatio) {
            this.aspectRatio = aspectRatio;
            return this;
        }

        /**
         * Sets the image size/resolution for generated images.
         *
         * <p>Supported sizes (for gemini-3-pro-image-preview only): "1K", "2K", "4K"</p>
         *
         * <p>Note: You must use an uppercase 'K'. Lowercase parameters will be rejected.</p>
         *
         * @param imageSize the image size (e.g., "2K")
         * @return this builder
         */
        public GoogleAiGeminiBatchImageModelBuilder imageSize(String imageSize) {
            this.imageSize = imageSize;
            return this;
        }

        /**
         * Sets the timeout for API requests.
         *
         * @param timeout the timeout duration
         * @return this builder
         */
        public GoogleAiGeminiBatchImageModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Enables or disables logging of both requests and responses.
         *
         * @param logRequestsAndResponses true to enable logging
         * @return this builder
         */
        public GoogleAiGeminiBatchImageModelBuilder logRequestsAndResponses(Boolean logRequestsAndResponses) {
            this.logRequestsAndResponses = logRequestsAndResponses;
            return this;
        }

        /**
         * Enables or disables logging of requests only.
         *
         * @param logRequests true to enable request logging
         * @return this builder
         */
        public GoogleAiGeminiBatchImageModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Enables or disables logging of responses only.
         *
         * @param logResponses true to enable response logging
         * @return this builder
         */
        public GoogleAiGeminiBatchImageModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * Sets a custom logger for request/response logging.
         *
         * @param logger the SLF4J logger to use
         * @return this builder
         */
        public GoogleAiGeminiBatchImageModelBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        /**
         * Sets safety settings to control content filtering.
         *
         * @param safetySettings the list of safety settings
         * @return this builder
         */
        public GoogleAiGeminiBatchImageModelBuilder safetySettings(List<GeminiSafetySetting> safetySettings) {
            this.safetySettings = safetySettings;
            return this;
        }

        /**
         * Builds a new {@link GoogleAiGeminiBatchImageModel} instance.
         *
         * @return the configured batch image model
         * @throws IllegalArgumentException if required parameters are missing
         */
        public GoogleAiGeminiBatchImageModel build() {
            return new GoogleAiGeminiBatchImageModel(this);
        }
    }

    private class ImageRequestPreparer
            implements GeminiBatchProcessor.RequestPreparer<
                    ImageGenerationRequest,
                    GeminiGenerateContentRequest,
                    GeminiGenerateContentResponse,
                    Response<@NonNull Image>> {
        private static final TypeReference<GeminiGenerateContentResponse> responseWrapperType =
                new TypeReference<>() {};
        private static final TypeReference<BatchCreateResponse.InlinedResponseWrapper<GeminiGenerateContentResponse>>
                inlinedResponseWrapperType = new TypeReference<>() {};

        @Override
        public ImageGenerationRequest prepareRequest(ImageGenerationRequest request) {
            return request;
        }

        @Override
        public GeminiGenerateContentRequest createInlinedRequest(ImageGenerationRequest request) {
            GeminiContent content =
                    new GeminiContent(List.of(GeminiPart.ofText(request.prompt())), GeminiRole.USER.toString());

            // Build imageConfig only if there are values to set
            GeminiImageConfig config =
                    (imageConfig != null && (imageConfig.aspectRatio() != null || imageConfig.imageSize() != null))
                            ? imageConfig
                            : null;

            return GeminiGenerateContentRequest.builder()
                    .contents(List.of(content))
                    .generationConfig(GeminiGenerationConfig.builder()
                            .responseModalities(responseModalities)
                            .imageConfig(config)
                            .build())
                    .safetySettings(safetySettings)
                    .build();
        }

        @Override
        public ExtractedBatchResults<Response<@NonNull Image>> extractResults(
                BatchCreateResponse<GeminiGenerateContentResponse> response) {
            if (response == null || response.inlinedResponses() == null) {
                return new ExtractedBatchResults<>(List.of(), List.of());
            }

            List<Response<@NonNull Image>> responses = new ArrayList<>();
            List<BatchRequestResponse.Operation.Status> errors = new ArrayList<>();

            for (Object wrapper : response.inlinedResponses().inlinedResponses()) {
                var typed = Json.convertValue(wrapper, inlinedResponseWrapperType);
                if (typed.response() != null) {
                    var geminiResponse = Json.convertValue(typed.response(), responseWrapperType);
                    responses.add(extractImage(geminiResponse));
                }
                if (typed.error() != null) {
                    errors.add(typed.error());
                }
            }

            return new ExtractedBatchResults<>(responses, errors);
        }

        private Response<@NonNull Image> extractImage(GeminiGenerateContentResponse geminiResponse) {
            if (geminiResponse.candidates() == null
                    || geminiResponse.candidates().isEmpty()) {
                throw new GeminiImageGenerationException("No image generated in response");
            }

            var candidate = geminiResponse.candidates().get(0);
            if (candidate.content() == null || candidate.content().parts() == null) {
                throw new GeminiImageGenerationException("No content in response candidate");
            }

            for (GeminiPart part : candidate.content().parts()) {
                if (part.inlineData() != null) {
                    Image image = Image.builder()
                            .base64Data(part.inlineData().data())
                            .mimeType(part.inlineData().mimeType())
                            .build();
                    return Response.from(image);
                }
            }

            throw new GeminiImageGenerationException("No image data found in response");
        }
    }
}
