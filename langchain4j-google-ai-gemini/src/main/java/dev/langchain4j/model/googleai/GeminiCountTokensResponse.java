package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiCountTokensResponse {
    Integer totalTokens;

    public GeminiCountTokensResponse() {
    }

    public Integer getTotalTokens() {
        return this.totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiCountTokensResponse)) return false;
        final GeminiCountTokensResponse other = (GeminiCountTokensResponse) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$totalTokens = this.getTotalTokens();
        final Object other$totalTokens = other.getTotalTokens();
        if (this$totalTokens == null ? other$totalTokens != null : !this$totalTokens.equals(other$totalTokens))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiCountTokensResponse;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $totalTokens = this.getTotalTokens();
        result = result * PRIME + ($totalTokens == null ? 43 : $totalTokens.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiCountTokensResponse(totalTokens=" + this.getTotalTokens() + ")";
    }
}
