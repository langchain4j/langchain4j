package dev.langchain4j.model.cohere;

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
import java.net.Proxy;
import java.time.Duration;
import java.util.Objects;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

class CohereClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final CohereApi cohereApi;
    private final String authorizationHeader;

    CohereClient(String baseUrl, String apiKey, Duration timeout, Proxy proxy, Boolean logRequests, Boolean logResponses) {

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

        if (Objects.nonNull(proxy)) {
            okHttpClientBuilder.proxy(proxy);
        }

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Utils.ensureTrailingForwardSlash(baseUrl))
                .client(okHttpClientBuilder.build())
                .addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
                .build();

        this.cohereApi = retrofit.create(CohereApi.class);
        this.authorizationHeader = "Bearer " + ensureNotBlank(apiKey, "apiKey");
    }

    public static CohereClientBuilder builder() {
        return new CohereClientBuilder();
    }

    EmbedResponse embed(EmbedRequest request) {
        try {
            retrofit2.Response<EmbedResponse> retrofitResponse
                    = cohereApi.embed(request, authorizationHeader).execute();

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
                    = cohereApi.rerank(request, authorizationHeader).execute();

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

    public static class CohereClientBuilder {
        private String baseUrl;
        private String apiKey;
        private Duration timeout;
        private Proxy proxy;
        private Boolean logRequests;
        private Boolean logResponses;

        CohereClientBuilder() {
        }

        public CohereClientBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public CohereClientBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public CohereClientBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public CohereClientBuilder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public CohereClientBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public CohereClientBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public CohereClient build() {
            return new CohereClient(this.baseUrl, this.apiKey, this.timeout, this.proxy, this.logRequests, this.logResponses);
        }

        public String toString() {
            return "CohereClient.CohereClientBuilder(baseUrl=" + this.baseUrl + ", apiKey=" + this.apiKey + ", timeout=" + this.timeout + ", proxy=" + this.proxy + ", logRequests=" + this.logRequests + ", logResponses=" + this.logResponses + ")";
        }
    }
}
