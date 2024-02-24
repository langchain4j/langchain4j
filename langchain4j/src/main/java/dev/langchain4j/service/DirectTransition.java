package dev.langchain4j.service;

public class DirectTransition<StateObject> implements Transition<StateObject> {

    @Override
    public boolean canTransit(StateObject stateObject) {
        return true;
    }
}
