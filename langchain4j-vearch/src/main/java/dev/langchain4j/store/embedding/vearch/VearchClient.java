package dev.langchain4j.store.embedding.vearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.internal.Utils;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static dev.langchain4j.store.embedding.vearch.VearchApi.OK;

class VearchClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .enable(INDENT_OUTPUT);

    private final VearchApi vearchApi;

    public VearchClient(String baseUrl,
                        Duration timeout,
                        boolean logRequests,
                        boolean logResponses) {
        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
            .callTimeout(timeout)
            .connectTimeout(timeout)
            .readTimeout(timeout)
            .writeTimeout(timeout);

        if (logRequests) {
            okHttpClientBuilder.addInterceptor(new VearchRequestLoggingInterceptor());
        }
        if (logResponses) {
            okHttpClientBuilder.addInterceptor(new VearchResponseLoggingInterceptor());
        }

        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(Utils.ensureTrailingForwardSlash(baseUrl))
            .client(okHttpClientBuilder.build())
            .addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
            .build();

        vearchApi = retrofit.create(VearchApi.class);
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

    public CreateDatabaseResponse createDatabase(String databaseName) {
        try {
            Response<ResponseWrapper<CreateDatabaseResponse>> response = vearchApi.createDatabase(databaseName).execute();

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

    public List<ListSpaceResponse> listSpaceOfDatabase(String dbName) {
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

    public void upsert(UpsertRequest request) {
        try {
            Response<ResponseWrapper<UpsertResponse>> response = vearchApi.upsert(request).execute();

            if (response.isSuccessful() && response.body() != null) {
                ResponseWrapper<UpsertResponse> wrapper = response.body();
                if (wrapper.getCode() != OK) {
                    throw toException(wrapper);
                }
            } else {
                throw toException(response);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public SearchResponse search(SearchRequest request) {
        try {
            Response<ResponseWrapper<SearchResponse>> response = vearchApi.search(request).execute();

            if (response.isSuccessful() && response.body() != null) {
                ResponseWrapper<SearchResponse> wrapper = response.body();
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

    public void deleteSpace(String databaseName, String spaceName) {
        try {
            Response<Void> response = vearchApi.deleteSpace(databaseName, spaceName).execute();

            if (!response.isSuccessful()) {
                throw toException(response);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {

        private String baseUrl;
        private Duration timeout;
        private boolean logRequests;
        private boolean logResponses;

        Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        VearchClient build() {
            return new VearchClient(baseUrl, timeout, logRequests, logResponses);
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
