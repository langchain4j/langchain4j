package dev.langchain4j.model.openai.internal.audio.texttospeech;

/**
 * Represents the response of the OpenAI speech API.
 * The body is raw binary audio (not JSON), so this is built from the raw HTTP response
 * rather than deserialized; {@code contentType} carries the audio format reported by the API.
 */
public class OpenAiTextToSpeechResponse {

    private final byte[] audio;
    private final String contentType;

    public OpenAiTextToSpeechResponse(byte[] audio, String contentType) {
        this.audio = audio;
        this.contentType = contentType;
    }

    public byte[] audio() {
        return audio;
    }

    public String contentType() {
        return contentType;
    }

    public static OpenAiTextToSpeechResponse from(byte[] audio, String contentType) {
        return new OpenAiTextToSpeechResponse(audio, contentType);
    }
}
