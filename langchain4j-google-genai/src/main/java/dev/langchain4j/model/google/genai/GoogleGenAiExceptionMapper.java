package dev.langchain4j.model.google.genai;

import com.google.genai.errors.ApiException;
import dev.langchain4j.Internal;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.internal.ExceptionMapper;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;

@Internal
class GoogleGenAiExceptionMapper extends ExceptionMapper.DefaultExceptionMapper {

    static final GoogleGenAiExceptionMapper INSTANCE = new GoogleGenAiExceptionMapper();

    private GoogleGenAiExceptionMapper() {}

    @Override
    public RuntimeException mapException(Throwable t) {
        if (t instanceof ApiException apiException) {
            return mapHttpStatusCode(apiException, apiException.code());
        } else if (t.getCause() instanceof ApiException apiException) {
            return mapHttpStatusCode(apiException, apiException.code());
        } else if (isTimeout(t)) {
            return new TimeoutException(t);
        }

        return t instanceof RuntimeException re ? re : new LangChain4jException(t);
    }

    private static boolean isTimeout(Throwable t) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException
                    || (cause instanceof InterruptedIOException && "timeout".equals(cause.getMessage()))) {
                return true;
            }
        }
        return false;
    }
}
