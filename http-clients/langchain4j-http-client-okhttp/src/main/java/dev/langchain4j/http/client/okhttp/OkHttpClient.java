package dev.langchain4j.http.client.okhttp;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpException;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.internal.Utils;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.io.InputStream;

public class OkHttpClient implements HttpClient {

    // TODO make configurable?
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final okhttp3.OkHttpClient delegate;

    public OkHttpClient(OkHttpClientBuilder builder) {
        okhttp3.OkHttpClient.Builder okHttpClientBuilder = Utils.getOrDefault(builder.okHttpClientBuilder(), okhttp3.OkHttpClient.Builder::new);
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
    public void execute(HttpRequest httpRequest, ServerSentEventParser parser, ServerSentEventListener listener) {

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
                            parser.parse(inputStream, listener);
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

        httpRequest.headers().forEach((name, values) -> {
            if (values != null) {
                values.forEach(value -> requestBuilder.addHeader(name, value));
            }
        });

        return requestBuilder.build();
    }

    private static SuccessfulHttpResponse fromOkHttpResponse(Response okHttpResponse, String body) throws IOException { // TODO IOException
        return SuccessfulHttpResponse.builder()
                .statusCode(okHttpResponse.code())
                .headers(okHttpResponse.headers().toMultimap())
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
            return "Cannot read error response body: " + e.getMessage();
        }
    }
}
