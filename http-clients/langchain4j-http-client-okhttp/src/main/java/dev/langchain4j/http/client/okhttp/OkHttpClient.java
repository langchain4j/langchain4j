package dev.langchain4j.http.client.okhttp;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.http.client.FormDataFile;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static dev.langchain4j.http.client.sse.ServerSentEventListenerUtils.ignoringExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;

public class OkHttpClient implements HttpClient {

    private final okhttp3.OkHttpClient client;

    public OkHttpClient(OkHttpClientBuilder builder) {
        okhttp3.OkHttpClient.Builder okBuilder =
                getOrDefault(builder.okHttpClientBuilder(), okhttp3.OkHttpClient.Builder::new);

        if (builder.connectTimeout() != null) {
            okBuilder.connectTimeout(builder.connectTimeout().toMillis(), TimeUnit.MILLISECONDS);
        }
        if (builder.readTimeout() != null) {
            okBuilder.readTimeout(builder.readTimeout().toMillis(), TimeUnit.MILLISECONDS);
        }

        this.client = okBuilder.build();
    }

    public static OkHttpClientBuilder builder() {
        return new OkHttpClientBuilder();
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) throws HttpException {
        Request okRequest = toOkHttpRequest(request);
        try (Response response = client.newCall(okRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new HttpException(response.code(), readBody(response));
            }
            return fromOkHttpResponse(response);
        } catch (SocketTimeoutException e) {
            throw new TimeoutException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        Request okRequest = toOkHttpRequest(request);
        client.newCall(okRequest).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) {
                try (response) {
                    if (!response.isSuccessful()) {
                        HttpException exception = new HttpException(response.code(), readBody(response));
                        ignoringExceptions(() -> listener.onError(exception));
                        return;
                    }

                    SuccessfulHttpResponse successResponse = fromOkHttpResponse(response);
                    ignoringExceptions(() -> listener.onOpen(successResponse));

                    try (InputStream inputStream = getInputStream(response)) {
                        parser.parse(inputStream, listener);
                        ignoringExceptions(listener::onClose);
                    } catch (Exception e) {
                        ignoringExceptions(() -> listener.onError(e));
                    }
                } catch (Exception e) {
                    ignoringExceptions(() -> listener.onError(e));
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                if (e instanceof SocketTimeoutException) {
                    ignoringExceptions(() -> listener.onError(new TimeoutException(e)));
                } else {
                    ignoringExceptions(() -> listener.onError(e));
                }
            }
        });
    }

    private InputStream getInputStream(Response response) {
        return response.body().byteStream();
    }

    private SuccessfulHttpResponse fromOkHttpResponse(Response response) {
        Map<String, List<String>> headers = new HashMap<>();
        for (String name : response.headers().names()) {
            headers.put(name, response.headers().values(name));
        }

        String contentType = response.header("content-type");
        String body;
        if (contentType != null && contentType.contains("text/event-stream")) {
            body = null;
        } else {
            body = readBody(response);
        }

        return SuccessfulHttpResponse.builder()
                .statusCode(response.code())
                .headers(headers)
                .body(body)
                .build();
    }

    private String readBody(Response response) {
        try {
            return response.body().string();
        } catch (Exception e) {
            return "Cannot read error response body: " + e.getMessage();
        }
    }

    private Request toOkHttpRequest(HttpRequest request) {
        Request.Builder builder = new Request.Builder().url(request.url());

        request.headers().forEach((name, values) -> {
            if (values != null) {
                for (String value : values) {
                    builder.addHeader(name, value);
                }
            }
        });

        RequestBody body = buildRequestBody(request);

        switch (request.method()) {
            case GET -> builder.get();
            case POST -> builder.post(body != null ? body : RequestBody.create(new byte[0]));
            case DELETE -> {
                if (body != null) {
                    builder.delete(body);
                } else {
                    builder.delete();
                }
            }
        }

        return builder.build();
    }

    private RequestBody buildRequestBody(HttpRequest request) {
        if (!request.formDataFields().isEmpty() || !request.formDataFiles().isEmpty()) {
            MultipartBody.Builder multipartBuilder =
                    new MultipartBody.Builder().setType(MultipartBody.FORM);

            for (Map.Entry<String, String> entry : request.formDataFields().entrySet()) {
                multipartBuilder.addFormDataPart(entry.getKey(), entry.getValue());
            }

            for (Map.Entry<String, FormDataFile> entry : request.formDataFiles().entrySet()) {
                FormDataFile file = entry.getValue();
                RequestBody fileBody = RequestBody.create(
                        file.content(), MediaType.parse(file.contentType()));
                multipartBuilder.addFormDataPart(entry.getKey(), file.fileName(), fileBody);
            }

            return multipartBuilder.build();
        }

        if (request.body() != null) {
            return RequestBody.create(request.body(), MediaType.parse("application/json"));
        }

        return null;
    }
}
