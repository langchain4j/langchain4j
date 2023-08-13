package dev.langchain4j.store.embedding.chroma;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;

import java.util.List;

interface ChromaApi {

    @GET("/api/v1/collections")
    @Headers({"Content-Type: application/json"})
    Call<List<CollectionCreationResponse>> getCollections();

    @GET("/api/v1/collections/{collection_name}")
    @Headers({"Content-Type: application/json"})
    Call<CollectionCreationResponse> getCollection(@Path("collection_name") String collectionName);

    @POST("/api/v1/collections")
    @Headers({"Content-Type: application/json"})
    Call<CollectionCreationResponse> createCollection(@Body CollectionCreationRequest collectionCreationRequest);

    @POST("api/v1/collections/{collection_id}/create_index")
    @Headers({"Content-Type: application/json"})
    Call<Boolean> createIndex(@Path("collection_id") String collectionId, @Body IndexCreationRequest indexCreationRequest);

    @POST("/api/v1/collections/{collection_id}/add")
    @Headers({"Content-Type: application/json"})
    Call<Boolean> addEmbeddingToCollection(@Path("collection_id") String collectionId, @Body EmbeddingsRequest embedding);

    @POST("/api/v1/collections/{collection_id}/upsert")
    @Headers({"Content-Type: application/json"})
    Call<Boolean> upsertEmbeddingToCollection(@Path("collection_id") String collectionId, @Body EmbeddingsRequest embedding);

    @POST("/api/v1/collections/{collection_id}/update")
    @Headers({"Content-Type: application/json"})
    Call<Void> updateEmbeddingInCollection(@Path("collection_id") String collectionId, @Body EmbeddingsRequest updateEmbedding);

    @POST("/api/v1/collections/{collection_id}/query")
    @Headers({"Content-Type: application/json"})
    Call<SuccessfulResponse> getNearestNeighbors(@Path("collection_id") String collectionId, @Body QueryRequest queryRequest);

}
