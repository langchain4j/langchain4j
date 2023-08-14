package dev.langchain4j.store.embedding.chroma;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;

interface ChromaApi {

    @GET("/api/v1/collections/{collection_name}")
    @Headers({"Content-Type: application/json"})
    Call<CollectionCreationResponse> getCollection(@Path("collection_name") String collectionName);

    @POST("/api/v1/collections")
    @Headers({"Content-Type: application/json"})
    Call<CollectionCreationResponse> createCollection(@Body CollectionCreationRequest collectionCreationRequest);

    @POST("/api/v1/collections/{collection_id}/add")
    @Headers({"Content-Type: application/json"})
    Call<Boolean> addEmbeddingToCollection(@Path("collection_id") String collectionId, @Body EmbeddingsRequest embedding);

    @POST("/api/v1/collections/{collection_id}/query")
    @Headers({"Content-Type: application/json"})
    Call<SuccessfulResponse> getNearestNeighbors(@Path("collection_id") String collectionId, @Body QueryRequest queryRequest);

}
