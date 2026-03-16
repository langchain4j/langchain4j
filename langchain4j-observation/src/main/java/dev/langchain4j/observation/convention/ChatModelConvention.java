package dev.langchain4j.observation.convention;

import dev.langchain4j.observation.context.ChatModelObservationContext;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;

public interface ChatModelConvention extends ObservationConvention<ChatModelObservationContext> {
    @Override
    default boolean supportsContext(Observation.Context context) {
        return context instanceof ChatModelObservationContext;
    }
}
