package dev.langchain4j.store.embedding.vearch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.langchain4j.store.embedding.vearch.api.*;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES;
import static dev.langchain4j.store.embedding.vearch.api.VearchApi.OK;

public class VearchClient {

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private final VearchApi vearchApi;

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
            return new VearchClient(baseUrl, timeout);
        }
    }

    public List<ListDatabaseResponse> listDatabase() {
        try {
            Response<ResponseWrapper<List<ListDatabaseResponse>>> response = vearchApi.listDatabase().execute();

            if (response.isSuccessful() && response.body() != null) {
                ResponseWrapper<List<ListDatabaseResponse>> wrapper = response.body();
                if (wrapper.getCode() != OK) {
                    throw toException(wrapper);
                }
                return wrapper.getData();
            } else {
                throw toException(response);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public CreateDatabaseResponse createDatabase(CreateDatabaseRequest request) {
        try {
            Response<ResponseWrapper<CreateDatabaseResponse>> response = vearchApi.createDatabase(request).execute();

            if (response.isSuccessful() && response.body() != null) {
                ResponseWrapper<CreateDatabaseResponse> wrapper = response.body();
                if (wrapper.getCode() != OK) {
                    throw toException(wrapper);
                }
                return wrapper.getData();
            } else {
                throw toException(response);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<ListSpaceResponse> listSpace(String dbName) {
        try {
            Response<ResponseWrapper<List<ListSpaceResponse>>> response = vearchApi.listSpaceOfDatabase(dbName).execute();

            if (response.isSuccessful() && response.body() != null) {
                ResponseWrapper<List<ListSpaceResponse>> wrapper = response.body();
                if (wrapper.getCode() != OK) {
                    throw toException(wrapper);
                }
                return wrapper.getData();
            } else {
                throw toException(response);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public CreateSpaceResponse createSpace(String dbName, CreateSpaceRequest request) {
        try {
            Response<ResponseWrapper<CreateSpaceResponse>> response = vearchApi.createSpace(dbName, request).execute();

            if (response.isSuccessful() && response.body() != null) {
                ResponseWrapper<CreateSpaceResponse> wrapper = response.body();
                if (wrapper.getCode() != OK) {
                    throw toException(wrapper);
                }
                return wrapper.getData();
            } else {
                throw toException(response);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public InsertionResponse batchInsert(InsertionRequest request) {
        try {
            Response<InsertionResponse> response = vearchApi.batchInsert(request).execute();

            if (response.isSuccessful() && response.body() != null) {
                InsertionResponse insertionResponse = response.body();
                if (insertionResponse.getCode() != OK) {
                    throw toException(insertionResponse.getCode(), insertionResponse.getMsg());
                }
                return insertionResponse;
            } else {
                throw toException(response);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public SearchResponse search(SearchRequest request) {
        try {
            Response<SearchResponse> response = vearchApi.search(request).execute();

            if (response.isSuccessful() && response.body() != null) {
                SearchResponse searchResponse = response.body();
                if (searchResponse.getCode() != OK) {
                    throw toException(searchResponse.getCode(), searchResponse.getMsg());
                }
                return searchResponse;
            } else {
                throw toException(response);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private RuntimeException toException(Response<?> response) throws IOException {
        int code = response.code();
        String body = response.errorBody().string();

        String errorMessage = String.format("status code: %s; body: %s", code, body);
        return new RuntimeException(errorMessage);
    }

    private RuntimeException toException(ResponseWrapper<?> responseWrapper) {
        return toException(responseWrapper.getCode(), responseWrapper.getMsg());
    }

    private RuntimeException toException(int code, String msg) {
        String errorMessage = String.format("code: %s; message: %s", code, msg);

        return new RuntimeException(errorMessage);
    }
}
