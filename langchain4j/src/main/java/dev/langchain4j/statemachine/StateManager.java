package dev.langchain4j.statemachine;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public class StateManager {
    private final StateMachineContext context;

    private final State initialState;
    private State currentState;

    private final BiFunction<State, StateMachineContext, State> stateManager;

    private Function<State, String> stateToSystemMessage;
    private Function<State, String> stateToUserMessage;

    public StateManager(StateMachineContext stateMachineContext, State initialState, BiFunction<State, StateMachineContext, State> stateManager) {
        this.context = stateMachineContext;

        this.initialState = initialState;
        this.currentState = initialState;

        this.stateManager = stateManager;
    }

    public void setStateToSystemMessage(Function<State, String> stateToSystemMessage) {
        this.stateToSystemMessage = stateToSystemMessage;
    }

    public void setStateToUserMessage(Function<State, String> stateToUserMessage) {
        this.stateToUserMessage = stateToUserMessage;
    }

    public Optional<String> getSystemMessage() {
        return stateToSystemMessage != null ?
                Optional.of(stateToSystemMessage.apply(currentState)) :
                Optional.empty();
    }

    public Optional<String> getUserMessage() {
        return stateToUserMessage != null ?
                Optional.of(stateToUserMessage.apply(currentState)) :
                Optional.empty();
    }

    public State nextState() {
        currentState = stateManager.apply(currentState, context);
        return currentState;
    }
}
