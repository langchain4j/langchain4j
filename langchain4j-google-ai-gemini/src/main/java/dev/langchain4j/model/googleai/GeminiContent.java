package dev.langchain4j.model.googleai;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record GeminiContent(List<GeminiPart> parts, String role) {
    void addPart(GeminiPart part) {
        this.parts.add(part);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiPart(
            String text,
            GeminiBlob inlineData,
            GeminiFunctionCall functionCall,
            GeminiFunctionResponse functionResponse,
            GeminiFileData fileData,
            GeminiExecutableCode executableCode,
            GeminiCodeExecutionResult codeExecutionResult,
            Boolean thought,
            String thoughtSignature) {

        static Builder builder() {
            return new Builder();
        }

        Boolean isThought() {
            return thought;
        }

        static class Builder {
            private String text;
            private GeminiBlob inlineData;
            private GeminiFunctionCall functionCall;
            private GeminiFunctionResponse functionResponse;
            private GeminiFileData fileData;
            private GeminiExecutableCode executableCode;
            private GeminiCodeExecutionResult codeExecutionResult;
            private Boolean thought;
            private String thoughtSignature;

            private Builder() {
            }

            Builder text(String text) {
                this.text = text;
                return this;
            }

            Builder inlineData(GeminiBlob inlineData) {
                this.inlineData = inlineData;
                return this;
            }

            Builder functionCall(GeminiFunctionCall functionCall) {
                this.functionCall = functionCall;
                return this;
            }

            Builder functionResponse(GeminiFunctionResponse functionResponse) {
                this.functionResponse = functionResponse;
                return this;
            }

            Builder fileData(GeminiFileData fileData) {
                this.fileData = fileData;
                return this;
            }

            Builder executableCode(GeminiExecutableCode executableCode) {
                this.executableCode = executableCode;
                return this;
            }

            Builder codeExecutionResult(GeminiCodeExecutionResult codeExecutionResult) {
                this.codeExecutionResult = codeExecutionResult;
                return this;
            }

            Builder thought(Boolean thought) {
                this.thought = thought;
                return this;
            }

            Builder thoughtSignature(String thoughtSignature) {
                this.thoughtSignature = thoughtSignature;
                return this;
            }

            GeminiPart build() {
                return new GeminiPart(
                        text,
                        inlineData,
                        functionCall,
                        functionResponse,
                        fileData,
                        executableCode,
                        codeExecutionResult,
                        thought,
                        thoughtSignature
                );
            }
        }
    }
}

