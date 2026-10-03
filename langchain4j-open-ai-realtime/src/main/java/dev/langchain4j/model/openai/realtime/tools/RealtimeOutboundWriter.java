package dev.langchain4j.model.openai.realtime.tools;

/**
 * Sends outbound Realtime client events (JSON) to the OpenAI WebSocket.
 */
@FunctionalInterface
public interface RealtimeOutboundWriter {

    void send(String json);
}
