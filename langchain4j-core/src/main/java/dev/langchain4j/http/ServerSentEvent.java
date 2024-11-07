package dev.langchain4j.http;

import dev.langchain4j.Experimental;

@Experimental
public class ServerSentEvent {
    private final String type;
    private final String data;

    ServerSentEvent(final String type, final String data) {
        this.type = type;
        this.data = data;
    }


    public static class ServerSentEventBuilder {
        private String type;
        private String data;

        ServerSentEventBuilder() {
        }

        /**
         * @return {@code this}.
         */
        public ServerSentEvent.ServerSentEventBuilder type(final String type) {
            this.type = type;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public ServerSentEvent.ServerSentEventBuilder data(final String data) {
            this.data = data;
            return this;
        }

        public ServerSentEvent build() {
            return new ServerSentEvent(this.type, this.data);
        }

        @java.lang.Override
        public java.lang.String toString() {
            return "ServerSentEvent.ServerSentEventBuilder(type=" + this.type + ", data=" + this.data + ")";
        }
    }

    public static ServerSentEvent.ServerSentEventBuilder builder() {
        return new ServerSentEvent.ServerSentEventBuilder();
    }

    public String getType() {
        return this.type;
    }

    public String getData() {
        return this.data;
    }
}
