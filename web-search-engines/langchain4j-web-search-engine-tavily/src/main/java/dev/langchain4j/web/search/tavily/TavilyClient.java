package dev.langchain4j.web.search.tavily;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.langchain4j.internal.Utils;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.time.Duration;

class TavilyClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final TavilyApi tavilyApi;

    public TavilyClient(String baseUrl, Duration timeout) {

        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Utils.ensureTrailingForwardSlash(baseUrl))
                .client(okHttpClientBuilder.build())
                .addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
                .build();

        this.tavilyApi = retrofit.create(TavilyApi.class);
    }

    public static TavilyClientBuilder builder() {
        return new TavilyClientBuilder();
    }

    public TavilyResponse search(TavilySearchRequest searchRequest) {
        try {
            Response<TavilyResponse> retrofitResponse = tavilyApi
                    .search(searchRequest)
                    .execute();
            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static RuntimeException toException(Response<?> response) throws IOException {
        int code = response.code();
        String body = response.errorBody().string();
        String errorMessage = String.format("status code: %s; body: %s", code, body);
        return new RuntimeException(errorMessage);
    }

    public static class TavilyClientBuilder {
        private String baseUrl;
        private Duration timeout;

        TavilyClientBuilder() {
        }

        public TavilyClientBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public TavilyClientBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public TavilyClient build() {
            return new TavilyClient(this.baseUrl, this.timeout);
        }

        public String toString() {
            return "TavilyClient.TavilyClientBuilder(baseUrl=" + this.baseUrl + ", timeout=" + this.timeout + ")";
        }
    }
}
