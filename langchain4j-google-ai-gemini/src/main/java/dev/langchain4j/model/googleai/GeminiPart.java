package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiPart {

    private String text;
    private GeminiBlob inlineData;
    private GeminiFunctionCall functionCall;
    private GeminiFunctionResponse functionResponse;
    private GeminiFileData fileData;
    private GeminiExecutableCode executableCode;
    private GeminiCodeExecutionResult codeExecutionResult;
    private Boolean thought;
    private String thoughtSignature;

    @JsonCreator
    GeminiPart(
            @JsonProperty("text") String text,
            @JsonProperty("inlineData") GeminiBlob inlineData,
            @JsonProperty("functionCall") GeminiFunctionCall functionCall,
            @JsonProperty("functionResponse") GeminiFunctionResponse functionResponse,
            @JsonProperty("fileData") GeminiFileData fileData,
            @JsonProperty("executableCode") GeminiExecutableCode executableCode,
            @JsonProperty("codeExecutionResult") GeminiCodeExecutionResult codeExecutionResult,
            @JsonProperty("thought") Boolean thought,
            @JsonProperty("thoughtSignature") String thoughtSignature
    ) {
        this.text = text;
        this.inlineData = inlineData;
        this.functionCall = functionCall;
        this.functionResponse = functionResponse;
        this.fileData = fileData;
        this.executableCode = executableCode;
        this.codeExecutionResult = codeExecutionResult;
        this.thought = thought;
        this.thoughtSignature = thoughtSignature;
    }

    public static GeminiPartBuilder builder() {
        return new GeminiPartBuilder();
    }

    public String getText() {
        return this.text;
    }

    public GeminiBlob getInlineData() {
        return this.inlineData;
    }

    public GeminiFunctionCall getFunctionCall() {
        return this.functionCall;
    }

    public GeminiFunctionResponse getFunctionResponse() {
        return this.functionResponse;
    }

    public GeminiFileData getFileData() {
        return this.fileData;
    }

    public GeminiExecutableCode getExecutableCode() {
        return this.executableCode;
    }

    public GeminiCodeExecutionResult getCodeExecutionResult() {
        return this.codeExecutionResult;
    }

    public Boolean isThought() {
        return thought;
    }

    public String getThoughtSignature() {
        return thoughtSignature;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setInlineData(GeminiBlob inlineData) {
        this.inlineData = inlineData;
    }

    public void setFunctionCall(GeminiFunctionCall functionCall) {
        this.functionCall = functionCall;
    }

    public void setFunctionResponse(GeminiFunctionResponse functionResponse) {
        this.functionResponse = functionResponse;
    }

    public void setFileData(GeminiFileData fileData) {
        this.fileData = fileData;
    }

    public void setExecutableCode(GeminiExecutableCode executableCode) {
        this.executableCode = executableCode;
    }

    public void setCodeExecutionResult(GeminiCodeExecutionResult codeExecutionResult) {
        this.codeExecutionResult = codeExecutionResult;
    }

    public void setThought(Boolean thought) {
        this.thought = thought;
    }

    public void setThoughtSignature(String thoughtSignature) {
        this.thoughtSignature = thoughtSignature;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        GeminiPart that = (GeminiPart) object;
        return Objects.equals(text, that.text)
                && Objects.equals(inlineData, that.inlineData)
                && Objects.equals(functionCall, that.functionCall)
                && Objects.equals(functionResponse, that.functionResponse)
                && Objects.equals(fileData, that.fileData)
                && Objects.equals(executableCode, that.executableCode)
                && Objects.equals(codeExecutionResult, that.codeExecutionResult)
                && Objects.equals(thought, that.thought)
                && Objects.equals(thoughtSignature, that.thoughtSignature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, inlineData, functionCall, functionResponse, fileData,
                executableCode, codeExecutionResult, thought, thoughtSignature);
    }

    @Override
    public String toString() {
        return "GeminiPart{" +
                "text='" + text + '\'' +
                ", inlineData=" + inlineData +
                ", functionCall=" + functionCall +
                ", functionResponse=" + functionResponse +
                ", fileData=" + fileData +
                ", executableCode=" + executableCode +
                ", codeExecutionResult=" + codeExecutionResult +
                ", thought=" + thought +
                ", thoughtSignature='" + thoughtSignature + '\'' +
                '}';
    }

    public static class GeminiPartBuilder {

        private String text;
        private GeminiBlob inlineData;
        private GeminiFunctionCall functionCall;
        private GeminiFunctionResponse functionResponse;
        private GeminiFileData fileData;
        private GeminiExecutableCode executableCode;
        private GeminiCodeExecutionResult codeExecutionResult;
        private Boolean thought;
        private String thoughtSignature;

        GeminiPartBuilder() {
        }

        public GeminiPartBuilder text(String text) {
            this.text = text;
            return this;
        }

        public GeminiPartBuilder inlineData(GeminiBlob inlineData) {
            this.inlineData = inlineData;
            return this;
        }

        public GeminiPartBuilder functionCall(GeminiFunctionCall functionCall) {
            this.functionCall = functionCall;
            return this;
        }

        public GeminiPartBuilder functionResponse(GeminiFunctionResponse functionResponse) {
            this.functionResponse = functionResponse;
            return this;
        }

        public GeminiPartBuilder fileData(GeminiFileData fileData) {
            this.fileData = fileData;
            return this;
        }

        public GeminiPartBuilder executableCode(GeminiExecutableCode executableCode) {
            this.executableCode = executableCode;
            return this;
        }

        public GeminiPartBuilder codeExecutionResult(GeminiCodeExecutionResult codeExecutionResult) {
            this.codeExecutionResult = codeExecutionResult;
            return this;
        }

        public GeminiPartBuilder thought(Boolean thought) {
            this.thought = thought;
            return this;
        }

        public GeminiPartBuilder thoughtSignature(String thoughtSignature) {
            this.thoughtSignature = thoughtSignature;
            return this;
        }

        public GeminiPart build() {
            return new GeminiPart(this.text, this.inlineData, this.functionCall, this.functionResponse, this.fileData,
                    this.executableCode, this.codeExecutionResult, this.thought, this.thoughtSignature);
        }
    }
}
