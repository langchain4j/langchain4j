package dev.langchain4j.model.bedrock;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import dev.langchain4j.Internal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.utils.IoUtils;

@Internal
class AwsLoggingInterceptor implements ExecutionInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(AwsLoggingInterceptor.class);

    private final boolean logRequests;
    private final boolean logResponses;

    public AwsLoggingInterceptor(boolean logRequests, boolean logResponses) {
        this.logRequests = logRequests;
        this.logResponses = logResponses;
    }

    @Override
    public void beforeExecution(Context.BeforeExecution context, ExecutionAttributes executionAttributes) {
        if (logRequests)
            logger.debug("AWS SDK Operation: {}", context.request().getClass().getSimpleName());
    }

    @Override
    public void beforeTransmission(Context.BeforeTransmission context, ExecutionAttributes executionAttributes) {
        SdkHttpRequest request = context.httpRequest();
        String body = null;
        if (logRequests) {
            if (request.method() == SdkHttpMethod.POST && request instanceof SdkHttpFullRequest sdkHttpFullRequest) {
                try {
                    ContentStreamProvider csp = sdkHttpFullRequest.contentStreamProvider().orElse(null);
                    if (nonNull(csp)) body = IoUtils.toUtf8String(csp.newStream());
                } catch (IOException e) {
                    logger.warn("Unable to obtain request body", e);
                }
            }
            logger.debug(
                    "Request:\n- method: {}\n- url: {}\n- headers: {}\n- body: {}{}",
                    request.method(),
                    request.getUri(),
                    request.headers(),
                    body,
                    request.rawQueryParameters());
        }
    }

    @Override
    public software.amazon.awssdk.core.SdkResponse modifyResponse(
            Context.ModifyResponse context, ExecutionAttributes executionAttributes) {
        Optional.ofNullable(context.httpResponse()).ifPresent(response -> logResponse(response, context));
        return context.response();
    }

    private void logResponse(SdkHttpResponse response, Context.ModifyResponse context) {
        if (logResponses) {
            logger.debug(
                    "Response Status: {} \nHeaders: {} \nResponse Body Type: {}",
                    response.statusCode(),
                    response.headers(),
                    context.response().getClass().getSimpleName());
        }
    }

    @Override
    public Optional<InputStream> modifyHttpResponseContent(
            Context.ModifyHttpResponse context, ExecutionAttributes executionAttributes) {
        byte[] content = null;
        if (logResponses) {
            try {
                InputStream responseContentStream = context.responseBody().orElse(InputStream.nullInputStream());
                content = IoUtils.toByteArray(responseContentStream);
                logger.debug("Response Body: {}", new String(content, StandardCharsets.UTF_8));
            } catch (IOException e) {
                logger.warn("Unable to obtain response body", e);
            }
        }
        return isNull(content) ? Optional.empty() : Optional.of(new ByteArrayInputStream(content));
    }
}
