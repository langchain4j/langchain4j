package dev.langchain4j.model.googleai;

import java.util.function.Consumer;

public interface LiveSession extends AutoCloseable {

    /**
     * Send audio data to the model via streaming (realtimeInput)
     * @param audioData Raw PCM audio data (16-bit, little-endian, 16kHz)
     */
    void sendAudio(byte[] audioData);

    /**
     * Send complete audio as a turn (clientContent) - use for short audio clips
     * @param audioData Raw PCM audio data (16-bit, little-endian, 16kHz)
     */
    void sendAudioAsTurn(byte[] audioData);

    /**
     * Signal end of audio stream to flush cached audio.
     * Call this after sending audio data when the user has finished speaking.
     */
    void sendAudioStreamEnd();

    /**
     * Signal that the user's turn is complete, prompting the model to respond.
     * Call this after sending audio/text to explicitly trigger a model response.
     */
    void sendTurnComplete();

    /**
     * Send text message to the model
     * @param text The text message
     */
    void sendText(String text);

    /**
     * Send video frame to the model
     * @param videoData Raw video frame data
     */
    void sendVideo(byte[] videoData);

    /**
     * Register callback for audio responses
     * @param handler Consumer that receives audio bytes
     */
    void onAudioResponse(Consumer<byte[]> handler);

    /**
     * Register callback for text responses
     * @param handler Consumer that receives text
     */
    void onTextResponse(Consumer<String> handler);

    /**
     * Register callback for errors
     * @param handler Consumer that receives errors
     */
    void onError(Consumer<Throwable> handler);

    /**
     * Register callback for turn completion (model finished responding)
     * @param handler Runnable called when turn is complete
     */
    void onTurnComplete(Runnable handler);

    /**
     * Check if connection is active
     * @return true if connected
     */
    boolean isConnected();

    @Override
    void close();
}
