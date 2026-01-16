package dev.langchain4j.model.openai.internal.audio.transcription;

import dev.langchain4j.data.audio.Audio;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class AudioFile {

    private final Audio audio;

    private AudioFile(Audio audio) {
        this.audio = ensureNotNull(audio, "audio");
    }

    public String fileName() {
        return "audio_file" + getAudioExtension(audio.mimeType());
    }

    public String mimeType() {
        return audio.mimeType();
    }

    public byte[] content() {
        if (audio.binaryData() != null) {
            return audio.binaryData();
        }

        if (audio.base64Data() != null) {
            try {
                return java.util.Base64.getDecoder().decode(audio.base64Data());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid base64 audio data provided", e);
            }
        }

        if (audio.url() != null) {
            throw new IllegalArgumentException(
                    "URL-based audio is not supported by OpenAI transcription. Please provide audio as binary data or base64 encoded data.");
        }

        throw new IllegalArgumentException("No audio data found. Audio must contain either binary data, base64 data");
    }

    public static AudioFile from(Audio audio) {
        return new AudioFile(audio);
    }

    private String getAudioExtension(String mimeType) {
        if (mimeType == null) return "";

        return switch (mimeType) {
            case "audio/flac" -> ".flac";
            case "audio/mpeg", "audio/mpeg3" -> ".mp3";
            case "audio/mp4", "video/mp4" -> ".mp4";
            case "audio/mpga" -> ".mpga";
            case "audio/m4a" -> ".m4a";
            case "audio/ogg" -> ".ogg";
            case "audio/x-wav", "audio/wave", "audio/wav" -> ".wav";
            case "audio/webm", "video/webm" -> ".webm";
            case "audio/x-mpegurl", "audio/mpegurl" -> ".m3u";
            default -> ""; // Unknown; return empty or throw
        };
    }
}
