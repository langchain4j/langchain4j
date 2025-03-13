package dev.langchain4j.http.client.sse;

import dev.langchain4j.Experimental;

import java.util.Objects;

@Experimental
public class ServerSentEvent {

    private final String event;
    private final String data;

    public ServerSentEvent(String event, String data) {
        this.event = event;
        this.data = data;
    }

    public String event() {
        return event;
    }

    public String data() {
        return data;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ServerSentEvent) obj;
        return Objects.equals(this.event, that.event) &&
                Objects.equals(this.data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(event, data);
    }

    @Override
    public String toString() {
        return "ServerSentEvent[" +
                "event=" + event + ", " +
                "data=" + data + ']';
    }
}
