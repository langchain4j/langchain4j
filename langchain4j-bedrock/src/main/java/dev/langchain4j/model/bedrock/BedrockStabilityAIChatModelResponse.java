package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.BedrockChatModelResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * @deprecated Will be removed in the next release, this functionality will not be supported anymore.
 * Please reach out (via GitHub issues) if you use it.
 */
@Deprecated(forRemoval = true, since = "1.0.0-beta2")
@Getter
@Setter
public class BedrockStabilityAIChatModelResponse implements BedrockChatModelResponse {

    @Getter
    @Setter
    public static class Artifact {
        private String base64;
        private int seed;
        private String finishReason;
    }

    private String result;
    private List<Artifact> artifacts;

    @Override
    public String getOutputText() {
        return artifacts.get(0).base64;
    }

    @Override
    public FinishReason getFinishReason() {
        switch (artifacts.get(0).finishReason) {
            case "SUCCESS":
                return FinishReason.STOP;
            case "CONTENT_FILTERED":
                return FinishReason.CONTENT_FILTER;
            default:
                throw new IllegalArgumentException("Unknown stop reason: " + artifacts.get(0).finishReason);
        }
    }

    @Override
    public TokenUsage getTokenUsage() {
        return null;
    }
}
