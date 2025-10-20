package dev.langchain4j.model.googleai.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.langchain4j.Internal;

@Internal
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiCodeExecution {
    public GeminiCodeExecution() {}

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiCodeExecution)) return false;
        final GeminiCodeExecution other = (GeminiCodeExecution) o;
        if (!other.canEqual((Object) this)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiCodeExecution;
    }

    public int hashCode() {
        int result = 1;
        return result;
    }

    public String toString() {
        return "GeminiCodeExecution()";
    }
}
