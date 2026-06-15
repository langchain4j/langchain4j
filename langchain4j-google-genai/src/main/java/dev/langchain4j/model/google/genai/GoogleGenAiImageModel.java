package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GoogleSearch;
import com.google.genai.types.GroundingMetadata;
import com.google.genai.types.ImageConfig;
import com.google.genai.types.Part;
import com.google.genai.types.SafetySetting;
import com.google.genai.types.Tool;
import dev.langchain4j.Experimental;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a Google GenAI model for image generation and editing using the official com.google.genai SDK.
 */
@Experimental
public class GoogleGenAiImageModel implements ImageModel {

    private static final Logger log = LoggerFactory.getLogger(GoogleGenAiImageModel.class);

    private final Client client;
    private final String modelName;
    private final Integer maxRetries;
    private final List<SafetySetting> safetySettings;
    private final boolean useGoogleSearchGrounding;
    private final String aspectRatio;
    private final String imageSize;
    private final String personGeneration;
    private final Map<String, String> labels;
    private final boolean logRequests;
    private final boolean logResponses;

    private GoogleGenAiImageModel(Builder builder) {
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
        this.maxRetries = getOrDefault(builder.maxRetries, 3);
        this.safetySettings = copy(builder.safetySettings);
        this.useGoogleSearchGrounding = getOrDefault(builder.useGoogleSearchGrounding, false);
        this.aspectRatio = builder.aspectRatio;
        this.imageSize = builder.imageSize;
        this.personGeneration = builder.personGeneration;
        this.labels = builder.labels != null ? new HashMap<>(builder.labels) : null;
        this.logRequests = getOrDefault(builder.logRequests, false);
        this.logResponses = getOrDefault(builder.logResponses, false);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Response<Image> generate(String prompt) {
        ensureNotBlank(prompt, "prompt");

        Content content =
                Content.builder().parts(List.of(Part.fromText(prompt))).build();

        return generateImageResponse(List.of(content));
    }

    @Override
    public Response<Image> edit(Image image, String prompt) {
        ensureNotNull(image, "image");
        ensureNotBlank(prompt, "prompt");

        List<Part> parts = new ArrayList<>();
        parts.add(Part.fromText(prompt));
        parts.add(createImagePart(image));

        Content content = Content.builder().parts(parts).build();

        return generateImageResponse(List.of(content));
    }

    @Override
    public Response<Image> edit(Image image, Image mask, String prompt) {
        ensureNotNull(image, "image");
        ensureNotNull(mask, "mask");
        ensureNotBlank(prompt, "prompt");

        List<Part> parts = new ArrayList<>();
        parts.add(Part.fromText(prompt));
        parts.add(createImagePart(image));
        parts.add(createImagePart(mask));

        Content content = Content.builder().parts(parts).build();

        return generateImageResponse(List.of(content));
    }

    private Response<Image> generateImageResponse(List<Content> contents) {
        GenerateContentConfig config = createGenerateContentConfig();

        if (logRequests) {
            log.info("Request:\n- model: {}\n- contents: {}\n- config: {}", modelName, contents, config);
        }

        GenerateContentResponse response = withRetryMappingExceptions(
                () -> client.models.generateContent(modelName, contents, config), maxRetries);

        Response<Image> imageResponse = toResponse(response);

        if (logResponses) {
            log.info("Response:\n- model: {}\n- response: {}", modelName, imageResponse);
        }

        return imageResponse;
    }

    private GenerateContentConfig createGenerateContentConfig() {
        GenerateContentConfig.Builder configBuilder =
                GenerateContentConfig.builder().responseModalities(List.of("IMAGE"));

        if (!safetySettings.isEmpty()) {
            configBuilder.safetySettings(safetySettings);
        }

        if (useGoogleSearchGrounding) {
            configBuilder.tools(List.of(
                    Tool.builder().googleSearch(GoogleSearch.builder().build()).build()));
        }

        if (aspectRatio != null || imageSize != null || personGeneration != null) {
            ImageConfig.Builder imageConfigBuilder = ImageConfig.builder();
            if (aspectRatio != null) {
                imageConfigBuilder.aspectRatio(aspectRatio);
            }
            if (imageSize != null) {
                imageConfigBuilder.imageSize(imageSize);
            }
            if (personGeneration != null) {
                imageConfigBuilder.personGeneration(personGeneration);
            }
            configBuilder.imageConfig(imageConfigBuilder.build());
        }

        if (labels != null && !labels.isEmpty()) {
            configBuilder.labels(labels);
        }

        return configBuilder.build();
    }

    private Part createImagePart(Image image) {
        String base64Data = image.base64Data();
        String mimeType = image.mimeType();
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "image/png";
        }

        if (base64Data == null && image.url() != null) {
            return Part.fromUri(image.url().toString(), mimeType);
        }

        byte[] imageBytes = Base64.getDecoder().decode(ensureNotBlank(base64Data, "image.base64Data"));
        return Part.fromBytes(imageBytes, mimeType);
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Response<Image> toResponse(GenerateContentResponse response) {
        if (response.parts() == null || response.parts().isEmpty()) {
            throw new RuntimeException("No image generated in response");
        }

        Map<String, Object> metadata = new HashMap<>();
        if (response.candidates().isPresent() && !response.candidates().get().isEmpty()) {
            Candidate candidate = response.candidates().get().get(0);
            if (candidate.groundingMetadata().isPresent()) {
                GroundingMetadata gm = candidate.groundingMetadata().get();
                try {
                    Map<String, Object> groundingMap =
                            OBJECT_MAPPER.readValue(gm.toJson(), new TypeReference<Map<String, Object>>() {});
                    metadata.put("groundingMetadata", groundingMap);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to parse grounding metadata", e);
                }
            }
        }

        for (Part part : response.parts()) {
            if (part.inlineData().isPresent()) {
                var blob = part.inlineData().get();
                if (blob.data().isPresent()) {
                    byte[] bytes = blob.data().get();
                    String base64Data = Base64.getEncoder().encodeToString(bytes);
                    String mimeType = blob.mimeType().orElse("image/png");

                    Image image = Image.builder()
                            .base64Data(base64Data)
                            .mimeType(mimeType)
                            .build();

                    return Response.from(image, null, null, metadata);
                }
            }
        }

        throw new RuntimeException("No image data found in response");
    }

    public static class Builder {
        private Client client;
        private String apiKey;
        private GoogleCredentials googleCredentials;
        private String projectId;
        private String location;
        private Duration timeout;
        private String modelName;
        private Integer maxRetries;
        private List<SafetySetting> safetySettings;
        private Boolean useGoogleSearchGrounding;
        private String aspectRatio;
        private String imageSize;
        private String personGeneration;
        private Boolean logRequests;
        private Boolean logResponses;
        private String apiEndpoint;
        private Map<String, String> customHeaders;
        private Map<String, String> labels;

        public Builder client(Client client) {
            this.client = client;
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

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
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

        public Builder safetySettings(List<SafetySetting> safetySettings) {
            this.safetySettings = safetySettings;
            return this;
        }

        public Builder useGoogleSearchGrounding(Boolean useGoogleSearchGrounding) {
            this.useGoogleSearchGrounding = useGoogleSearchGrounding;
            return this;
        }

        public Builder aspectRatio(String aspectRatio) {
            this.aspectRatio = aspectRatio;
            return this;
        }

        public Builder imageSize(String imageSize) {
            this.imageSize = imageSize;
            return this;
        }

        public Builder personGeneration(String personGeneration) {
            this.personGeneration = personGeneration;
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

        public Builder apiEndpoint(String apiEndpoint) {
            this.apiEndpoint = apiEndpoint;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public Builder labels(Map<String, String> labels) {
            this.labels = labels;
            return this;
        }

        public GoogleGenAiImageModel build() {
            return new GoogleGenAiImageModel(this);
        }
    }
}
