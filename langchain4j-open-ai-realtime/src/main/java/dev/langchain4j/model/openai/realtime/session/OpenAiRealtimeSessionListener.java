package dev.langchain4j.model.openai.realtime.session;

/**
 * Application callbacks for an {@link OpenAiRealtimeSession}.
 */
public interface OpenAiRealtimeSessionListener {

    void onEvent(String json);

    default void onClosed(int code, String reason) {}

    default void onFailure(Throwable t) {}
}
