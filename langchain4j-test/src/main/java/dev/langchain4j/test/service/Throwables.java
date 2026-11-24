package dev.langchain4j.test.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

final class Throwables {

    private Throwables() {}

    /**
     * Returns the given error followed by its causes, each one once, also when the causes form a loop.
     */
    static List<Throwable> causalChain(Throwable error) {
        List<Throwable> chain = new ArrayList<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = error; cause != null && visited.add(cause); cause = cause.getCause()) {
            chain.add(cause);
        }
        return chain;
    }
}
