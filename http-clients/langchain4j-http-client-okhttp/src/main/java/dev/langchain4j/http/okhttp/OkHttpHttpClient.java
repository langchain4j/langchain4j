package dev.langchain4j.http.okhttp;

import dev.langchain4j.http.HttpClient;
import dev.langchain4j.http.HttpException;
import dev.langchain4j.http.HttpRequest;
import dev.langchain4j.http.SuccessfulHttpResponse;
import dev.langchain4j.http.streaming.ServerSentEventListener;
import dev.langchain4j.http.streaming.StreamingStrategy;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;

public class OkHttpHttpClient implements HttpClient {

    // TODO make configurable?
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final okhttp3.OkHttpClient delegate;

    public OkHttpHttpClient(OkHttpHttpClientBuilder builder) {
        OkHttpClient.Builder okHttpClientBuilder = getOrDefault(builder.okHttpClientBuilder(), OkHttpClient.Builder::new);
        if (builder.connectTimeout() != null) {
            okHttpClientBuilder.connectTimeout(builder.connectTimeout());
        }
        if (builder.readTimeout() != null) {
            okHttpClientBuilder.readTimeout(builder.readTimeout());
        }
        this.delegate = okHttpClientBuilder.build();
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) throws HttpException {

        Request okHttpRequest = toOkHttpRequest(request);

        try (Response okHttpResponse = delegate.newCall(okHttpRequest).execute()) {
            if (okHttpResponse.isSuccessful()) {
                return fromOkHttpResponse(okHttpResponse, readBody(okHttpResponse));
            } else {
                throw new HttpException(okHttpResponse.code(), readBody(okHttpResponse)); // TODO
            }
        } catch (IOException e) {
            throw new RuntimeException(e); // TODO
        }
    }

    @Override
    public void execute(HttpRequest httpRequest, StreamingStrategy strategy, ServerSentEventListener listener) {

        Request request = toOkHttpRequest(httpRequest);

        delegate.newCall(request).enqueue(new Callback() {

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    ResponseBody responseBody = response.body();

                    if (!response.isSuccessful()) {
                        String message;
                        if (responseBody != null) {
                            message = responseBody.string();
                        } else {
                            message = response.message();
                        }
                        listener.onError(new HttpException(response.code(), message)); // TODO
                        return;
                    }

                    listener.onOpen(fromOkHttpResponse(response, null));

                    if (responseBody != null) {
                        try (InputStream inputStream = responseBody.byteStream()) {
                            strategy.process(inputStream, listener);
                            listener.onClose();
                        }
                    }
                } catch (Exception e) {
                    listener.onError(e);
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                listener.onError(e);
            }
        });
    }

    private Request toOkHttpRequest(HttpRequest httpRequest) {

        Request.Builder requestBuilder = new Request.Builder();

        switch (httpRequest.method()) {
            // TODO
            case GET:
                requestBuilder.get();
                break;
            case POST:
                requestBuilder.post(RequestBody.create(httpRequest.body(), JSON));
                break;
            case DELETE:
                requestBuilder.delete();
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

    private static SuccessfulHttpResponse fromOkHttpResponse(Response okHttpResponse, String body) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        okHttpResponse.headers().forEach((header) -> headers.put(header.component1(), header.component2()));

        return SuccessfulHttpResponse.builder()
                .statusCode(okHttpResponse.code())
                .headers(headers)
                .body(body)
                .build();
    }

    private static String readBody(Response okHttpResponse) {
        if (okHttpResponse.body() == null) {
            return null;
        }
        try (ResponseBody body = okHttpResponse.body()) {
            return body.string();
        } catch (Exception e) {
            // TODO log?
            return "[cannot read error response body]"; // TODO
        }
    }
}
