package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiErrorContainer {
    private final GeminiError error;

    @JsonCreator
    GeminiErrorContainer(@JsonProperty("error") GeminiError error) {
        this.error = error;
    }

    public GeminiError getError() {
        return this.error;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiErrorContainer)) return false;
        final GeminiErrorContainer other = (GeminiErrorContainer) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$error = this.getError();
        final Object other$error = other.getError();
        if (this$error == null ? other$error != null : !this$error.equals(other$error)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiErrorContainer;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $error = this.getError();
        result = result * PRIME + ($error == null ? 43 : $error.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiErrorContainer(error=" + this.getError() + ")";
    }
}
