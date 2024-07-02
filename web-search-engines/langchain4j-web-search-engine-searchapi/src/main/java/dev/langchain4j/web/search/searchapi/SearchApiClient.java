package dev.langchain4j.web.search.searchapi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Builder;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES;

class SearchApiClient {

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .create();

    private final Retrofit retrofitBase;

    @Builder
    SearchApiClient(String baseUrl,
                           Duration timeout) {
        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout);
        this.retrofitBase = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClientBuilder.build())
                .addConverterFactory(GsonConverterFactory.create(GSON))
                .build();
    }

    SearchApiResponse search(SearchApiRequest request) {
        Map<String, Object> finalParameters = new HashMap<>(request.getAdditionalParameters());
        SearchApiEngine engine = request.getEngine();
        finalParameters.put("engine", engine.getValue());
        finalParameters.put("q", request.getQuery());
        String bearerToken = "Bearer " + request.getApiKey();
        return searchByEngine(finalParameters, bearerToken, engine);
    }

    private SearchApiResponse searchByEngine(Map<String, Object> finalParameters,
                                             String bearerToken,
                                             SearchApiEngine engine) {
        try {
            switch (engine) {
                case GOOGLE_SEARCH:
                    SearchApiGoogleSearchApi api = retrofitBase.create(SearchApiGoogleSearchApi.class);
                    Response<SearchApiGoogleSearchResponse> response = api.search(finalParameters, bearerToken)
                            .execute();
                    return getBody(response);
                default:
                    throw new RuntimeException("Invalid engine");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private <T extends SearchApiResponse> T getBody(Response<T> response) throws IOException {
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
