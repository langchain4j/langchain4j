package dev.langchain4j.model.jinaAi.rerank;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.time.Duration;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE;
import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static com.fasterxml.jackson.databind.cfg.EnumFeature.WRITE_ENUMS_TO_LOWERCASE;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

class JinaClient {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.enable(INDENT_OUTPUT);
        objectMapper.setPropertyNamingStrategy(SNAKE_CASE);
        objectMapper.configure(WRITE_ENUMS_TO_LOWERCASE, true);
        objectMapper.setSerializationInclusion(NON_NULL);
        objectMapper.enable(INDENT_OUTPUT);
    }

    private final JinaApi jinaApi;
    private final String authorizationHeader;

    @Builder
    JinaClient(String baseUrl, String apiKey, Duration timeout, Boolean logRequests, Boolean logResponses) {

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
                .baseUrl(baseUrl)
                .client(okHttpClientBuilder.build())
                .addConverterFactory(JacksonConverterFactory.create(objectMapper))
                .build();

        this.jinaApi = retrofit.create(JinaApi.class);
        this.authorizationHeader = "Bearer " + ensureNotBlank(apiKey, "apiKey");
    }

    public RerankResponse rerank(RerankRequest request) {
        try {
            retrofit2.Response<RerankResponse> retrofitResponse
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
