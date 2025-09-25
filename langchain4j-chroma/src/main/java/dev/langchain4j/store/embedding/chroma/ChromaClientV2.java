package dev.langchain4j.store.embedding.chroma;

import static dev.langchain4j.internal.Utils.getOrDefault;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.langchain4j.internal.Utils;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

class ChromaClientV2 implements ChromaClient {

    private final ChromaApiV2 chromaApi;
    private final String tenantName;
    private final String databaseName;

    private ChromaClientV2(Builder builder) {
        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder()
                .callTimeout(builder.timeout)
                .connectTimeout(builder.timeout)
                .readTimeout(builder.timeout)
                .writeTimeout(builder.timeout);

        if (builder.logRequests) {
            httpClientBuilder.addInterceptor(new ChromaRequestLoggingInterceptor());
        }
        if (builder.logResponses) {
            httpClientBuilder.addInterceptor(new ChromaResponseLoggingInterceptor());
        }

        this.tenantName = getOrDefault(builder.tenantName, "default");
        this.databaseName = getOrDefault(builder.databaseName, "default");

        ObjectMapper objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Utils.ensureTrailingForwardSlash(builder.baseUrl))
                .client(httpClientBuilder.build())
                .addConverterFactory(JacksonConverterFactory.create(objectMapper))
                .build();

        this.chromaApi = retrofit.create(ChromaApiV2.class);
    }

    public static class Builder {

        private String baseUrl;
        private Duration timeout;
        private boolean logRequests;
        private boolean logResponses;
        private String tenantName;
        private String databaseName;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder tenantName(String tenantName) {
            this.tenantName = tenantName;
            return this;
        }

        public Builder databaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        public ChromaClientV2 build() {
            return new ChromaClientV2(this);
        }
    }

    public void createTenant() {
        try {
            Response<Void> response =
                    chromaApi.createTenant(new CreateTenantRequest(tenantName)).execute();
            if (!response.isSuccessful()) {
                throw toException(response);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Tenant tenant() {
        try {
            Response<Tenant> response = chromaApi.tenant(tenantName).execute();
            if (response.isSuccessful()) {
                return response.body();
            } else {
                // if collection is not present, Chroma returns: Status - 500
                return null;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void createDatabase() {
        try {
            Response<Void> response = chromaApi
                    .createDatabase(tenantName, new CreateDatabaseRequest(databaseName))
                    .execute();
            if (!response.isSuccessful()) {
                throw toException(response);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Database database() {
        try {
            Response<Database> response =
                    chromaApi.database(tenantName, databaseName).execute();
            if (response.isSuccessful()) {
                return response.body();
            } else {
                // if collection is not present, Chroma returns: Status - 500
                return null;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Collection createCollection(CreateCollectionRequest createCollectionRequest) {
        try {
            Response<Collection> response = chromaApi
                    .createCollection(tenantName, databaseName, createCollectionRequest)
                    .execute();
            if (response.isSuccessful()) {
                return response.body();
            } else {
                throw toException(response);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Collection collection(String collectionName) {
        try {
            Response<Collection> response = chromaApi
                    .collection(tenantName, databaseName, collectionName)
                    .execute();
            if (response.isSuccessful()) {
                return response.body();
            } else {
                // if collection is not present, Chroma returns: Status - 500
                return null;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean addEmbeddings(String collectionId, AddEmbeddingsRequest addEmbeddingsRequest) {
        try {
            Response<Void> retrofitResponse = chromaApi
                    .addEmbeddings(tenantName, databaseName, collectionId, addEmbeddingsRequest)
                    .execute();
            if (retrofitResponse.isSuccessful()) {
                return true;
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public QueryResponse queryCollection(String collectionId, QueryRequest queryRequest) {
        try {
            Response<QueryResponse> retrofitResponse = chromaApi
                    .queryCollection(tenantName, databaseName, collectionId, queryRequest)
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

    @Override
    public void deleteEmbeddings(String collectionId, DeleteEmbeddingsRequest deleteEmbeddingsRequest) {
        try {
            Response<List<String>> retrofitResponse = chromaApi
                    .deleteEmbeddings(tenantName, databaseName, collectionId, deleteEmbeddingsRequest)
                    .execute();
            if (!retrofitResponse.isSuccessful()) {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteCollection(String collectionName) {
        try {
            chromaApi.deleteCollection(tenantName, databaseName, collectionName).execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static RuntimeException toException(Response<?> response) throws IOException {
        int code = response.code();
        String body = response.errorBody().string();

        String errorMessage = String.format("status code: %s; body: %s", code, body);
        return new RuntimeException(errorMessage);
    }
}
