package dev.langchain4j.code.judge0;

import static dev.langchain4j.code.judge0.JavaScriptCodeFixer.fixIfNoLogToConsole;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.time.Duration;
import java.util.Objects;

/**
 * A tool that executes JS code using the Judge0 service, hosted by Rapid API.
 */
public class Judge0JavaScriptExecutionTool {

    /** The language ID for JavaScript in Judge0's API */
    public static final int JAVASCRIPT = 102;

    /** Default timeout for Judge0 code execution (10 seconds) */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final Judge0JavaScriptEngine engine;
    private final boolean fixCodeIfNeeded;

    /**
     * Constructs a new instance with the provided Rapid API key.
     * The code fixing feature is enabled by default.
     * Default timeout is 10 seconds.
     *
     * @param apiKey The Rapid API key. You can subscribe to the free plan (Basic) here: https://rapidapi.com/judge0-official/api/judge0-ce/pricing
     * @throws IllegalArgumentException if the API key is null, empty, or blank
     */
    public Judge0JavaScriptExecutionTool(String apiKey) {
        this(apiKey, true, DEFAULT_TIMEOUT);
    }

    /**
     * Constructs a new instance with the provided Rapid API key and a timeout.
     * The code fixing feature is enabled by default.
     *
     * @param apiKey The Rapid API key
     * @param timeout Timeout for calling Judge0
     * @throws IllegalArgumentException if the API key is null, empty, or blank
     * @throws NullPointerException if timeout is null
     */
    public Judge0JavaScriptExecutionTool(String apiKey, Duration timeout) {
        this(apiKey, true, timeout);
    }

    /**
     * Constructs a new instance with the provided Rapid API key and code fixing flag.
     * Uses default timeout of 10 seconds.
     *
     * @param apiKey The Rapid API key
     * @param fixCodeIfNeeded Whether to automatically fix code if needed
     * @throws IllegalArgumentException if the API key is null, empty, or blank
     */
    public Judge0JavaScriptExecutionTool(String apiKey, boolean fixCodeIfNeeded) {
        this(apiKey, fixCodeIfNeeded, DEFAULT_TIMEOUT);
    }

    /**
     * Constructs a new instance with the provided Rapid API key, a flag to control whether to fix the code, and a timeout.
     *
     * @param apiKey          Rapid API key. You can subscribe to the free plan (Basic) here: https://rapidapi.com/judge0-official/api/judge0-ce/pricing
     * @param fixCodeIfNeeded Judge0 can return result of an execution if it was printed to the console.
     *                        If provided JS code does not print result to the console, attempt will be made to fix it.
     * @param timeout         Timeout for calling Judge0.
     * @throws IllegalArgumentException if the API key is null, empty, or blank
     * @throws NullPointerException if timeout is null
     */
    public Judge0JavaScriptExecutionTool(String apiKey, boolean fixCodeIfNeeded, Duration timeout) {
        if (isNullOrBlank(apiKey)) {
            throw new IllegalArgumentException("Please provide a valid Rapid API key");
        }
        Objects.requireNonNull(timeout, "Timeout must not be null");

        this.engine = new Judge0JavaScriptEngine(apiKey, JAVASCRIPT, timeout);
        this.fixCodeIfNeeded = fixCodeIfNeeded;
    }

    /**
     * Executes the provided JavaScript code using the Judge0 service.
     *
     * If code fixing is enabled (default), the method will attempt to add console.log statements
     * if the code doesn't already output results to the console.
     *
     * @param javaScriptCode JavaScript code to execute
     * @return The result of the JavaScript code execution
     * @throws IllegalArgumentException if javaScriptCode is null, empty or blank
     * @throws RuntimeException if code execution fails
     */
    @Tool("MUST be used for accurate calculations: math, sorting, filtering, aggregating, string processing, etc")
    public String executeJavaScriptCode(
            @P("JavaScript code to execute, result MUST be printed to console") String javaScriptCode) {
        if (isNullOrBlank(javaScriptCode)) {
            throw new IllegalArgumentException("JavaScript code must not be null or empty");
        }

        if (fixCodeIfNeeded) {
            javaScriptCode = fixIfNoLogToConsole(javaScriptCode);
        }

        try {
            return engine.execute(javaScriptCode);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute JavaScript code: " + e.getMessage(), e);
        }
    }

    /**
     * Returns whether code fixing is enabled for this tool.
     *
     * @return true if code fixing is enabled, false otherwise
     */
    public boolean isCodeFixingEnabled() {
        return fixCodeIfNeeded;
    }

    /**
     * Creates a builder for configuring a Judge0JavaScriptExecutionTool.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating a configured Judge0JavaScriptExecutionTool.
     */
    public static class Builder {
        private String apiKey;
        private boolean fixCodeIfNeeded = true;
        private Duration timeout = DEFAULT_TIMEOUT;

        /**
         * Sets the Rapid API key for Judge0.
         *
         * @param apiKey the API key
         * @return this builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets whether to fix code if needed.
         *
         * @param fixCodeIfNeeded whether to fix code
         * @return this builder
         */
        public Builder fixCodeIfNeeded(boolean fixCodeIfNeeded) {
            this.fixCodeIfNeeded = fixCodeIfNeeded;
            return this;
        }

        /**
         * Sets the timeout for code execution.
         *
         * @param timeout the timeout duration
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Builds a new Judge0JavaScriptExecutionTool with the configured properties.
         *
         * @return a new Judge0JavaScriptExecutionTool
         * @throws IllegalArgumentException if apiKey is null, empty or blank
         */
        public Judge0JavaScriptExecutionTool build() {
            return new Judge0JavaScriptExecutionTool(apiKey, fixCodeIfNeeded, timeout);
        }
    }
}
