package dev.langchain4j.model.openai.realtime.session;

/**
 * Callbacks from {@link RealtimeTransport}.
 */
public interface RealtimeTransportListener {

    void onOpen();

    void onTextMessage(String text);

    void onClosed(int code, String reason);

    void onFailure(Throwable t);
}
