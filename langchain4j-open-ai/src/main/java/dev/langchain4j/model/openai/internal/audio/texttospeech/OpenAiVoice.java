package dev.langchain4j.model.openai.internal.audio.texttospeech;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * The {@code voice} parameter of the speech API. On the wire it is either:
 * <ul>
 *     <li>a built-in voice, serialized as a JSON string: {@code "alloy"}</li>
 *     <li>a custom voice, serialized as an object: {@code {"id": "voice_1234"}}</li>
 * </ul>
 */
public sealed interface OpenAiVoice permits OpenAiVoice.BuiltIn, OpenAiVoice.Custom {

    static OpenAiVoice custom(String id) {
        return new Custom(id);
    }

    /**
     * A built-in voice, serialized as its name.
     */
    enum BuiltIn implements OpenAiVoice {
        @JsonProperty("alloy")
        ALLOY,
        @JsonProperty("ash")
        ASH,
        @JsonProperty("ballad")
        BALLAD,
        @JsonProperty("coral")
        CORAL,
        @JsonProperty("echo")
        ECHO,
        @JsonProperty("fable")
        FABLE,
        @JsonProperty("onyx")
        ONYX,
        @JsonProperty("nova")
        NOVA,
        @JsonProperty("sage")
        SAGE,
        @JsonProperty("shimmer")
        SHIMMER,
        @JsonProperty("verse")
        VERSE,
        @JsonProperty("marin")
        MARIN,
        @JsonProperty("cedar")
        CEDAR
    }

    /**
     * A custom voice, serialized as an object holding its ID.
     */
    final class Custom implements OpenAiVoice {

        @JsonProperty
        private final String id;

        private Custom(String id) {
            this.id = ensureNotBlank(id, "id");
        }

        public String id() {
            return id;
        }

        @Override
        public boolean equals(Object another) {
            if (this == another) return true;
            return another instanceof Custom custom && Objects.equals(id, custom.id);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }

        @Override
        public String toString() {
            return "Custom{id=" + id + "}";
        }
    }
}
