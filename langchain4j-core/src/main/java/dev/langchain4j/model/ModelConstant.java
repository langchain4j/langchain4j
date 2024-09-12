package dev.langchain4j.model;

import java.time.Duration;

/**
 * Represents a model constant for default values in the interface.
 */
public interface ModelConstant {

    double DEFAULT_TEMPERATURE = 0.7d;
    int DEFAULT_MAX_TOKENS = 512;

    /*
     * Client default settings.
     */
    Duration DEFAULT_CLIENT_TIMEOUT = Duration.ofMinutes(1);
    int DEFAULT_CLIENT_RETRIES = 3;
}
