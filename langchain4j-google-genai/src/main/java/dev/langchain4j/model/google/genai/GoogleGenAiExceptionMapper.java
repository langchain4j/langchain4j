package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

import com.google.genai.errors.ApiException;
import com.google.genai.gaos.models.errors.GaosApiException;
import com.google.genai.gaos.models.errors.GaosClientException;
import com.google.genai.gaos.models.errors.GaosServerException;
import dev.langchain4j.Internal;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.ContentFilteredException;
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
import java.util.Optional;
import java.util.Set;
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

    private static final Set<String> CONTENT_BLOCKED_CODES = Set.of(
            "safety",
            "prohibited_content",
            "blocklist",
            "content_blocked",
            "recitation",
            "spii",
            "image_safety",
            "image_prohibited_content");

    private GoogleGenAiExceptionMapper() {}

    @Override
    public RuntimeException mapException(Throwable t) {
        Throwable unwrapped = unwrapCompletionException(t);
        if (unwrapped instanceof GaosApiException gaosException
                && gaosException.code() >= 200
                && gaosException.code() < 300) {
            return map2xxGaosException(gaosException);
        } else if (unwrapped.getCause() instanceof GaosApiException gaosException
                && gaosException.code() >= 200
                && gaosException.code() < 300) {
            return map2xxGaosException(gaosException);
        }

        ApiException apiException = unwrapped instanceof ApiException ae
                ? ae
                : (unwrapped.getCause() instanceof ApiException ae ? ae : null);
        if (apiException != null) {
            Optional<String> body = extractBody(apiException);
            if (body.isPresent()) {
                return mapGaosHttpStatusCode(apiException, apiException.code(), body.get());
            }
            return mapHttpStatusCode(apiException, apiException.code());
        }
        if (isTimeout(unwrapped) || isTimeout(unwrapped.getCause())) {
            return new TimeoutException(unwrapped);
        }

        return super.mapException(unwrapped);
    }

    private static Optional<String> extractBody(ApiException exception) {
        if (exception instanceof GaosApiException gaosApi) {
            return gaosApi.bodyAsString();
        }
        if (exception instanceof GaosClientException gaosClient) {
            return gaosClient.bodyAsString();
        }
        if (exception instanceof GaosServerException gaosServer) {
            return gaosServer.bodyAsString();
        }
        return Optional.empty();
    }

    private static boolean isTimeout(@Nullable Throwable t) {
        return t instanceof SocketTimeoutException
                || (t instanceof InterruptedIOException && "timeout".equals(t.getMessage()));
    }

    protected RuntimeException map2xxGaosException(GaosApiException gaosException) {
        return new LangChain4jException(
                "The response of the Gemini API is invalid: " + gaosException.getMessage(), gaosException);
    }

    private static RuntimeException mapGaosHttpStatusCode(ApiException cause, int httpStatusCode, String body) {
        String message = extractErrorMessage(cause, body);
        Object codeField = errorField(body, "code");
        if (codeField instanceof String codeStr && CONTENT_BLOCKED_CODES.contains(codeStr.toLowerCase())) {
            return new ContentFilteredException(message, cause);
        }
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

    private static String extractErrorMessage(ApiException exception, String body) {
        if (isNullOrBlank(body)) {
            return exception.getMessage();
        }
        return errorField(body, "message") instanceof String message && !isNullOrBlank(message) ? message : body;
    }

    static @Nullable Object errorField(String body, String field) {
        try {
            Map<?, ?> root = Json.fromJson(body, Map.class);
            return root != null && root.get("error") instanceof Map<?, ?> error ? error.get(field) : null;
        } catch (RuntimeException notJson) {
            return null;
        }
    }
}
