package dev.langchain4j.code.judge0;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.code.CodeExecutionEngine;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A JavaScript code execution engine that uses Judge0 API for code execution.
 * <p>
 * This class connects to the Judge0 API via RapidAPI to execute JavaScript code.
 */
class Judge0JavaScriptEngine implements CodeExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(Judge0JavaScriptEngine.class);

    // HTTP Constants
    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json");
    private static final String RAPID_API_HOST = "judge0-ce.p.rapidapi.com";
    private static final String API_URL =
            "https://judge0-ce.p.rapidapi.com/submissions?base64_encoded=true&wait=true&fields=*";

    // Judge0 status codes
    private static final int STATUS_ACCEPTED = 3;

    // Error messages
    private static final String ERROR_RATE_LIMIT = "Rate limit exceeded. Please try again later.";
    private static final String ERROR_FORBIDDEN = "Access forbidden. Please check your API key.";
    private static final String ERROR_NOT_FOUND = "Resource not found. Please check the endpoint URL.";
    private static final String ERROR_SERVER = "Internal server error. Please try again later.";
    private static final String ERROR_UNAVAILABLE = "Service unavailable. Please try again later.";
    private static final String ERROR_NULL_RESPONSE = "Response body is null";
    private static final String ERROR_NO_STDOUT = "No result: nothing was printed out to the console";

    // Retry settings
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final String apiKey;
    private final int languageId;
    private final OkHttpClient client;

    /**
     * Creates a new Judge0JavaScriptEngine with the specified API key, language ID, and timeout.
     *
     * @param apiKey The RapidAPI key for Judge0
     * @param languageId The language ID for the Judge0 API (e.g., 102 for JavaScript)
     * @param timeout The timeout duration for HTTP requests
     * @throws IllegalArgumentException if apiKey is null or blank
     * @throws NullPointerException if timeout is null
     */
    Judge0JavaScriptEngine(String apiKey, int languageId, Duration timeout) {
        if (isNullOrBlank(apiKey)) {
            throw new IllegalArgumentException("API key must not be null or blank");
        }
        Objects.requireNonNull(timeout, "Timeout must not be null");

        this.apiKey = apiKey;
        this.languageId = languageId;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .callTimeout(timeout)
                .build();
    }

    /**
     * Executes the provided code using the Judge0 API.
     *
     * @param code The code to execute
     * @return The result of code execution or an error message
     */
    @Override
    public String execute(String code) {
        if (isNullOrBlank(code)) {
            return "Error: Cannot execute empty or null code";
        }

        String base64EncodedCode = Base64.getEncoder().encodeToString(code.getBytes(StandardCharsets.UTF_8));
        Submission submission = new Submission(languageId, base64EncodedCode);
        RequestBody requestBody = RequestBody.create(Json.toJson(submission), MEDIA_TYPE_JSON);
        Request request = buildRequest(requestBody);

        return executeWithRetry(request);
    }

    /**
     * Builds a request to the Judge0 API.
     *
     * @param requestBody The request body
     * @return The built request
     */
    private Request buildRequest(RequestBody requestBody) {
        return new Request.Builder()
                .url(API_URL)
                .addHeader("x-rapidapi-host", RAPID_API_HOST)
                .addHeader("x-rapidapi-key", apiKey)
                .post(requestBody)
                .build();
    }

    /**
     * Executes a request with retry logic.
     *
     * @param request The request to execute
     * @return The result of the request or an error message
     */
    private String executeWithRetry(Request request) {
        Exception lastException = null;

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                try {
                    log.info("Retrying request, attempt {} of {}", attempt + 1, MAX_RETRIES);
                    TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "Request interrupted: " + e.getMessage();
                }
            }

            try {
                return processRequest(request);
            } catch (IOException e) {
                lastException = e;
                log.warn("Request failed (attempt {}/{}): {}", attempt + 1, MAX_RETRIES, e.getMessage());
            }
        }

        log.error("All retry attempts failed", lastException);
        return "Failed after " + MAX_RETRIES + " attempts: " + lastException.getMessage();
    }

    /**
     * Processes a single request to the Judge0 API.
     *
     * @param request The request to process
     * @return The result of the request or an error message
     * @throws IOException if an I/O error occurs
     */
    private String processRequest(Request request) throws IOException {
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return handleErrorResponse(response);
            }

            if (response.body() == null) {
                log.warn(ERROR_NULL_RESPONSE);
                return ERROR_NULL_RESPONSE;
            }

            String responseBody = response.body().string();
            return processResponseBody(responseBody);
        }
    }

    /**
     * Handles error responses from the Judge0 API.
     *
     * @param response The error response
     * @return An appropriate error message
     */
    private String handleErrorResponse(Response response) {
        String errorMessage =
                switch (response.code()) {
                    case 429 -> ERROR_RATE_LIMIT;
                    case 403 -> ERROR_FORBIDDEN;
                    case 404 -> ERROR_NOT_FOUND;
                    case 500 -> ERROR_SERVER;
                    case 503 -> ERROR_UNAVAILABLE;
                    default -> "Unexpected error code " + response.code() + ": " + response.message();
                };

        log.warn(errorMessage);
        return errorMessage;
    }

    /**
     * Processes the response body from a successful request.
     *
     * @param responseBody The response body as a string
     * @return The processed result or an error message
     */
    private String processResponseBody(String responseBody) {
        SubmissionResult result = Json.fromJson(responseBody, SubmissionResult.class);

        if (result.status.id != STATUS_ACCEPTED) {
            return formatErrorResult(result);
        }

        String base64EncodedStdout = result.stdout;
        if (base64EncodedStdout == null) {
            return ERROR_NO_STDOUT;
        }

        try {
            return new String(Base64.getMimeDecoder().decode(base64EncodedStdout.trim()), StandardCharsets.UTF_8)
                    .trim();
        } catch (IllegalArgumentException e) {
            log.warn("Failed to decode base64 output", e);
            return "Error decoding result: " + e.getMessage();
        }
    }

    /**
     * Formats an error result from Judge0.
     *
     * @param result The submission result containing error information
     * @return A formatted error message
     */
    private String formatErrorResult(SubmissionResult result) {
        StringBuilder error = new StringBuilder(result.status.description);

        if (!isNullOrBlank(result.compile_output)) {
            error.append("\n");
            try {
                error.append(new String(Base64.getMimeDecoder().decode(result.compile_output), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                error.append("(Error decoding compile output: ")
                        .append(e.getMessage())
                        .append(")");
            }
        }

        return error.toString();
    }

    /**
     * Represents a code submission to the Judge0 API.
     */
    private static class Submission {
        private final int language_id;
        private final String source_code;

        Submission(int languageId, String sourceCode) {
            this.language_id = languageId;
            this.source_code = sourceCode;
        }

        public int getLanguage_id() {
            return language_id;
        }

        public String getSource_code() {
            return source_code;
        }
    }

    /**
     * Represents the result of a code submission.
     */
    private static class SubmissionResult {
        private String stdout;
        private Status status;
        private String compile_output;

        public String getStdout() {
            return stdout;
        }

        public void setStdout(String stdout) {
            this.stdout = stdout;
        }

        public Status getStatus() {
            return status;
        }

        public void setStatus(Status status) {
            this.status = status;
        }

        public String getCompile_output() {
            return compile_output;
        }

        public void setCompile_output(String compile_output) {
            this.compile_output = compile_output;
        }
    }

    /**
     * Represents the status of a code submission.
     */
    private static class Status {
        private int id;
        private String description;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
