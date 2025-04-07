package dev.langchain4j.model.jina.internal.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.jina.internal.api.JinaApi;
import dev.langchain4j.model.jina.internal.api.JinaEmbeddingRequest;
import dev.langchain4j.model.jina.internal.api.JinaEmbeddingResponse;
import dev.langchain4j.model.jina.internal.api.JinaRerankingRequest;
import dev.langchain4j.model.jina.internal.api.JinaRerankingResponse;
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

    public static JinaClientBuilder builder() {
        return new JinaClientBuilder();
    }

    public JinaEmbeddingResponse embed(JinaEmbeddingRequest request) {
        try {
            retrofit2.Response<JinaEmbeddingResponse> retrofitResponse =
                    jinaApi.embed(request, authorizationHeader).execute();
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
            retrofit2.Response<JinaRerankingResponse> retrofitResponse =
                    jinaApi.rerank(request, authorizationHeader).execute();

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

    public static class JinaClientBuilder {
        private String baseUrl;
        private String apiKey;
        private Duration timeout;
        private boolean logRequests;
        private boolean logResponses;

        JinaClientBuilder() {
        }

        public JinaClientBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public JinaClientBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public JinaClientBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public JinaClientBuilder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public JinaClientBuilder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public JinaClient build() {
            return new JinaClient(this.baseUrl, this.apiKey, this.timeout, this.logRequests, this.logResponses);
        }

        public String toString() {
            return "JinaClient.JinaClientBuilder(baseUrl=" + this.baseUrl + ", apiKey=" + this.apiKey + ", timeout=" + this.timeout + ", logRequests=" + this.logRequests + ", logResponses=" + this.logResponses + ")";
        }
    }
}
