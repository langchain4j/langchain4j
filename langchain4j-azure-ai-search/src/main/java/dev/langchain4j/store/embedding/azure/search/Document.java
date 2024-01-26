package dev.langchain4j.store.embedding.azure.search;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;

public class Document {

    private String id;

    private String content;

    @JsonProperty("content_vector")
    private Collection<Float> contentVector;

    private Metadata metadata;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Collection<Float> getContentVector() {
        return contentVector;
    }

    public void setContentVector(Collection<Float> contentVector) {
        this.contentVector = contentVector;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public static class Metadata {
        private String source;

        private Collection<Attribute> attributes;

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public Collection<Attribute> getAttributes() {
            return attributes;
        }

        public void setAttributes(Collection<Attribute> attributes) {
            this.attributes = attributes;
        }

        public static class Attribute {
            private String key;

            private String value;

            public String getKey() {
                return key;
            }

            public void setKey(String key) {
                this.key = key;
            }

            public String getValue() {
                return value;
            }

            public void setValue(String value) {
                this.value = value;
            }
        }
    }
}


