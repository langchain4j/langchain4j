package dev.langchain4j.model.typesafe;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.decision.response.DecisionResponseMetadata;
import java.util.Objects;

/**
 * The metadata of a {@link dev.langchain4j.model.decision.response.DecisionResponse} returned by
 * {@link TypeSafeDecisionModel}: in addition to the model name and the token usage, the {@link #requestId()} that
 * the server assigned to the request, and the {@link #rawHttpResponse()}.
 *
 * @since 1.23.0
 */
@Experimental
public class TypeSafeDecisionResponseMetadata extends DecisionResponseMetadata {

    private final String requestId;
    private final SuccessfulHttpResponse rawHttpResponse;

    private TypeSafeDecisionResponseMetadata(Builder builder) {
        super(builder);
        this.requestId = builder.requestId;
        this.rawHttpResponse = builder.rawHttpResponse;
    }

    /**
     * The ID that the server assigned to the request (the {@code x-typesafe-request-id} response header), which
     * identifies the request when contacting TypeSafe support. {@code null} if the server did not send it, for
     * example another server that implements the System One API.
     */
    public String requestId() {
        return requestId;
    }

    /**
     * The raw HTTP response, including its headers.
     */
    public SuccessfulHttpResponse rawHttpResponse() {
        return rawHttpResponse;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        TypeSafeDecisionResponseMetadata that = (TypeSafeDecisionResponseMetadata) o;
        return Objects.equals(requestId, that.requestId) && Objects.equals(rawHttpResponse, that.rawHttpResponse);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), requestId, rawHttpResponse);
    }

    @Override
    public String toString() {
        return "TypeSafeDecisionResponseMetadata{modelName=" + modelName() + ", tokenUsage=" + tokenUsage()
                + ", requestId=" + requestId + ", rawHttpResponse=" + rawHttpResponse + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DecisionResponseMetadata.Builder<Builder> {

        private String requestId;
        private SuccessfulHttpResponse rawHttpResponse;

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder rawHttpResponse(SuccessfulHttpResponse rawHttpResponse) {
            this.rawHttpResponse = rawHttpResponse;
            return this;
        }

        @Override
        public TypeSafeDecisionResponseMetadata build() {
            return new TypeSafeDecisionResponseMetadata(this);
        }
    }
}
