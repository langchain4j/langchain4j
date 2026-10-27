package dev.langchain4j.model.openai.internal.audio.texttospeech;

import dev.langchain4j.model.openai.internal.audio.transcription.AudioTokenUsage;

/**
 * Represents the response of the OpenAI speech API.
 * The body is either raw binary audio, or (when {@code stream_format} is {@code sse}) a stream of
 * {@link OpenAiTextToSpeechStreamEvent}s, so this is built from the raw HTTP response rather than deserialized.
 * {@code contentType} carries the audio format, and {@code usage} the token usage
 * (only reported by the API in {@code sse} mode).
 */
public class OpenAiTextToSpeechResponse {

    private final byte[] audio;
    private final String contentType;
    private final AudioTokenUsage usage;

    public OpenAiTextToSpeechResponse(byte[] audio, String contentType) {
        this(audio, contentType, null);
    }

    public OpenAiTextToSpeechResponse(byte[] audio, String contentType, AudioTokenUsage usage) {
        this.audio = audio;
        this.contentType = contentType;
        this.usage = usage;
    }

    public byte[] audio() {
        return audio;
    }

    public String contentType() {
        return contentType;
    }

    public AudioTokenUsage usage() {
        return usage;
    }

    public static OpenAiTextToSpeechResponse from(byte[] audio, String contentType) {
        return new OpenAiTextToSpeechResponse(audio, contentType);
    }

    public static OpenAiTextToSpeechResponse from(byte[] audio, String contentType, AudioTokenUsage usage) {
        return new OpenAiTextToSpeechResponse(audio, contentType, usage);
    }
}
