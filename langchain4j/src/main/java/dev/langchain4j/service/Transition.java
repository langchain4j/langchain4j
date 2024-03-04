package dev.langchain4j.service;

public interface Transition<StateObject> {

    boolean canTransit(StateObject stateObject);
}
