package dev.langchain4j.model.openaiofficial;

import com.openai.errors.OpenAIServiceException;
import dev.langchain4j.internal.ExceptionMapper;

/**
 * Maps the exceptions of the OpenAI Java SDK to LangChain4j exceptions, based on their HTTP status code.
 */
class OpenAiOfficialExceptionMapper extends ExceptionMapper.DefaultExceptionMapper {

    static final OpenAiOfficialExceptionMapper INSTANCE = new OpenAiOfficialExceptionMapper();

    @Override
    public RuntimeException mapException(Throwable t) {
        if (t instanceof OpenAIServiceException serviceException) {
            return mapHttpStatusCode(serviceException, serviceException.statusCode());
        }
        return super.mapException(t);
    }
}
