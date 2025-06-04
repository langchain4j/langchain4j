package dev.langchain4j.model.azure;

import com.azure.core.exception.HttpResponseException;
import com.azure.core.http.HttpResponse;
import dev.langchain4j.Internal;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.internal.ExceptionMapper;

@Internal
class AzureOpenAiExceptionMapper extends ExceptionMapper.DefaultExceptionMapper {

    static final AzureOpenAiExceptionMapper INSTANCE = new AzureOpenAiExceptionMapper();

    @Override
    public RuntimeException mapException(Throwable t) {
        if (t instanceof HttpResponseException httpResponseException) {
            HttpResponse httpResponse = httpResponseException.getResponse();
            if (httpResponse != null) {
                return mapHttpStatusCode(httpResponseException, httpResponse.getStatusCode());
            }
        }

        return t instanceof RuntimeException re ? re : new LangChain4jException(t);
    }
}
