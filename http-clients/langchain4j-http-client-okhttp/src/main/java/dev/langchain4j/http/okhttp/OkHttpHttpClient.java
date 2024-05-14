package dev.langchain4j.http.okhttp;

import dev.langchain4j.http.*;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;

public class OkHttpHttpClient extends AbstractHttpClient {

    // TODO make configurable?
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final okhttp3.OkHttpClient client;
    private final boolean logRequests;
    private final boolean logResponses;

    public OkHttpHttpClient(OkHttpHttpClientBuilder builder) {
        OkHttpClient.Builder okHttpClientBuilder = getOrDefault(builder.okHttpClientBuilder(), OkHttpClient.Builder::new);
        okHttpClientBuilder
                .callTimeout(builder.timeout())
                .connectTimeout(builder.timeout())
                .readTimeout(builder.timeout())
                .writeTimeout(builder.timeout());
        this.client = okHttpClientBuilder.build();

        this.logRequests = builder.logRequests();
        this.logResponses = builder.logResponses();
    }
    // TODO customize: baseUrl, etc

    public static OkHttpHttpClientBuilder builder() { // TODO
        return new OkHttpHttpClientBuilder();
    }

    @Override
    protected HttpResponse doExecute(HttpRequest httpRequest) {

        Request okHttpRequest = toOkHttpRequest(httpRequest);

        try (Response okHttpResponse = client.newCall(okHttpRequest).execute()) {
            if (okHttpResponse.isSuccessful()) {
                return fromOkHttpResponse(okHttpResponse);
            } else {
                throw new HttpException(okHttpResponse.code(), getBody(okHttpResponse));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void doExecute(HttpRequest httpRequest, ServerSentEventListener listener) {

        EventSourceListener eventSourceListener = new EventSourceListener() {

            @Override
            public void onOpen(EventSource eventSource, okhttp3.Response response) {
                try {
                    HttpResponse httpResponse = fromOkHttpResponse(response);
                    listener.onStart(httpResponse);
                } catch (IOException e) {
                    throw new RuntimeException(e); // TODO
                }
            }

            @Override
            public void onEvent(EventSource eventSource, String id, String type, String dataString) {
                ServerSentEvent serverSentEvent = ServerSentEvent.builder()
                        .type(type)
                        .data(dataString)
                        .build();
                listener.onEvent(serverSentEvent);
            }

            @Override
            public void onFailure(EventSource eventSource, Throwable t, okhttp3.Response response) {
                listener.onError(t); // TODO propagate response message/code?
            }

            @Override
            public void onClosed(EventSource eventSource) {
                listener.onFinish();
            }
        };

        // TODO reuse factory?
        EventSources.createFactory(client).newEventSource(toOkHttpRequest(httpRequest), eventSourceListener);
    }

    private static Request toOkHttpRequest(HttpRequest httpRequest) {

        Request.Builder requestBuilder = new Request.Builder();

        switch (httpRequest.method()) {
            case GET:
                requestBuilder.get(); // TODO
                break;
            case POST:
                requestBuilder.post(RequestBody.create(httpRequest.body(), JSON));
                break;
            default:
                throw new RuntimeException("Unsupported HTTP method: " + httpRequest.method());
        }

        requestBuilder.url(httpRequest.url());

        httpRequest.headers().forEach((name, value) -> {
            if (value != null) {
                requestBuilder.addHeader(name, value);
            }
        });

        // TODO content type/length?

        return requestBuilder.build();
    }

    private static HttpResponse fromOkHttpResponse(Response okHttpResponse) throws IOException {

        Map<String, String> headers = new HashMap<>();
        okHttpResponse.headers().forEach((header) -> headers.put(header.component1(), header.component2()));

        return HttpResponse.builder()
                .statusCode(okHttpResponse.code())
                .headers(headers)
                .body(getBody(okHttpResponse))
                .build();
    }

    private static String getBody(Response okHttpResponse) throws IOException {
        if (okHttpResponse.body() == null) {
            return null;
        }
        try (ResponseBody body = okHttpResponse.body()) {
            return body.string();
        }
    }

    @Override
    protected boolean logRequests() {
        return logRequests;
    }

    @Override
    public boolean logResponses() {
        return logResponses;
    }
}
