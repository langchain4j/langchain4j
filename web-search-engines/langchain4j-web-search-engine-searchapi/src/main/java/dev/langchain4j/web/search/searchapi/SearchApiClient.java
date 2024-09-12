package dev.langchain4j.web.search.searchapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.ModelConstant;
import lombok.Builder;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

class SearchApiClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(INDENT_OUTPUT);

    private static final String BASE_URL = "https://www.searchapi.io/";
    private final SearchApi api;

    @Builder
    SearchApiClient(String baseUrl, Duration timeout) {
        baseUrl = getOrDefault(baseUrl, BASE_URL);
        timeout = getOrDefault(timeout, ModelConstant.DEFAULT_CLIENT_TIMEOUT);

        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout);
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(ensureNotBlank(baseUrl, "baseUrl"))
                .client(okHttpClientBuilder.build())
                .addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
                .build();
        this.api = retrofit.create(SearchApi.class);
    }

    SearchApiWebSearchResponse search(SearchApiWebSearchRequest request) {
        Map<String, Object> finalParameters = new HashMap<>(request.getFinalOptionalParameters());
        finalParameters.put("engine", request.getEngine());
        finalParameters.put("q", request.getQuery());
        String bearerToken = "Bearer " + request.getApiKey();
        try {
            Response<SearchApiWebSearchResponse> response = api.search(finalParameters, bearerToken).execute();
            return getBody(response);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private SearchApiWebSearchResponse getBody(Response<SearchApiWebSearchResponse> response) throws IOException {
        if (response.isSuccessful()) {
            return response.body();
        } else {
            throw toException(response);
        }
    }

    private static RuntimeException toException(Response<?> response) throws IOException {
        try (ResponseBody responseBody = response.errorBody()) {
            int code = response.code();
            if (responseBody != null) {
                String body = responseBody.string();
                String errorMessage = String.format("status code: %s; body: %s", code, body);
                return new RuntimeException(errorMessage);
            } else {
                return new RuntimeException(String.format("status code: %s;", code));
            }
        }
    }
}
