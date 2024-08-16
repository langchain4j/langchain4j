package dev.langchain4j.model.jina.internal.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.jina.internal.api.*;
import lombok.Builder;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.time.Duration;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

public class JinaClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(INDENT_OUTPUT);

    private final JinaApi jinaApi;
    private final String authorizationHeader;

    @Builder
    JinaClient(String baseUrl, String apiKey, Duration timeout, boolean logRequests, boolean logResponses) {

        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout);

        if (logRequests) {
            okHttpClientBuilder.addInterceptor(new RequestLoggingInterceptor());
        }
        if (logResponses) {
            okHttpClientBuilder.addInterceptor(new ResponseLoggingInterceptor());
        }

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Utils.ensureTrailingForwardSlash(baseUrl))
                .client(okHttpClientBuilder.build())
                .addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
                .build();

        this.jinaApi = retrofit.create(JinaApi.class);
        this.authorizationHeader = "Bearer " + ensureNotBlank(apiKey, "apiKey");
    }

    public JinaEmbeddingResponse embed(JinaEmbeddingRequest request) {
        try {
            retrofit2.Response<JinaEmbeddingResponse> retrofitResponse
                    = jinaApi.embed(request, authorizationHeader).execute();
            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public JinaRerankingResponse rerank(JinaRerankingRequest request) {
        try {
            retrofit2.Response<JinaRerankingResponse> retrofitResponse
                    = jinaApi.rerank(request, authorizationHeader).execute();

            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static RuntimeException toException(retrofit2.Response<?> response) throws IOException {
        int code = response.code();
        String body = response.errorBody().string();
        String errorMessage = String.format("status code: %s; body: %s", code, body);
        return new RuntimeException(errorMessage);
    }
}
