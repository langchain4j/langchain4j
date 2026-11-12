package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

import com.google.genai.errors.ApiException;
import com.google.genai.gaos.models.errors.GaosApiException;
import dev.langchain4j.Internal;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidDecisionResponseException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.internal.ExceptionMapper;
import dev.langchain4j.internal.Json;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@Internal
class GoogleGenAiExceptionMapper extends ExceptionMapper.DefaultExceptionMapper {

    static final GoogleGenAiExceptionMapper INSTANCE = new GoogleGenAiExceptionMapper();
    static final GoogleGenAiExceptionMapper DECISION_INSTANCE = new GoogleGenAiExceptionMapper() {
        @Override
        protected RuntimeException map2xxGaosException(GaosApiException gaosException) {
            return new InvalidDecisionResponseException(
                    "The response of the Gemini API is invalid: " + gaosException.getMessage(), gaosException);
        }
    };

    private GoogleGenAiExceptionMapper() {}

    @Override
    public RuntimeException mapException(Throwable t) {
        Throwable unwrapped = unwrapCompletionException(t);
        GaosApiException gaosException = unwrapped instanceof GaosApiException ge
                ? ge
                : (unwrapped.getCause() instanceof GaosApiException ge ? ge : null);
        if (gaosException != null) {
            if (gaosException.code() >= 200 && gaosException.code() < 300) {
                return map2xxGaosException(gaosException);
            }
            return mapGaosHttpStatusCode(gaosException);
        }
        if (unwrapped instanceof ApiException apiException) {
            return mapHttpStatusCode(apiException, apiException.code());
        } else if (unwrapped.getCause() instanceof ApiException apiException) {
            return mapHttpStatusCode(apiException, apiException.code());
        }
        if (isTimeout(unwrapped) || isTimeout(unwrapped.getCause())) {
            return new TimeoutException(unwrapped);
        }

        return super.mapException(unwrapped);
    }

    private static boolean isTimeout(@Nullable Throwable t) {
        // OkHttp reports an expired call timeout (HttpOptions.timeout) as InterruptedIOException("timeout")
        return t instanceof SocketTimeoutException
                || (t instanceof InterruptedIOException && "timeout".equals(t.getMessage()));
    }

    protected RuntimeException map2xxGaosException(GaosApiException gaosException) {
        return new LangChain4jException(
                "The response of the Gemini API is invalid: " + gaosException.getMessage(), gaosException);
    }

    private static RuntimeException mapGaosHttpStatusCode(GaosApiException cause) {
        int httpStatusCode = cause.code();
        String message = extractErrorMessage(cause);
        if (httpStatusCode >= 500 && httpStatusCode < 600) {
            return new InternalServerException(message, cause);
        }
        if (httpStatusCode == 401 || httpStatusCode == 403) {
            return new AuthenticationException(message, cause);
        }
        if (httpStatusCode == 404) {
            return new ModelNotFoundException(message, cause);
        }
        if (httpStatusCode == 408) {
            return new TimeoutException(message, cause);
        }
        if (httpStatusCode == 429) {
            return new RateLimitException(message, cause);
        }
        if (httpStatusCode >= 400 && httpStatusCode < 500) {
            return new InvalidRequestException(message, cause);
        }
        return new LangChain4jException(message, cause);
    }

    private static String extractErrorMessage(GaosApiException exception) {
        String body = exception.bodyAsString().orElse(null);
        if (isNullOrBlank(body)) {
            return exception.getMessage();
        }
        return errorField(body, "message") instanceof String message && !isNullOrBlank(message) ? message : body;
    }

    /**
     * A field of the {@code error} object of a Gemini error body, such as {@code {"error": {"message": "..."}}}, or
     * {@code null} when the body has no such field.
     */
    static @Nullable Object errorField(String body, String field) {
        try {
            Map<?, ?> root = Json.fromJson(body, Map.class);
            return root != null && root.get("error") instanceof Map<?, ?> error ? error.get(field) : null;
        } catch (RuntimeException notJson) {
            return null;
        }
    }
}
