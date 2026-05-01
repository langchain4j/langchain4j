package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.internal.Utils.quoted;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiAudioBase64Content extends MistralAiMessageContent {

    public String inputAudio;

    public MistralAiAudioBase64Content(String inputAudio, String mimeType) {
        super("input_audio");
        this.inputAudio = "data:" + mimeType + ";base64," + inputAudio;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        MistralAiAudioBase64Content that = (MistralAiAudioBase64Content) o;
        return Objects.equals(inputAudio, that.inputAudio);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), inputAudio);
    }

    @Override
    public String toString() {
        return "MistralAiAudioBase64Content{" + "inputAudio=" + inputAudio + ", type=" + quoted(type) + '}';
    }
}
