package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.mutableCopy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
record GeminiContent(List<GeminiPart> parts, String role) {

    GeminiContent {
        parts = mutableCopy(parts);
    }

    void addPart(GeminiPart part) {
        parts.add(part);
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
            String thoughtSignature,
            GeminiMediaResolution mediaResolution) {

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
            private GeminiMediaResolution mediaResolution;

            private Builder() {}

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

            Builder mediaResolution(GeminiMediaResolution mediaResolution) {
                this.mediaResolution = mediaResolution;
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
                        thoughtSignature,
                        mediaResolution);
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record GeminiBlob(String mimeType, String data) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record GeminiFunctionCall(String name, Map<String, Object> args) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record GeminiFunctionResponse(String name, Map<String, String> response) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record GeminiFileData(String mimeType, String fileUri) {}

        /**
         * Wrapper for per-part media resolution setting (Currently Gemini 3 only).
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        record GeminiMediaResolution(GeminiMediaResolutionLevel level) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record GeminiExecutableCode(GeminiLanguage programmingLanguage, String code) {
            enum GeminiLanguage {
                PYTHON,
                LANGUAGE_UNSPECIFIED;

                @Override
                public String toString() {
                    return name().toLowerCase();
                }
            }

            GeminiExecutableCode {
                if (programmingLanguage == null) {
                    programmingLanguage = GeminiLanguage.PYTHON;
                }
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record GeminiCodeExecutionResult(GeminiOutcome outcome, String output) {
            // TODO how to deal with the non-OK outcomes?
            enum GeminiOutcome {
                OUTCOME_UNSPECIFIED,
                OUTCOME_OK,
                OUTCOME_FAILED,
                OUTCOME_DEADLINE_EXCEEDED;

                @Override
                public String toString() {
                    return this.name().toLowerCase();
                }
            }
        }
    }
}
