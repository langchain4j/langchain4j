package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class GoogleAiBatchEmbeddingRequest {
    List<GoogleAiEmbeddingRequest> requests;

    public GoogleAiBatchEmbeddingRequest() {
    }

    public List<GoogleAiEmbeddingRequest> getRequests() {
        return this.requests;
    }

    public void setRequests(List<GoogleAiEmbeddingRequest> requests) {
        this.requests = requests;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GoogleAiBatchEmbeddingRequest)) return false;
        final GoogleAiBatchEmbeddingRequest other = (GoogleAiBatchEmbeddingRequest) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$requests = this.getRequests();
        final Object other$requests = other.getRequests();
        if (this$requests == null ? other$requests != null : !this$requests.equals(other$requests)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GoogleAiBatchEmbeddingRequest;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $requests = this.getRequests();
        result = result * PRIME + ($requests == null ? 43 : $requests.hashCode());
        return result;
    }

    public String toString() {
        return "GoogleAiBatchEmbeddingRequest(requests=" + this.getRequests() + ")";
    }
}
