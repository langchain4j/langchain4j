package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiGenerateContentResponse {

    private String responseId;
    private String modelVersion;
    private List<GeminiCandidate> candidates;
    private GeminiPromptFeedback promptFeedback;
    private GeminiUsageMetadata usageMetadata;

    @JsonCreator
    GeminiGenerateContentResponse(
            @JsonProperty("responseId") String responseId,
            @JsonProperty("modelVersion") String modelVersion,
            @JsonProperty("candidates") List<GeminiCandidate> candidates,
            @JsonProperty("promptFeedback") GeminiPromptFeedback promptFeedback,
            @JsonProperty("usageMetadata") GeminiUsageMetadata usageMetadata) {
        this.responseId = responseId;
        this.modelVersion = modelVersion;
        this.candidates = candidates;
        this.promptFeedback = promptFeedback;
        this.usageMetadata = usageMetadata;
    }

    public String getResponseId() {
        return responseId;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public List<GeminiCandidate> getCandidates() {
        return this.candidates;
    }

    public GeminiPromptFeedback getPromptFeedback() {
        return this.promptFeedback;
    }

    public GeminiUsageMetadata getUsageMetadata() {
        return this.usageMetadata;
    }

    public void setResponseId(String responseId) {
        this.responseId = responseId;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public void setCandidates(List<GeminiCandidate> candidates) {
        this.candidates = candidates;
    }

    public void setPromptFeedback(GeminiPromptFeedback promptFeedback) {
        this.promptFeedback = promptFeedback;
    }

    public void setUsageMetadata(GeminiUsageMetadata usageMetadata) {
        this.usageMetadata = usageMetadata;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        GeminiGenerateContentResponse that = (GeminiGenerateContentResponse) object;
        return Objects.equals(responseId, that.responseId)
                && Objects.equals(modelVersion, that.modelVersion)
                && Objects.equals(candidates, that.candidates)
                && Objects.equals(promptFeedback, that.promptFeedback)
                && Objects.equals(usageMetadata, that.usageMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(responseId, modelVersion, candidates, promptFeedback, usageMetadata);
    }
}
