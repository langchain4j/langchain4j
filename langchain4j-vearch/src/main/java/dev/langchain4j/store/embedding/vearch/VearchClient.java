package dev.langchain4j.store.embedding.vearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.internal.Utils;
import lombok.Builder;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static dev.langchain4j.store.embedding.vearch.VearchApi.OK;

class VearchClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
                .baseUrl(Utils.ensureTrailingForwardSlash(baseUrl))
                .client(okHttpClient)
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

    public void bulk(String dbName, String spaceName, BulkRequest request) {
        try {
            StringBuilder bodyString = new StringBuilder();
            for (Map<String, Object> document : request.getDocuments()) {
                Map<String, Object> fieldsExceptId = new HashMap<>();
                for (Map.Entry<String, Object> entry : document.entrySet()) {
                    String fieldName = entry.getKey();
                    Object value = entry.getValue();

                    if ("_id".equals(fieldName)) {
                        bodyString.append("{\"index\": {\"_id\": \"").append(value).append("\"}}\n");
                    } else {
                        fieldsExceptId.put(fieldName, value);
                    }
                }
                bodyString.append(OBJECT_MAPPER.writeValueAsString(fieldsExceptId)).append("\n");
            }
            RequestBody body = RequestBody.create(bodyString.toString(), MediaType.parse("application/json; charset=utf-8"));
            Response<List<BulkResponse>> response = vearchApi.bulk(dbName, spaceName, body).execute();

            if (response.isSuccessful() && response.body() != null) {
                List<BulkResponse> bulkResponses = response.body();
                bulkResponses.forEach(bulkResponse -> {
                    if (bulkResponse.getStatus() != OK) {
                        throw toException(bulkResponse.getStatus(), bulkResponse.getError());
                    }
                });
            } else {
                throw toException(response);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public SearchResponse search(String dbName, String spaceName, SearchRequest request) {
        try {
            Response<SearchResponse> response = vearchApi.search(dbName, spaceName, request).execute();

            if (response.isSuccessful() && response.body() != null) {
                SearchResponse searchResponse = response.body();
                if (Boolean.TRUE.equals(searchResponse.getTimeout())) {
                    throw new RuntimeException("Search Timeout");
                }
                return searchResponse;
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
