package dev.langchain4j.store.embedding.chroma;

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

class ChromaClient {

    private final ChromaApi chromaApi;

    private ChromaClient(Builder builder) {
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

        this.chromaApi = retrofit.create(ChromaApi.class);
    }

    public static class Builder {

        private String baseUrl;
        private Duration timeout;
        private boolean logRequests;
        private boolean logResponses;

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

        public ChromaClient build() {
            return new ChromaClient(this);
        }
    }

    /**
     * @Deprecated since API V2
     * Please use {@link #createCollection(String, String, CreateCollectionRequest)}
     * with specific tenant & database.
     */
    @Deprecated
    Collection createCollection(CreateCollectionRequest createCollectionRequest) {
        return createCollection("default_tenant", "default_database", createCollectionRequest);
    }

    /**
     * API V2 create collection
     * @param tenantName   tenant name
     * @param databaseName database name
     * @param createCollectionRequest  request (collection name & metadata)
     * @return newly created collection object
     */
    Collection createCollection(
            String tenantName, String databaseName, CreateCollectionRequest createCollectionRequest) {
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

    /**
     * @Deprecated since API V2
     * Please use {@link #collection(String, String, String)}
     * with specific tenant & database.
     */
    @Deprecated
    Collection collection(String collectionName) {
        return collection("default_tenant", "default_database", collectionName);
    }

    /**
     * API V2 get collection
     * @param tenantName   tenant name
     * @param databaseName database name
     * @param collectionName  collection name
     * @return Collection object
     */
    Collection collection(String tenantName, String databaseName, String collectionName) {
        try {
            Response<List<Collection>> response =
                    chromaApi.collections(tenantName, databaseName).execute();
            if (response.isSuccessful()) {
                List<Collection> collections = response.body();
                for (Collection collection : collections) {
                    if (collection.getName().equals(collectionName)) {
                        return collection;
                    }
                }
                return null;
            } else {
                // if collection is not present, Chroma returns: Status - 500
                return null;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @Deprecated since API V2
     * Please use {@link #addEmbeddings(String, String, String, AddEmbeddingsRequest)}
     * with specific tenant & database.
     */
    @Deprecated
    boolean addEmbeddings(String collectionId, AddEmbeddingsRequest addEmbeddingsRequest) {
        return addEmbeddings("default_tenant", "default_database", collectionId, addEmbeddingsRequest);
    }

    /**
     * API V2 add records to a collection
     * @param tenantName   tenant name
     * @param databaseName database name
     * @param collectionId  collection id
     * @param addEmbeddingsRequest  request (embedding ids, vectors, metadatas, documents)
     * @return true if successful
     */
    boolean addEmbeddings(
            String tenantName, String databaseName, String collectionId, AddEmbeddingsRequest addEmbeddingsRequest) {
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

    /**
     * @Deprecated since API V2
     * Please use {@link #queryCollection(String, String, String, QueryRequest)}
     * with specific tenant & database.
     */
    @Deprecated
    QueryResponse queryCollection(String collectionId, QueryRequest queryRequest) {
        return queryCollection("default_tenant", "default_database", collectionId, queryRequest);
    }

    /**
     * API V2 query a collection
     * @param tenantName   tenant name
     * @param databaseName database name
     * @param collectionId  collection id
     * @param queryRequest  request (query embedding, filter, limit, n_results)
     * @return QueryResponse result (ids, distances, metadatas, documents)
     */
    QueryResponse queryCollection(
            String tenantName, String databaseName, String collectionId, QueryRequest queryRequest) {
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

    /**
     * @Deprecated since API V2
     * Please use {@link #deleteEmbeddings(String, String, String, DeleteEmbeddingsRequest)}
     * with specific tenant & database.
     */
    @Deprecated
    void deleteEmbeddings(String collectionId, DeleteEmbeddingsRequest deleteEmbeddingsRequest) {
        deleteEmbeddings("default_tenant", "default_database", collectionId, deleteEmbeddingsRequest);
    }

    /**
     * API V2 delete records from a collection
     * @param tenantName   tenant name
     * @param databaseName database name
     * @param collectionId  collection id
     * @param deleteEmbeddingsRequest  request (embedding ids)
     */
    void deleteEmbeddings(
            String tenantName,
            String databaseName,
            String collectionId,
            DeleteEmbeddingsRequest deleteEmbeddingsRequest) {
        try {
            Response<Void> retrofitResponse = chromaApi
                    .deleteEmbeddings(tenantName, databaseName, collectionId, deleteEmbeddingsRequest)
                    .execute();
            if (!retrofitResponse.isSuccessful()) {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @Deprecated since API V2
     * Please use {@link #deleteCollection(String, String, String)}
     * with specific tenant & database.
     */
    @Deprecated
    void deleteCollection(String collectionName) {
        deleteCollection("default_tenant", "default_database", collectionName);
    }

    /**
     * API V2 delete a collection
     * @param tenantName   tenant name
     * @param databaseName database name
     * @param collectionName  collection name (it's id in API V2's docs)
     */
    void deleteCollection(String tenantName, String databaseName, String collectionName) {
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
