package dev.langchain4j.model.azure;

import com.azure.core.http.HttpResponse;
import dev.langchain4j.Internal;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.internal.ExceptionMapper;
import java.util.Map;

@Internal
class AzureOpenAiExceptionMapper extends ExceptionMapper.DefaultExceptionMapper {

    static final AzureOpenAiExceptionMapper INSTANCE = new AzureOpenAiExceptionMapper();

    @Override
    public RuntimeException mapException(Throwable t) {
        if (t instanceof com.azure.core.exception.HttpResponseException httpResponseException) {

            if (httpResponseException.getValue() instanceof Map<?,?> map && map.containsKey("error")) {
                if (map.get("error") instanceof Map<?,?> errorMap) {
                    if ("content_filter".equals(errorMap.get("code"))) {
                        return new ContentFilteredException(t);
                    }
                }
            }

            HttpResponse httpResponse = httpResponseException.getResponse();
            if (httpResponse != null) {
                return mapHttpStatusCode(httpResponseException, httpResponse.getStatusCode());
            }
        }

        if (t instanceof io.netty.channel.ConnectTimeoutException
                || t instanceof java.util.concurrent.TimeoutException) {
            return new TimeoutException(t);
        } else if (t.getCause() instanceof io.netty.channel.ConnectTimeoutException
                || t.getCause() instanceof java.util.concurrent.TimeoutException) {
            return new TimeoutException(t.getCause());
        }

        return t instanceof RuntimeException re ? re : new LangChain4jException(t);
    }
}
