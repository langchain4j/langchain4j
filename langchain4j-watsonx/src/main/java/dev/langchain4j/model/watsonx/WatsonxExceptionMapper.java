package dev.langchain4j.model.watsonx;

import com.ibm.watsonx.ai.core.exeception.WatsonxException;
import com.ibm.watsonx.ai.core.exeception.model.WatsonxError;
import dev.langchain4j.Internal;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.internal.ExceptionMapper;
import java.net.http.HttpTimeoutException;

@Internal
class WatsonxExceptionMapper extends ExceptionMapper.DefaultExceptionMapper {

    static final WatsonxExceptionMapper INSTANCE = new WatsonxExceptionMapper();

    private WatsonxExceptionMapper() {}

    @Override
    public RuntimeException mapException(Throwable t) {
        if (t instanceof WatsonxException watsonxException) {

            if (watsonxException.details().isEmpty())
                return mapHttpStatusCode(watsonxException, watsonxException.statusCode());

            WatsonxError details = watsonxException.details().get();
            WatsonxError.Error error = details.errors().get(0);

            try {
                return switch (WatsonxError.Code.valueOf(error.code().toUpperCase())) {
                    case AUTHENTICATION_TOKEN_EXPIRED, AUTHORIZATION_REJECTED ->
                        new AuthenticationException(error.message(), watsonxException);
                    case INVALID_INPUT_ARGUMENT, INVALID_REQUEST_ENTITY, JSON_TYPE_ERROR, JSON_VALIDATION_ERROR ->
                        new InvalidRequestException(error.message(), watsonxException);
                    case MODEL_NOT_SUPPORTED -> new ModelNotFoundException(error.message(), watsonxException);
                    case TOKEN_QUOTA_REACHED -> new RateLimitException(error.message(), watsonxException);
                    default -> new LangChain4jException(error.message(), watsonxException);
                };
            } catch (IllegalArgumentException e) {
                return new LangChain4jException(error.message(), watsonxException);
            }

        } else if (t instanceof HttpTimeoutException || t instanceof java.util.concurrent.TimeoutException) {
            return new TimeoutException(t);
        }

        return t instanceof RuntimeException re ? re : new LangChain4jException(t);
    }
}
