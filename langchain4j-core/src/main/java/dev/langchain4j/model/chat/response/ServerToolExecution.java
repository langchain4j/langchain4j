package dev.langchain4j.model.chat.response;

import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import java.util.Objects;

/**
 * Represents a lifecycle event for a provider-hosted tool execution.
 * <p>
 * Providers can extend this class when they need to expose additional provider-specific fields.
 */
@Experimental
@JacocoIgnoreCoverageGenerated
public class ServerToolExecution {

    private final String id;
    private final String type;
    private final Object rawEvent;

    public ServerToolExecution(Builder builder) {
        this(builder.id, builder.type, builder.rawEvent);
    }

    protected ServerToolExecution(String id, String type, Object rawEvent) {
        this.id = id;
        this.type = ensureNotBlank(type, "type");
        this.rawEvent = rawEvent;
    }

    /**
     * The provider-generated identifier for the hosted tool execution, when available.
     */
    public String id() {
        return id;
    }

    /**
     * The provider event type, for example {@code response.web_search_call.searching}.
     */
    public String type() {
        return type;
    }

    /**
     * The raw provider event that produced this callback, when available.
     */
    public Object rawEvent() {
        return rawEvent;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        ServerToolExecution that = (ServerToolExecution) object;
        return Objects.equals(id, that.id)
                && Objects.equals(type, that.type)
                && Objects.equals(rawEvent, that.rawEvent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, rawEvent);
    }

    @Override
    public String toString() {
        return "ServerToolExecution{" + "id=" + quoted(id) + ", type=" + quoted(type) + ", rawEvent=" + rawEvent + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String id;
        private String type;
        private Object rawEvent;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder rawEvent(Object rawEvent) {
            this.rawEvent = rawEvent;
            return this;
        }

        public ServerToolExecution build() {
            return new ServerToolExecution(this);
        }
    }
}
