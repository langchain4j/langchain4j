package dev.langchain4j.model.nomic;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.langchain4j.internal.Utils;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.time.Duration;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

class NomicClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final NomicApi nomicApi;
    private final String authorizationHeader;

    NomicClient(String baseUrl, String apiKey, Duration timeout, Boolean logRequests, Boolean logResponses) {

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

        this.nomicApi = retrofit.create(NomicApi.class);
        this.authorizationHeader = "Bearer " + ensureNotBlank(apiKey, "apiKey");
    }

    public static NomicClientBuilder builder() {
        return new NomicClientBuilder();
    }

    public EmbeddingResponse embed(EmbeddingRequest request) {
        try {
            retrofit2.Response<EmbeddingResponse> retrofitResponse
                    = nomicApi.embed(request, authorizationHeader).execute();

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

    public static class NomicClientBuilder {
        private String baseUrl;
        private String apiKey;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;

        NomicClientBuilder() {
        }

        public NomicClientBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public NomicClientBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public NomicClientBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public NomicClientBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public NomicClientBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public NomicClient build() {
            return new NomicClient(this.baseUrl, this.apiKey, this.timeout, this.logRequests, this.logResponses);
        }

        public String toString() {
            return "NomicClient.NomicClientBuilder(baseUrl=" + this.baseUrl + ", apiKey=" + this.apiKey + ", timeout=" + this.timeout + ", logRequests=" + this.logRequests + ", logResponses=" + this.logResponses + ")";
        }
    }
}
