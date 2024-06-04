package dev.langchain4j.exception;

import static java.lang.String.format;

public class IllegalConfigurationException extends RuntimeException {

    public IllegalConfigurationException(String message) {
        super(message);
    }

    public IllegalConfigurationException(String message, Exception cause) {
        super(message, cause);
    }

    public static IllegalConfigurationException illegalConfiguration(String message) {
        return new IllegalConfigurationException(message);
    }

    public static IllegalConfigurationException illegalConfiguration(String message, Exception cause) {
        return new IllegalConfigurationException(message, cause);
    }

    public static IllegalConfigurationException illegalConfiguration(String format, Object... args) {
        return new IllegalConfigurationException(format(format, args));
    }

    public static IllegalConfigurationException illegalConfiguration(String format, Exception cause, Object... args) {
        return new IllegalConfigurationException(format(format, args), cause);
    }
}
