package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class GoogleAiEmbeddingResponseValues {
    List<Float> values;

    public GoogleAiEmbeddingResponseValues() {
    }

    public List<Float> getValues() {
        return this.values;
    }

    public void setValues(List<Float> values) {
        this.values = values;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GoogleAiEmbeddingResponseValues)) return false;
        final GoogleAiEmbeddingResponseValues other = (GoogleAiEmbeddingResponseValues) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$values = this.getValues();
        final Object other$values = other.getValues();
        if (this$values == null ? other$values != null : !this$values.equals(other$values)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GoogleAiEmbeddingResponseValues;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $values = this.getValues();
        result = result * PRIME + ($values == null ? 43 : $values.hashCode());
        return result;
    }

    public String toString() {
        return "GoogleAiEmbeddingResponseValues(values=" + this.getValues() + ")";
    }
}
