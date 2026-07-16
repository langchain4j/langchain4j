package dev.langchain4j.model.google.genai;

import com.google.genai.errors.ApiException;
import dev.langchain4j.Internal;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.internal.ExceptionMapper;

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
        }

        return t instanceof RuntimeException re ? re : new LangChain4jException(t);
    }
}
