package dev.langchain4j.model.batch;

import java.util.List;
import java.util.Objects;

/**
 * Represents a batch of requests to be processed together.
 *
 * @param <T> The type of the requests in this batch.
 */
public class BatchRequest<T> {
    private final List<T> requests;

    /**
     * Creates a new BatchRequest.
     *
     * @param requests The list of requests. Must not be null.
     */
    public BatchRequest(List<T> requests) {
        this.requests = Objects.requireNonNull(requests);
    }

    /**
     * Returns the list of requests in this batch.
     *
     * @return The requests.
     */
    public List<T> requests() {
        return requests;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof BatchRequest<?> that && Objects.equals(this.requests, that.requests));
    }

    @Override
    public int hashCode() {
        return Objects.hash(requests);
    }

    @Override
    public String toString() {
        return String.format("BatchRequest{requests=%s}", requests);
    }
}
