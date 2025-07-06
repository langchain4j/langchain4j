package dev.langchain4j.model.openaiofficial;

import com.openai.errors.OpenAIIoException;
import dev.langchain4j.Internal;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.internal.ExceptionMapper;
import java.io.InterruptedIOException;

@Internal
class OpenAiOfficialExceptionMapper extends ExceptionMapper.DefaultExceptionMapper {

    static final OpenAiOfficialExceptionMapper INSTANCE = new OpenAiOfficialExceptionMapper();

    @Override
    public RuntimeException mapException(Throwable t) {
        if (t instanceof com.openai.errors.OpenAIServiceException httpResponseException) {
            final var statusCode = httpResponseException.statusCode();
            return mapHttpStatusCode(new HttpException(statusCode, httpResponseException), statusCode);
        } else if (t instanceof OpenAIIoException ioException) {
            if (t.getCause() instanceof InterruptedIOException) {
                return new TimeoutException("Request timed out", t);
            }
        }

        return new LangChain4jException(t);
    }
}
