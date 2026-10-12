package dev.langchain4j.spi.services;

import dev.langchain4j.Internal;
import dev.langchain4j.service.decision.DecisionServices;

/**
 * Creates the builders returned by {@link DecisionServices#builder(Class)}, so that frameworks can provide their own
 * implementation of decision services.
 */
@Internal
public interface DecisionServicesFactory {

    <T> DecisionServices.Builder<T> create(Class<T> serviceInterface);
}
