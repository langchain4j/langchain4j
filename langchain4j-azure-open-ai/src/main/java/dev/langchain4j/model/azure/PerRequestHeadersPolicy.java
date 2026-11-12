package dev.langchain4j.model.azure;

import static com.azure.core.http.policy.AddHeadersFromContextPolicy.AZURE_REQUEST_HTTP_HEADERS_KEY;

import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpPipelineCallContext;
import com.azure.core.http.HttpPipelineNextPolicy;
import com.azure.core.http.HttpPipelineNextSyncPolicy;
import com.azure.core.http.HttpResponse;
import com.azure.core.http.policy.HttpPipelinePolicy;
import com.azure.core.util.Context;
import reactor.core.publisher.Mono;

/**
 * Applies the per-request custom HTTP headers that are passed in the {@link Context} of a call,
 * after all other headers, so that they win over headers with the same name.
 * The exception is {@code User-Agent}: the per-request value is appended to the value already on the request,
 * separated by a space, as a {@code User-Agent} is a list of product tokens.
 */
final class PerRequestHeadersPolicy implements HttpPipelinePolicy {

    @Override
    public Mono<HttpResponse> process(HttpPipelineCallContext context, HttpPipelineNextPolicy next) {
        applyPerRequestHeaders(context);
        return next.process();
    }

    @Override
    public HttpResponse processSync(HttpPipelineCallContext context, HttpPipelineNextSyncPolicy next) {
        applyPerRequestHeaders(context);
        return next.processSync();
    }

    private static void applyPerRequestHeaders(HttpPipelineCallContext context) {
        context.getData(AZURE_REQUEST_HTTP_HEADERS_KEY).ifPresent(data -> {
            if (data instanceof HttpHeaders perRequestHeaders) {
                HttpHeaders headers = context.getHttpRequest().getHeaders();
                String currentUserAgent = headers.getValue(HttpHeaderName.USER_AGENT);
                headers.setAllHttpHeaders(perRequestHeaders);
                String requestUserAgent = perRequestHeaders.getValue(HttpHeaderName.USER_AGENT);
                if (requestUserAgent != null
                        && currentUserAgent != null
                        && !currentUserAgent.isEmpty()
                        && !currentUserAgent.equals(requestUserAgent)) {
                    headers.set(HttpHeaderName.USER_AGENT, currentUserAgent + " " + requestUserAgent);
                }
            }
        });
    }
}
