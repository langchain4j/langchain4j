package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiSemanticRetrieverChunk {
    private String source;
    private String chunk;

    @JsonCreator
    GeminiSemanticRetrieverChunk(@JsonProperty("source") String source, @JsonProperty("chunk") String chunk) {
        this.source = source;
        this.chunk = chunk;
    }

    public static GeminiSemanticRetrieverChunkBuilder builder() {
        return new GeminiSemanticRetrieverChunkBuilder();
    }

    public String getSource() {
        return this.source;
    }

    public String getChunk() {
        return this.chunk;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setChunk(String chunk) {
        this.chunk = chunk;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiSemanticRetrieverChunk)) return false;
        final GeminiSemanticRetrieverChunk other = (GeminiSemanticRetrieverChunk) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$source = this.getSource();
        final Object other$source = other.getSource();
        if (this$source == null ? other$source != null : !this$source.equals(other$source)) return false;
        final Object this$chunk = this.getChunk();
        final Object other$chunk = other.getChunk();
        if (this$chunk == null ? other$chunk != null : !this$chunk.equals(other$chunk)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiSemanticRetrieverChunk;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $source = this.getSource();
        result = result * PRIME + ($source == null ? 43 : $source.hashCode());
        final Object $chunk = this.getChunk();
        result = result * PRIME + ($chunk == null ? 43 : $chunk.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiSemanticRetrieverChunk(source=" + this.getSource() + ", chunk=" + this.getChunk() + ")";
    }

    public static class GeminiSemanticRetrieverChunkBuilder {
        private String source;
        private String chunk;

        GeminiSemanticRetrieverChunkBuilder() {
        }

        public GeminiSemanticRetrieverChunkBuilder source(String source) {
            this.source = source;
            return this;
        }

        public GeminiSemanticRetrieverChunkBuilder chunk(String chunk) {
            this.chunk = chunk;
            return this;
        }

        public GeminiSemanticRetrieverChunk build() {
            return new GeminiSemanticRetrieverChunk(this.source, this.chunk);
        }

        public String toString() {
            return "GeminiSemanticRetrieverChunk.GeminiSemanticRetrieverChunkBuilder(source=" + this.source + ", chunk=" + this.chunk + ")";
        }
    }
}
