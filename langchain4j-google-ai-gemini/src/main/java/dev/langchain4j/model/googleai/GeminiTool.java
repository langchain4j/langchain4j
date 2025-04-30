package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiTool {
    private List<GeminiFunctionDeclaration> functionDeclarations;
    private GeminiCodeExecution codeExecution;

    @JsonCreator
    GeminiTool(@JsonProperty("functionDeclarations") List<GeminiFunctionDeclaration> functionDeclarations, @JsonProperty("codeExecution") GeminiCodeExecution codeExecution) {
        this.functionDeclarations = functionDeclarations;
        this.codeExecution = codeExecution;
    }

    public static GeminiToolBuilder builder() {
        return new GeminiToolBuilder();
    }

    public List<GeminiFunctionDeclaration> getFunctionDeclarations() {
        return this.functionDeclarations;
    }

    public GeminiCodeExecution getCodeExecution() {
        return this.codeExecution;
    }

    public void setFunctionDeclarations(List<GeminiFunctionDeclaration> functionDeclarations) {
        this.functionDeclarations = functionDeclarations;
    }

    public void setCodeExecution(GeminiCodeExecution codeExecution) {
        this.codeExecution = codeExecution;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiTool)) return false;
        final GeminiTool other = (GeminiTool) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$functionDeclarations = this.getFunctionDeclarations();
        final Object other$functionDeclarations = other.getFunctionDeclarations();
        if (this$functionDeclarations == null ? other$functionDeclarations != null : !this$functionDeclarations.equals(other$functionDeclarations))
            return false;
        final Object this$codeExecution = this.getCodeExecution();
        final Object other$codeExecution = other.getCodeExecution();
        if (this$codeExecution == null ? other$codeExecution != null : !this$codeExecution.equals(other$codeExecution))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiTool;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $functionDeclarations = this.getFunctionDeclarations();
        result = result * PRIME + ($functionDeclarations == null ? 43 : $functionDeclarations.hashCode());
        final Object $codeExecution = this.getCodeExecution();
        result = result * PRIME + ($codeExecution == null ? 43 : $codeExecution.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiTool(functionDeclarations=" + this.getFunctionDeclarations() + ", codeExecution=" + this.getCodeExecution() + ")";
    }

    public static class GeminiToolBuilder {
        private List<GeminiFunctionDeclaration> functionDeclarations;
        private GeminiCodeExecution codeExecution;

        GeminiToolBuilder() {
        }

        public GeminiToolBuilder functionDeclarations(List<GeminiFunctionDeclaration> functionDeclarations) {
            this.functionDeclarations = functionDeclarations;
            return this;
        }

        public GeminiToolBuilder codeExecution(GeminiCodeExecution codeExecution) {
            this.codeExecution = codeExecution;
            return this;
        }

        public GeminiTool build() {
            return new GeminiTool(this.functionDeclarations, this.codeExecution);
        }

        public String toString() {
            return "GeminiTool.GeminiToolBuilder(functionDeclarations=" + this.functionDeclarations + ", codeExecution=" + this.codeExecution + ")";
        }
    }
}
