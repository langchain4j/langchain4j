package dev.langchain4j.model.vertexai.gemini;

import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import dev.langchain4j.Internal;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.internal.ExceptionMapper;

@Internal
class VertexAiGeminiExceptionMapper extends ExceptionMapper.DefaultExceptionMapper {

    static final VertexAiGeminiExceptionMapper INSTANCE = new VertexAiGeminiExceptionMapper();

    private VertexAiGeminiExceptionMapper() {}

    @Override
    public RuntimeException mapException(Throwable t) {
        Throwable cause = t;
        while (cause != null) {
            if (cause instanceof ApiException apiException) {
                if (apiException.getStatusCode().getCode() == StatusCode.Code.DEADLINE_EXCEEDED) {
                    return new TimeoutException(apiException);
                }
                return mapHttpStatusCode(
                        apiException, apiException.getStatusCode().getCode().getHttpStatusCode());
            }
            cause = cause.getCause() == cause ? null : cause.getCause();
        }

        return t instanceof RuntimeException re ? re : new LangChain4jException(t);
    }
}
