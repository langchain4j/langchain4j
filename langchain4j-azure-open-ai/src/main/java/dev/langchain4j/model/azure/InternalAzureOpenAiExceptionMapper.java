package dev.langchain4j.model.azure;

import com.azure.core.exception.HttpResponseException;
import com.azure.core.http.HttpResponse;
import dev.langchain4j.Internal;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.internal.ExceptionMapper;
import io.netty.channel.ConnectTimeoutException;

@Internal
class InternalAzureOpenAiExceptionMapper extends ExceptionMapper.DefaultExceptionMapper {

    static final InternalAzureOpenAiExceptionMapper INSTANCE = new InternalAzureOpenAiExceptionMapper();

    @Override
    public RuntimeException mapException(Throwable t) {
        if (t instanceof HttpResponseException httpResponseException) {
            HttpResponse httpResponse = httpResponseException.getResponse();
            if (httpResponse != null) {
                return mapHttpStatusCode(httpResponseException, httpResponse.getStatusCode());
            }
        }

        if (t instanceof ConnectTimeoutException) {
            return new TimeoutException(t);
        } else if (t.getCause() instanceof ConnectTimeoutException) {
            return new TimeoutException(t.getCause());
        }

        return t instanceof RuntimeException re ? re : new LangChain4jException(t);
    }
}
