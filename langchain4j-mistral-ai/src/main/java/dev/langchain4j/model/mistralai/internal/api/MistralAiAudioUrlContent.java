package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.internal.Utils.quoted;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MistralAiAudioUrlContent extends MistralAiMessageContent {

    public String inputAudio;

    public MistralAiAudioUrlContent(String inputAudio) {
        super("input_audio");
        this.inputAudio = inputAudio;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        MistralAiAudioUrlContent that = (MistralAiAudioUrlContent) o;
        return inputAudio.equals(that.inputAudio);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), inputAudio);
    }

    @Override
    public String toString() {
        return "MistralAiAudioUrlContent{" + "inputAudio=" + inputAudio + ", type=" + quoted(type) + '}';
    }
}
