package dev.langchain4j.service;

import java.util.function.Predicate;

public class ConditionalTransition<StateObject> implements Transition<StateObject> {

    private final Predicate<StateObject> predicate;

    public ConditionalTransition(Predicate<StateObject> predicate) {
        this.predicate = predicate;
    }

    @Override
    public boolean canTransit(StateObject stateObject) {
        return predicate.test(stateObject);
    }
}
