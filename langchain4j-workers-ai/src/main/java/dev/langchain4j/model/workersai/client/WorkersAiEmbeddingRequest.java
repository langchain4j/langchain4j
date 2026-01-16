package dev.langchain4j.model.workersai.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Request to compute embeddings
 */
public class WorkersAiEmbeddingRequest {

    private List<String> text = new ArrayList<>();

    /**
     * Default constructor.
     */
    public WorkersAiEmbeddingRequest() {
    }

    public List<String> getText() {
        return this.text;
    }

    public void setText(List<String> text) {
        this.text = text;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof WorkersAiEmbeddingRequest)) return false;
        final WorkersAiEmbeddingRequest other = (WorkersAiEmbeddingRequest) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$text = this.getText();
        final Object other$text = other.getText();
        if (this$text == null ? other$text != null : !this$text.equals(other$text)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof WorkersAiEmbeddingRequest;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $text = this.getText();
        result = result * PRIME + ($text == null ? 43 : $text.hashCode());
        return result;
    }

    public String toString() {
        return "WorkersAiEmbeddingRequest(text=" + this.getText() + ")";
    }
}
