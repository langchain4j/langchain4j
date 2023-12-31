package dev.langchain4j.store.embedding.vearch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Builder;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.time.Duration;

import static com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES;

public class VearchClient {

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private final VearchApi vearchApi;

    @Builder
    public VearchClient(String baseUrl, Duration timeout) {

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(GSON))
                .build();

        vearchApi = retrofit.create(VearchApi.class);
    }

    public InsertionResponse batchInsertion(String dbName, String spaceName, InsertionRequest request) {
        // TODO
        return null;
    }

    public SearchResponse search(String dbName, String spaceName, SearchRequest request) {
        // TODO
        return null;
    }
}
