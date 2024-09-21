package dev.langchain4j.model.voyage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.langchain4j.internal.Utils;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.jetbrains.annotations.NotNull;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.time.Duration;

class VoyageClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final VoyageApi voyageApi;

    VoyageClient(
            String baseUrl,
            Duration timeout,
            String apiKey,
            Boolean logRequests,
            Boolean logResponses
    ) {
        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout);

        okHttpClientBuilder.addInterceptor(new AuthorizationInterceptor(apiKey));
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

        this.voyageApi = retrofit.create(VoyageApi.class);
    }

    EmbeddingResponse embed(EmbeddingRequest request) {
        try {
            retrofit2.Response<EmbeddingResponse> retrofitResponse
                    = voyageApi.embed(request).execute();

            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    RerankResponse rerank(RerankRequest request) {
        try {
            retrofit2.Response<RerankResponse> retrofitResponse
                    = voyageApi.rerank(request).execute();

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

    static VoyageClientBuilder builder() {
        return new VoyageClientBuilder();
    }

    static class AuthorizationInterceptor implements Interceptor {

        private final String apiKey;

        AuthorizationInterceptor(String apiKey) {
            this.apiKey = apiKey;
        }

        @NotNull
        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {
            Request.Builder builder = chain.request().newBuilder();

            builder.addHeader("Authorization", "Bearer " + apiKey);

            return chain.proceed(builder.build());
        }
    }

    static class VoyageClientBuilder {

        private String baseUrl;
        private Duration timeout;
        private String apiKey;
        private boolean logRequests;
        private boolean logResponses;

        VoyageClientBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        VoyageClientBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        VoyageClientBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        VoyageClientBuilder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        VoyageClientBuilder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        VoyageClient build() {
            return new VoyageClient(baseUrl, timeout, apiKey, logRequests, logResponses);
        }
    }
}
