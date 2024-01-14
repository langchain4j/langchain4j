package dev.langchain4j.store.embedding.vearch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.langchain4j.store.embedding.vearch.api.*;
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

    public VearchClient(String baseUrl, Duration timeout) {
        // TODO: builder

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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String baseUrl;
        private Duration timeout;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public VearchClient build() {
            VearchClient vearchClient = new VearchClient(baseUrl, timeout);
            return vearchClient;
        }
    }

    public ListDatabaseResponse listDatabase() {
        // TODO
        return null;
    }

    public CreateDatabaseResponse createDatabase() {
        // TODO
        return null;
    }

    public ListSpaceResponse listSpace() {
        // TODO
        return null;
    }

    public CreateSpaceRequest createSpaceRequest() {
        // TODO
        return null;
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
