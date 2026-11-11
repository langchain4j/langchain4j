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
            String hint = requests.isEmpty() ? "" : ". To check each of them, use requests(), for example requests().get(0)";
            throw new IllegalStateException(
                    "Expected exactly 1 " + kind + " request, but received " + requests.size() + hint);
        }
        return requests.get(0);
    }
}
