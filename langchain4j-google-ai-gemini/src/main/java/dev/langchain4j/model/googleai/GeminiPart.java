package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiPart {
    private String text;
    private GeminiBlob inlineData;
    private GeminiFunctionCall functionCall;
    private GeminiFunctionResponse functionResponse;
    private GeminiFileData fileData;
    private GeminiExecutableCode executableCode;
    private GeminiCodeExecutionResult codeExecutionResult;

    @JsonCreator
    GeminiPart(
            @JsonProperty("text") String text,
            @JsonProperty("inlineData") GeminiBlob inlineData,
            @JsonProperty("functionCall") GeminiFunctionCall functionCall,
            @JsonProperty("functionResponse") GeminiFunctionResponse functionResponse,
            @JsonProperty("fileData") GeminiFileData fileData,
            @JsonProperty("executableCode") GeminiExecutableCode executableCode,
            @JsonProperty("codeExecutionResult") GeminiCodeExecutionResult codeExecutionResult
    ) {
        this.text = text;
        this.inlineData = inlineData;
        this.functionCall = functionCall;
        this.functionResponse = functionResponse;
        this.fileData = fileData;
        this.executableCode = executableCode;
        this.codeExecutionResult = codeExecutionResult;
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

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiPart)) return false;
        final GeminiPart other = (GeminiPart) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$text = this.getText();
        final Object other$text = other.getText();
        if (this$text == null ? other$text != null : !this$text.equals(other$text)) return false;
        final Object this$inlineData = this.getInlineData();
        final Object other$inlineData = other.getInlineData();
        if (this$inlineData == null ? other$inlineData != null : !this$inlineData.equals(other$inlineData))
            return false;
        final Object this$functionCall = this.getFunctionCall();
        final Object other$functionCall = other.getFunctionCall();
        if (this$functionCall == null ? other$functionCall != null : !this$functionCall.equals(other$functionCall))
            return false;
        final Object this$functionResponse = this.getFunctionResponse();
        final Object other$functionResponse = other.getFunctionResponse();
        if (this$functionResponse == null ? other$functionResponse != null : !this$functionResponse.equals(other$functionResponse))
            return false;
        final Object this$fileData = this.getFileData();
        final Object other$fileData = other.getFileData();
        if (this$fileData == null ? other$fileData != null : !this$fileData.equals(other$fileData)) return false;
        final Object this$executableCode = this.getExecutableCode();
        final Object other$executableCode = other.getExecutableCode();
        if (this$executableCode == null ? other$executableCode != null : !this$executableCode.equals(other$executableCode))
            return false;
        final Object this$codeExecutionResult = this.getCodeExecutionResult();
        final Object other$codeExecutionResult = other.getCodeExecutionResult();
        if (this$codeExecutionResult == null ? other$codeExecutionResult != null : !this$codeExecutionResult.equals(other$codeExecutionResult))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiPart;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $text = this.getText();
        result = result * PRIME + ($text == null ? 43 : $text.hashCode());
        final Object $inlineData = this.getInlineData();
        result = result * PRIME + ($inlineData == null ? 43 : $inlineData.hashCode());
        final Object $functionCall = this.getFunctionCall();
        result = result * PRIME + ($functionCall == null ? 43 : $functionCall.hashCode());
        final Object $functionResponse = this.getFunctionResponse();
        result = result * PRIME + ($functionResponse == null ? 43 : $functionResponse.hashCode());
        final Object $fileData = this.getFileData();
        result = result * PRIME + ($fileData == null ? 43 : $fileData.hashCode());
        final Object $executableCode = this.getExecutableCode();
        result = result * PRIME + ($executableCode == null ? 43 : $executableCode.hashCode());
        final Object $codeExecutionResult = this.getCodeExecutionResult();
        result = result * PRIME + ($codeExecutionResult == null ? 43 : $codeExecutionResult.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiPart(text=" + this.getText() + ", inlineData=" + this.getInlineData() + ", functionCall=" + this.getFunctionCall() + ", functionResponse=" + this.getFunctionResponse() + ", fileData=" + this.getFileData() + ", executableCode=" + this.getExecutableCode() + ", codeExecutionResult=" + this.getCodeExecutionResult() + ")";
    }

    public static class GeminiPartBuilder {
        private String text;
        private GeminiBlob inlineData;
        private GeminiFunctionCall functionCall;
        private GeminiFunctionResponse functionResponse;
        private GeminiFileData fileData;
        private GeminiExecutableCode executableCode;
        private GeminiCodeExecutionResult codeExecutionResult;

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

        public GeminiPart build() {
            return new GeminiPart(this.text, this.inlineData, this.functionCall, this.functionResponse, this.fileData, this.executableCode, this.codeExecutionResult);
        }

        public String toString() {
            return "GeminiPart.GeminiPartBuilder(text=" + this.text + ", inlineData=" + this.inlineData + ", functionCall=" + this.functionCall + ", functionResponse=" + this.functionResponse + ", fileData=" + this.fileData + ", executableCode=" + this.executableCode + ", codeExecutionResult=" + this.codeExecutionResult + ")";
        }
    }
}
