package dev.langchain4j.model.voyageai;

import java.util.List;

/**
 * Wire request for Voyage's multimodal embeddings endpoint ({@code /multimodalembeddings}).
 * Each input carries an ordered list of content blocks (text / image) that are fused into a single embedding.
 * Field names are serialized in snake_case by {@link VoyageAiJsonUtils}.
 */
class MultimodalEmbeddingRequest {

    private List<MultimodalInput> inputs;
    private String model;
    private String inputType;
    private Boolean truncation;

    MultimodalEmbeddingRequest(List<MultimodalInput> inputs, String model, String inputType, Boolean truncation) {
        this.inputs = inputs;
        this.model = model;
        this.inputType = inputType;
        this.truncation = truncation;
    }

    public List<MultimodalInput> getInputs() {
        return inputs;
    }

    public String getModel() {
        return model;
    }

    public String getInputType() {
        return inputType;
    }

    public Boolean getTruncation() {
        return truncation;
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private List<MultimodalInput> inputs;
        private String model;
        private String inputType;
        private Boolean truncation;

        Builder inputs(List<MultimodalInput> inputs) {
            this.inputs = inputs;
            return this;
        }

        Builder model(String model) {
            this.model = model;
            return this;
        }

        Builder inputType(String inputType) {
            this.inputType = inputType;
            return this;
        }

        Builder truncation(Boolean truncation) {
            this.truncation = truncation;
            return this;
        }

        MultimodalEmbeddingRequest build() {
            return new MultimodalEmbeddingRequest(inputs, model, inputType, truncation);
        }
    }

    /** A single input: an ordered list of {@link ContentBlock}s fused into one embedding. */
    static class MultimodalInput {

        private final List<ContentBlock> content;

        MultimodalInput(List<ContentBlock> content) {
            this.content = content;
        }

        public List<ContentBlock> getContent() {
            return content;
        }
    }

    /**
     * One content part. {@code type} is one of {@code text}, {@code image_url}, {@code image_base64};
     * exactly one of the value fields is populated (nulls are omitted from the JSON).
     */
    static class ContentBlock {

        private final String type;
        private final String text;
        private final String imageUrl;
        private final String imageBase64;

        private ContentBlock(String type, String text, String imageUrl, String imageBase64) {
            this.type = type;
            this.text = text;
            this.imageUrl = imageUrl;
            this.imageBase64 = imageBase64;
        }

        static ContentBlock text(String text) {
            return new ContentBlock("text", text, null, null);
        }

        static ContentBlock imageUrl(String imageUrl) {
            return new ContentBlock("image_url", null, imageUrl, null);
        }

        static ContentBlock imageBase64(String imageBase64) {
            return new ContentBlock("image_base64", null, null, imageBase64);
        }

        public String getType() {
            return type;
        }

        public String getText() {
            return text;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public String getImageBase64() {
            return imageBase64;
        }
    }
}
