package dev.langchain4j.model.watsonx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.ibm.watsonx.ai.core.exeception.WatsonxException;
import com.ibm.watsonx.ai.core.exeception.model.WatsonxError;
import com.ibm.watsonx.ai.core.exeception.model.WatsonxError.Code;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import org.junit.jupiter.api.Test;

public class WatsonxExceptionMapperTest {

    private static final WatsonxExceptionMapper mapper = WatsonxExceptionMapper.INSTANCE;

    @Test
    void testOtherExceptions() {

        var runtimeException = new RuntimeException("test");
        assertEquals(runtimeException, mapper.mapException(runtimeException));

        var ex = mapper.mapException(new Exception("test"));
        assertInstanceOf(LangChain4jException.class, ex);

        ex = mapper.mapException(new HttpTimeoutException("test"));
        assertInstanceOf(TimeoutException.class, ex);

        ex = mapper.mapException(new java.util.concurrent.TimeoutException("test"));
        assertInstanceOf(TimeoutException.class, ex);
    }

    @Test
    void textWatsonxException() {
        var details = new WatsonxError(
                404,
                "96d1304b909f98de10f1199e92d9b873",
                List.of(new WatsonxError.Error(
                        "path_not_found_error",
                        "URI path '/ml/v1/text/chats' does not exist",
                        "https://cloud.ibm.com/apidocs/watsonx-ai")));
        var ex = mapper.mapException(new WatsonxException("path_not_found_error", 404, details));
        var langChain4jException = assertInstanceOf(LangChain4jException.class, ex);
        assertInstanceOf(WatsonxException.class, langChain4jException.getCause());
        assertEquals("URI path '/ml/v1/text/chats' does not exist", langChain4jException.getMessage());

        details = createWatsonxError(Code.AUTHENTICATION_TOKEN_EXPIRED, 401, "token expired");
        ex = mapper.mapException(new WatsonxException(Code.AUTHENTICATION_TOKEN_EXPIRED.value(), 401, details));
        langChain4jException = assertInstanceOf(AuthenticationException.class, ex);
        assertEquals("token expired", langChain4jException.getMessage());

        details = createWatsonxError(Code.INVALID_INPUT_ARGUMENT, 500, "invalid input argument");
        ex = mapper.mapException(new WatsonxException(Code.INVALID_INPUT_ARGUMENT.value(), 500, details));
        langChain4jException = assertInstanceOf(InvalidRequestException.class, ex);
        assertEquals("invalid input argument", langChain4jException.getMessage());

        details = createWatsonxError(Code.MODEL_NOT_SUPPORTED, 404, "model not supported");
        ex = mapper.mapException(new WatsonxException(Code.MODEL_NOT_SUPPORTED.value(), 404, details));
        langChain4jException = assertInstanceOf(ModelNotFoundException.class, ex);
        assertEquals("model not supported", langChain4jException.getMessage());

        details = createWatsonxError(Code.TOKEN_QUOTA_REACHED, 403, "token quota reached");
        ex = mapper.mapException(new WatsonxException(Code.MODEL_NOT_SUPPORTED.value(), 403, details));
        langChain4jException = assertInstanceOf(RateLimitException.class, ex);
        assertEquals("token quota reached", langChain4jException.getMessage());

        details = createWatsonxError(Code.USER_AUTHORIZATION_FAILED, 403, "user authorization failed");
        ex = mapper.mapException(new WatsonxException(Code.MODEL_NOT_SUPPORTED.value(), 403, details));
        langChain4jException = assertInstanceOf(LangChain4jException.class, ex);
        assertEquals("user authorization failed", langChain4jException.getMessage());

        ex = mapper.mapException(new WatsonxException(500));
        assertInstanceOf(InternalServerException.class, ex);
    }

    private WatsonxError createWatsonxError(WatsonxError.Code code, int statusCode, String message) {
        return new WatsonxError(
                statusCode,
                "96d1304b909f98de10f1199e92d9b873",
                List.of(new WatsonxError.Error(code.value(), message, "https://cloud.ibm.com/apidocs/watsonx-ai")));
    }
}
