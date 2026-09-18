package dev.langchain4j.model.vertexai.anthropic;

import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import dev.langchain4j.Internal;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.internal.ExceptionMapper;

@Internal
class VertexAiAnthropicExceptionMapper extends ExceptionMapper.DefaultExceptionMapper {

    static final VertexAiAnthropicExceptionMapper INSTANCE = new VertexAiAnthropicExceptionMapper();

    private VertexAiAnthropicExceptionMapper() {}

    @Override
    public RuntimeException mapException(Throwable t) {
        // The client wraps gax exceptions into IOException, so the whole cause chain has to be searched
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
