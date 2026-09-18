package dev.langchain4j.model.cohere;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Wire request for Cohere's <a href="https://docs.cohere.com/reference/embed">v2 embed endpoint</a>
 * ({@code /v2/embed}), used by the multimodal Embed v4 model. Each input carries an ordered list of content
 * parts (text / image) that are fused into a single embedding. Serialized in snake_case by the client mapper.
 */
class EmbedV2Request {

    private final String model;
    private final String inputType;
    private final List<String> embeddingTypes;
    private final List<V2Input> inputs;

    EmbedV2Request(String model, String inputType, List<String> embeddingTypes, List<V2Input> inputs) {
        this.model = model;
        this.inputType = inputType;
        this.embeddingTypes = embeddingTypes;
        this.inputs = inputs;
    }

    public String getModel() {
        return model;
    }

    public String getInputType() {
        return inputType;
    }

    public List<String> getEmbeddingTypes() {
        return embeddingTypes;
    }

    public List<V2Input> getInputs() {
        return inputs;
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private String model;
        private String inputType;
        private List<String> embeddingTypes;
        private List<V2Input> inputs;

        Builder model(String model) {
            this.model = model;
            return this;
        }

        Builder inputType(String inputType) {
            this.inputType = inputType;
            return this;
        }

        Builder embeddingTypes(List<String> embeddingTypes) {
            this.embeddingTypes = embeddingTypes;
            return this;
        }

        Builder inputs(List<V2Input> inputs) {
            this.inputs = inputs;
            return this;
        }

        EmbedV2Request build() {
            return new EmbedV2Request(model, inputType, embeddingTypes, inputs);
        }
    }

    /** A single input: an ordered list of content parts fused into one embedding. */
    static class V2Input {

        private final List<V2Content> content;

        V2Input(List<V2Content> content) {
            this.content = content;
        }

        public List<V2Content> getContent() {
            return content;
        }
    }

    /** One content part: {@code type} is {@code text} or {@code image_url} (with a nested {@code {url}} object). */
    static class V2Content {

        private final String type;
        private final String text;
        private final ImageUrl imageUrl;

        private V2Content(String type, String text, ImageUrl imageUrl) {
            this.type = type;
            this.text = text;
            this.imageUrl = imageUrl;
        }

        static V2Content text(String text) {
            return new V2Content("text", text, null);
        }

        static V2Content imageUrl(String url) {
            return new V2Content("image_url", null, new ImageUrl(url));
        }

        public String getType() {
            return type;
        }

        public String getText() {
            return text;
        }

        @JsonProperty("image_url")
        public ImageUrl getImageUrl() {
            return imageUrl;
        }
    }

    static class ImageUrl {

        private final String url;

        ImageUrl(String url) {
            this.url = url;
        }

        public String getUrl() {
            return url;
        }
    }
}
