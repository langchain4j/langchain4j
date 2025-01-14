package dev.langchain4j.http.okhttp;

import dev.langchain4j.http.AbstractHttpClient;
import dev.langchain4j.http.HttpException;
import dev.langchain4j.http.HttpRequest;
import dev.langchain4j.http.HttpResponse;
import dev.langchain4j.http.ServerSentEvent;
import dev.langchain4j.http.ServerSentEventListener;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;

public class OkHttpHttpClient extends AbstractHttpClient {

    // TODO make configurable?
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final okhttp3.OkHttpClient delegate;
    private final boolean logRequests;
    private final boolean logResponses;

    public OkHttpHttpClient(OkHttpHttpClientBuilder builder) {
        OkHttpClient.Builder okHttpClientBuilder = getOrDefault(builder.okHttpClientBuilder(), OkHttpClient.Builder::new);
        this.delegate = okHttpClientBuilder
                .connectTimeout(builder.connectTimeout())
                .readTimeout(builder.readTimeout())
                .build();
        this.logRequests = builder.logRequests();
        this.logResponses = builder.logResponses();
    }

    public static OkHttpHttpClientBuilder builder() { // TODO
        return new OkHttpHttpClientBuilder();
    }

    @Override
    protected HttpResponse doExecute(HttpRequest httpRequest) {

        Request okHttpRequest = toOkHttpRequest(httpRequest);

        try (Response okHttpResponse = delegate.newCall(okHttpRequest).execute()) {
            if (okHttpResponse.isSuccessful()) {
                return fromOkHttpResponse(okHttpResponse);
            } else {
                throw new HttpException(okHttpResponse.code(), getBody(okHttpResponse)); // TODO
            }
        } catch (IOException e) {
            throw new RuntimeException(e); // TODO
        }
    }

    @Override
    protected void doExecute(HttpRequest httpRequest, ServerSentEventListener listener) {

        Request okHttpRequest = toOkHttpRequest(httpRequest);

        Map<String, String> headers = httpRequest.headers();
        if (headers.containsKey("Content-Type") && "application/x-ndjson".equals(headers.get("Content-Type"))) {

            // TODO extract into a separate HttpClient method? provide as a strategy?

            delegate.newCall(okHttpRequest).enqueue(new okhttp3.Callback() {

                @Override
                public void onResponse(Call call, Response response) {
                    try (response) {
                        ResponseBody responseBody = response.body();

                        if (!response.isSuccessful()) {
                            String message;
                            if (responseBody != null) {
                                message = responseBody.string();
                            } else {
                                message = response.message();
                            }
                            throw new HttpException(response.code(), message);
                        }

//                        HttpResponse httpResponse = fromOkHttpResponse(response); TODO do not read body
//                        listener.onStart(httpResponse);

                        try (InputStream inputStream = responseBody.byteStream();
                             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

                            String line;
                            while ((line = reader.readLine()) != null) {
                                listener.onEvent(new ServerSentEvent(null, line));
                            }
                            listener.onFinish(); // TODO?
                        } catch (IOException e) { // TODO?
                            listener.onError(e);
                        }
                    } catch (Exception e) { // TODO?
                        listener.onError(e);
                    }
                }

                @Override
                public void onFailure(Call call, IOException e) {
                    listener.onError(e);
                }
            });

        } else {

            EventSourceListener eventSourceListener = new EventSourceListener() {

                @Override
                public void onOpen(EventSource eventSource, okhttp3.Response response) {
                    try {
                        HttpResponse httpResponse = fromOkHttpResponse(response);
                        listener.onStart(httpResponse);
                    } catch (IOException e) {
                        // TODO call listener.onError?
                        throw new RuntimeException(e); // TODO
                    }
                }

                @Override
                public void onEvent(EventSource eventSource, String id, String type, String dataString) {
                    ServerSentEvent serverSentEvent = new ServerSentEvent(type, dataString);
                    listener.onEvent(serverSentEvent);
                }

                @Override
                public void onFailure(EventSource eventSource, Throwable t, okhttp3.Response response) {
                    listener.onError(t); // TODO propagate response message/code
                }

                @Override
                public void onClosed(EventSource eventSource) {
                    listener.onFinish();
                }
            };

            // TODO reuse factory?
            EventSources.createFactory(delegate).newEventSource(okHttpRequest, eventSourceListener);
        }
    }

    private static Request toOkHttpRequest(HttpRequest httpRequest) {

        Request.Builder requestBuilder = new Request.Builder();

        switch (httpRequest.method()) {
            case GET:
                requestBuilder.get();
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

    @Override
    public void close() {
        // TODO
    }
}
