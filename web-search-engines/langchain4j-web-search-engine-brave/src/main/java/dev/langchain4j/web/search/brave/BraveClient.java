package dev.langchain4j.web.search.brave;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.langchain4j.internal.Utils;
import lombok.Builder;

import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.time.Duration;

public class BraveClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final BraveApi braveApi;

    @Builder
    public BraveClient(String baseUrl, Duration timeout) {

        // Build OkHttpClient with the logging interceptor
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

        this.braveApi = retrofit.create(BraveApi.class);
    }

    public BraveResponse search(BraveWebSearchRequest braveWebSearchRequest) {
        try {
            System.out.println(braveWebSearchRequest.getCount()+" "+braveWebSearchRequest.getQuery()+" "+braveWebSearchRequest.getCount()+" "+braveWebSearchRequest.getSafeSearch()+" "+braveWebSearchRequest.getResultFilter()+" "+braveWebSearchRequest.getFreshness());
            Response<BraveResponse> retrofitResponse = braveApi
                    .search(
                            braveWebSearchRequest.getApiKey(),
                            braveWebSearchRequest.getQuery(),
                            braveWebSearchRequest.getCount(),
                            braveWebSearchRequest.getSafeSearch(),
                            braveWebSearchRequest.getResultFilter(),
                            braveWebSearchRequest.getFreshness()
                    )
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

    private static RuntimeException toException(Response<BraveResponse> retrofitResponse) throws IOException {
        int code = retrofitResponse.code();
        String body = retrofitResponse.errorBody().string();
        String errorMessage = String.format("status code: %s; body: %s", code, body);
        return new RuntimeException(errorMessage);
    }
}
