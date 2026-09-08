package dev.langchain4j.model.google.genai;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ChatMessage;
import java.util.List;

/**
 * A live, bidirectional session with the <a href="https://ai.google.dev/gemini-api/docs/live">Gemini
 * Live API</a>, obtained from {@link GoogleGenAiLiveModel#connect(GoogleGenAiLiveResponseHandler)}.
 *
 * <p>Input is sent through this interface; output is delivered to the {@link GoogleGenAiLiveResponseHandler}
 * supplied when the session was opened. The session is single, long-lived and stateful; close it when done
 * (it is {@link AutoCloseable}). The {@code send*} methods throw {@link IllegalStateException} once the
 * session is closed.
 */
@Experimental
public interface GoogleGenAiLiveSession extends AutoCloseable {

    /**
     * Sends a text message as realtime input. This is the low-latency path and works across model
     * versions; the model responds based on voice activity detection.
     */
    void sendText(String text);

    /**
     * Sends ordered conversation content. Used to seed context or send turn-by-turn messages.
     *
     * @param messages     the messages that make up the turn(s)
     * @param turnComplete whether this completes the current turn (prompting the model to respond)
     */
    void sendClientContent(List<ChatMessage> messages, boolean turnComplete);

    /**
     * Sends a chunk of realtime input audio. Audio must be raw 16-bit little-endian PCM; send it in short
     * chunks (about 20-40 ms) for responsive voice activity detection.
     *
     * @param audioData raw PCM audio bytes
     * @param mimeType  the audio MIME type, e.g. {@code "audio/pcm;rate=16000"}
     */
    void sendAudio(byte[] audioData, String mimeType);

    /**
     * Signals the end of the realtime audio stream so the server flushes any buffered audio. Call this when
     * the audio input pauses (using automatic voice activity detection).
     */
    void sendAudioStreamEnd();

    /** Returns {@code true} while the session is open. */
    boolean isOpen();

    /** Returns the server-assigned session id, or {@code null} if the server did not assign one. */
    String sessionId();

    @Override
    void close();
}
