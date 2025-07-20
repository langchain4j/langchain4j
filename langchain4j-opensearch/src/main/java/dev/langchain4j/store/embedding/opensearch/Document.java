package dev.langchain4j.store.embedding.opensearch;

import java.util.Map;

class Document {

    private float[] vector;
    private String text;
    private Map<String, Object> metadata;

    public Document(float[] vector, String text, Map<String, Object> metadata) {
        this.vector = vector;
        this.text = text;
        this.metadata = metadata;
    }

    public Document() {
    }

    public static DocumentBuilder builder() {
        return new DocumentBuilder();
    }

    public float[] getVector() {
        return this.vector;
    }

    public String getText() {
        return this.text;
    }

    public Map<String, Object> getMetadata() {
        return this.metadata;
    }

    public void setVector(float[] vector) {
        this.vector = vector;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof Document)) return false;
        final Document other = (Document) o;
        if (!other.canEqual((Object) this)) return false;
        if (!java.util.Arrays.equals(this.getVector(), other.getVector())) return false;
        final Object this$text = this.getText();
        final Object other$text = other.getText();
        if (this$text == null ? other$text != null : !this$text.equals(other$text)) return false;
        final Object this$metadata = this.getMetadata();
        final Object other$metadata = other.getMetadata();
        if (this$metadata == null ? other$metadata != null : !this$metadata.equals(other$metadata)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof Document;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + java.util.Arrays.hashCode(this.getVector());
        final Object $text = this.getText();
        result = result * PRIME + ($text == null ? 43 : $text.hashCode());
        final Object $metadata = this.getMetadata();
        result = result * PRIME + ($metadata == null ? 43 : $metadata.hashCode());
        return result;
    }

    public String toString() {
        return "Document(vector=" + java.util.Arrays.toString(this.getVector()) + ", text=" + this.getText() + ", metadata=" + this.getMetadata() + ")";
    }

    public static class DocumentBuilder {
        private float[] vector;
        private String text;
        private Map<String, Object> metadata;

        DocumentBuilder() {
        }

        public DocumentBuilder vector(float[] vector) {
            this.vector = vector;
            return this;
        }

        public DocumentBuilder text(String text) {
            this.text = text;
            return this;
        }

        public DocumentBuilder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Document build() {
            return new Document(this.vector, this.text, this.metadata);
        }

        public String toString() {
            return "Document.DocumentBuilder(vector=" + java.util.Arrays.toString(this.vector) + ", text=" + this.text + ", metadata=" + this.metadata + ")";
        }
    }
}
