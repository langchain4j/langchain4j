package com.example.observability;

import dev.langchain4j.internal.TestObservabilityListenersProviders;

/**
 * An {@code ObservabilityListenersProvider} of another library: it must be skipped.
 */
public class ProviderOutsideLangChain4j extends TestObservabilityListenersProviders.Recording {}
