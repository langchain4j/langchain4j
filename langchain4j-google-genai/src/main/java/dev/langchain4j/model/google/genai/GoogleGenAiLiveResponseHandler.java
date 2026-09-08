package dev.langchain4j.model.google.genai;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;

/**
 * Receives events streamed back from a {@link GoogleGenAiLiveSession} over the
 * <a href="https://ai.google.dev/gemini-api/docs/live">Gemini Live API</a>.
 *
 * <p>Every method has a no-op default, so an implementation overrides only the events it cares about.
 * The methods are invoked from the SDK's callback threads and may run concurrently (for example, an error
 * from a failed send can arrive while a response is still streaming), so a handler that keeps mutable state
 * must guard it. Message events for a turn are delivered sequentially under an internal lock, so an
 * implementation must not block or acquire locks of its own that other threads may hold; hand long-running
 * work to your own executor.
 *
 * <p>A single server message can carry several of these events at once (for example audio plus a
 * transcript), so more than one method may be called for one message.
 */
@Experimental
public interface GoogleGenAiLiveResponseHandler {

    /** A chunk of streamed text for the current turn (response modality {@code TEXT}). */
    default void onPartialText(String text) {}

    /** The full text of a turn, concatenated from its {@link #onPartialText(String)} chunks. */
    default void onCompleteText(String text) {}

    /** A chunk of streamed output audio (24 kHz, 16-bit little-endian PCM; response modality {@code AUDIO}). */
    default void onAudio(byte[] audio) {}

    /** A transcript of the user's input audio (when input audio transcription is enabled). */
    default void onInputTranscription(String text) {}

    /** A transcript of the model's output audio (when output audio transcription is enabled). */
    default void onOutputTranscription(String text) {}

    /**
     * The model finished generating its response for the current turn. This fires before
     * {@link #onTurnComplete()} and is a useful cue to update the UI (for example, to stop a "speaking"
     * indicator) once no more output will arrive for the turn.
     */
    default void onGenerationComplete() {}

    /** The model finished its turn. */
    default void onTurnComplete() {}

    /**
     * The model's generation was interrupted (barge-in). Any queued output audio should be stopped and
     * discarded.
     */
    default void onInterrupted() {}

    /** Cumulative token usage for the session; sent periodically by the server. */
    default void onUsageMetadata(TokenUsage tokenUsage) {}

    /** The server will soon close the connection; {@code timeLeft} is how long remains, if provided. */
    default void onGoAway(Duration timeLeft) {}

    /** A session resumption handle that can be used to resume this session later. */
    default void onSessionResumptionUpdate(String handle) {}

    /** An error occurred while sending to or receiving from the session. */
    default void onError(Throwable error) {}

    /** The session was closed via {@link GoogleGenAiLiveSession#close()}. */
    default void onClose() {}
}
