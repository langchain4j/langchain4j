package dev.langchain4j.model.openai.internal.image;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Carries the parameters for an OpenAI image-edit request. Unlike
 * {@link GenerateImagesRequest}, this is not a JSON DTO — the OpenAI {@code /v1/images/edits}
 * endpoint accepts {@code multipart/form-data}, and this class is consumed by
 * {@code DefaultOpenAiClient.imagesEdit} which builds the multipart body directly.
 *
 * <p>Optional fields are encoded only when set. The {@code images} list maps to repeated
 * {@code image} multipart parts (one per element).
 *
 * <p>Find description of parameters
 * <a href="https://platform.openai.com/docs/api-reference/images/createEdit">here</a>.
 */
public class EditImagesRequest {

    private final String model;
    private final String prompt;
    private final List<EditImageFile> images;
    private final EditImageFile mask;
    private final Integer n;
    private final String size;
    private final String quality;
    private final String user;
    private final String responseFormat;
    private final String background;
    private final String inputFidelity;
    private final String outputFormat;
    private final Integer outputCompression;
    private final String moderation;

    public EditImagesRequest(Builder builder) {
        this.model = builder.model;
        this.prompt = builder.prompt;
        this.images = builder.images == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(builder.images));
        this.mask = builder.mask;
        this.n = builder.n;
        this.size = builder.size;
        this.quality = builder.quality;
        this.user = builder.user;
        this.responseFormat = builder.responseFormat;
        this.background = builder.background;
        this.inputFidelity = builder.inputFidelity;
        this.outputFormat = builder.outputFormat;
        this.outputCompression = builder.outputCompression;
        this.moderation = builder.moderation;
    }

    public String model() {
        return model;
    }

    public String prompt() {
        return prompt;
    }

    public List<EditImageFile> images() {
        return images;
    }

    public EditImageFile mask() {
        return mask;
    }

    public Integer n() {
        return n;
    }

    public String size() {
        return size;
    }

    public String quality() {
        return quality;
    }

    public String user() {
        return user;
    }

    public String responseFormat() {
        return responseFormat;
    }

    public String background() {
        return background;
    }

    public String inputFidelity() {
        return inputFidelity;
    }

    public String outputFormat() {
        return outputFormat;
    }

    public Integer outputCompression() {
        return outputCompression;
    }

    public String moderation() {
        return moderation;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String model;
        private String prompt;
        private List<EditImageFile> images;
        private EditImageFile mask;
        private Integer n;
        private String size;
        private String quality;
        private String user;
        private String responseFormat;
        private String background;
        private String inputFidelity;
        private String outputFormat;
        private Integer outputCompression;
        private String moderation;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder images(List<EditImageFile> images) {
            this.images = images;
            return this;
        }

        public Builder mask(EditImageFile mask) {
            this.mask = mask;
            return this;
        }

        public Builder n(Integer n) {
            this.n = n;
            return this;
        }

        public Builder size(String size) {
            this.size = size;
            return this;
        }

        public Builder quality(String quality) {
            this.quality = quality;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public Builder background(String background) {
            this.background = background;
            return this;
        }

        public Builder inputFidelity(String inputFidelity) {
            this.inputFidelity = inputFidelity;
            return this;
        }

        public Builder outputFormat(String outputFormat) {
            this.outputFormat = outputFormat;
            return this;
        }

        public Builder outputCompression(Integer outputCompression) {
            this.outputCompression = outputCompression;
            return this;
        }

        public Builder moderation(String moderation) {
            this.moderation = moderation;
            return this;
        }

        public EditImagesRequest build() {
            return new EditImagesRequest(this);
        }
    }
}
