package dev.langchain4j.test.mock;

import java.util.ArrayList;
import java.util.List;

/**
 * The requests a mock received.
 */
final class RecordedRequests<T> {

    private final String kind;
    private final List<T> requests = new ArrayList<>();

    RecordedRequests(String kind) {
        this.kind = kind;
    }

    /**
     * @return the number of this request, counting from 1
     */
    synchronized int add(T request) {
        requests.add(request);
        return requests.size();
    }

    synchronized List<T> all() {
        return List.copyOf(requests);
    }

    synchronized T only() {
        if (requests.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly 1 " + kind + " request, but received " + requests.size());
        }
        return requests.get(0);
    }

    /**
     * The value for the request with the given number: the values are used in order, and the last one is repeated.
     *
     * @return {@code null} if there are no values
     */
    static <V> V forRequest(List<V> values, int requestNumber) {
        return values.isEmpty() ? null : values.get(Math.min(requestNumber, values.size()) - 1);
    }
}
