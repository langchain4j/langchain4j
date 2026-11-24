package dev.langchain4j.service;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationEnd;
import dev.langchain4j.observability.api.AiServiceListenerRegistrar;
import dev.langchain4j.observability.api.event.AiServiceCancelledEvent;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;

/**
 * Fires the event that ends an AI Service invocation ({@link AiServiceCompletedEvent}, {@link AiServiceErrorEvent} or
 * {@link AiServiceCancelledEvent}), but only if no such event was fired for the invocation yet
 * (see {@link InvocationEnd}).
 */
final class TerminalEvents {

    private TerminalEvents() {}

    static boolean fireCompleted(
            AiServiceListenerRegistrar registrar, InvocationContext invocationContext, Object result) {
        if (!InvocationEnd.markEnded(invocationContext)) {
            return false;
        }
        registrar.fireEvent(AiServiceCompletedEvent.builder()
                .invocationContext(invocationContext)
                .result(result)
                .build());
        return true;
    }

    static boolean fireError(
            AiServiceListenerRegistrar registrar, InvocationContext invocationContext, Throwable error) {
        if (!InvocationEnd.markEnded(invocationContext)) {
            return false;
        }
        registrar.fireEvent(AiServiceErrorEvent.builder()
                .invocationContext(invocationContext)
                .error(error)
                .build());
        return true;
    }

    static boolean fireCancelled(AiServiceListenerRegistrar registrar, InvocationContext invocationContext) {
        if (!InvocationEnd.markEnded(invocationContext)) {
            return false;
        }
        registrar.fireEvent(AiServiceCancelledEvent.builder()
                .invocationContext(invocationContext)
                .build());
        return true;
    }
}
