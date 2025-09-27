package dev.langchain4j.store.embedding.chroma;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;

interface ChromaApi {

    @Deprecated
    @GET("api/v1/collections/{collection_name}")
    @Headers({"Content-Type: application/json"})
    Call<Collection> collection(@Path("collection_name") String collectionName);

    @GET("api/v2/tenants/{tenant}/databases/{database}/collections")
    @Headers({"Content-Type: application/json"})
    Call<List<Collection>> collections(@Path("tenant") String tenant, @Path("database") String database);

    @Deprecated
    @POST("api/v1/collections")
    @Headers({"Content-Type: application/json"})
    Call<Collection> createCollection(@Body CreateCollectionRequest createCollectionRequest);

    @POST("api/v2/tenants/{tenant}/databases/{database}/collections")
    @Headers({"Content-Type: application/json"})
    Call<Collection> createCollection(
            @Path("tenant") String tenant,
            @Path("database") String database,
            @Body CreateCollectionRequest createCollectionRequest);

    @Deprecated
    @POST("api/v1/collections/{collection_id}/add")
    @Headers({"Content-Type: application/json"})
    Call<Boolean> addEmbeddings(@Path("collection_id") String collectionId, @Body AddEmbeddingsRequest embedding);

    // Update: v2 embedding must has consistent number of IDs, embeddings, documents, URIs and metadatas
    @POST("api/v2/tenants/{tenant}/databases/{database}/collections/{collection_id}/add")
    @Headers({"Content-Type: application/json"})
    Call<Void> addEmbeddings(
            @Path("tenant") String tenant,
            @Path("database") String database,
            @Path("collection_id") String collectionId,
            @Body AddEmbeddingsRequest embedding);

    @Deprecated
    @POST("api/v1/collections/{collection_id}/query")
    @Headers({"Content-Type: application/json"})
    Call<QueryResponse> queryCollection(@Path("collection_id") String collectionId, @Body QueryRequest queryRequest);

    @POST("api/v2/tenants/{tenant}/databases/{database}/collections/{collection_id}/query")
    @Headers({"Content-Type: application/json"})
    Call<QueryResponse> queryCollection(
            @Path("tenant") String tenant,
            @Path("database") String database,
            @Path("collection_id") String collectionId,
            @Body QueryRequest queryRequest);

    @Deprecated
    @POST("api/v1/collections/{collection_id}/delete")
    @Headers({"Content-Type: application/json"})
    Call<List<String>> deleteEmbeddings(
            @Path("collection_id") String collectionId, @Body DeleteEmbeddingsRequest embedding);

    @POST("api/v2/tenants/{tenant}/databases/{database}/collections/{collection_id}/delete")
    @Headers({"Content-Type: application/json"})
    Call<Void> deleteEmbeddings(
            @Path("tenant") String tenant,
            @Path("database") String database,
            @Path("collection_id") String collectionId,
            @Body DeleteEmbeddingsRequest embedding);

    @Deprecated
    @DELETE("api/v1/collections/{collection_name}")
    @Headers({"Content-Type: application/json"})
    Call<Collection> deleteCollection(@Path("collection_name") String collectionName);

    // in docs, it's delete collection by id, but it's delete collection by name in fact
    @DELETE("api/v2/tenants/{tenant}/databases/{database}/collections/{collection_id}")
    @Headers({"Content-Type: application/json"})
    Call<Void> deleteCollection(
            @Path("tenant") String tenant,
            @Path("database") String database,
            @Path("collection_id") String collectionName);
}
