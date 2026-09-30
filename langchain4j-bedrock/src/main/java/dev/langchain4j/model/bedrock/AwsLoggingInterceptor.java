package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import dev.langchain4j.Internal;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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

    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(AwsLoggingInterceptor.class);

    /**
     * Authentication headers whose values must never be written to logs. Compared case-insensitively.
     */
    private static final Set<String> SENSITIVE_HEADERS = Set.of("authorization", "x-amz-security-token");

    private final boolean logRequests;
    private final boolean logResponses;
    private final Logger logger;

    AwsLoggingInterceptor(boolean logRequests, boolean logResponses, Logger logger) {
        this.logRequests = logRequests;
        this.logResponses = logResponses;
        this.logger = getOrDefault(logger, DEFAULT_LOGGER);
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
                    ContentStreamProvider csp =
                            sdkHttpFullRequest.contentStreamProvider().orElse(null);
                    if (nonNull(csp)) body = IoUtils.toUtf8String(csp.newStream());
                } catch (IOException e) {
                    logger.warn("Unable to obtain request body", e);
                }
            }
            logger.debug(
                    "Request:\n- method: {}\n- url: {}\n- headers: {}\n- query parameters: {}\n- body: {}",
                    request.method(),
                    request.getUri(),
                    maskHeaders(request.headers()),
                    request.rawQueryParameters(),
                    body);
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
                    maskHeaders(response.headers()),
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

    /**
     * Renders HTTP headers for logging, replacing the values of sensitive authentication headers
     * (e.g. {@code Authorization}, {@code X-Amz-Security-Token}) with a placeholder so that
     * credentials such as SigV4 signatures and temporary session tokens are not written to logs.
     * Header-name matching is case-insensitive; all other headers are rendered unchanged.
     */
    static String maskHeaders(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return "{}";
        }
        return headers.entrySet().stream()
                .map(entry -> {
                    if (SENSITIVE_HEADERS.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                        return entry.getKey() + "=[REDACTED]";
                    }
                    return entry.getKey() + "=" + entry.getValue();
                })
                .collect(Collectors.joining(", ", "{", "}"));
    }
}
