package dev.langchain4j.store.embedding.chroma;

import retrofit2.Call;
import retrofit2.http.*;

interface ChromaApi {

    @GET("/api/v1/collections/{collection_name}")
    @Headers({"Content-Type: application/json"})
    Call<Collection> collection(@Path("collection_name") String collectionName);

    @POST("/api/v1/collections")
    @Headers({"Content-Type: application/json"})
    Call<Collection> createCollection(@Body CreateCollectionRequest createCollectionRequest);

    @POST("/api/v1/collections/{collection_id}/add")
    @Headers({"Content-Type: application/json"})
    Call<Boolean> addEmbeddings(@Path("collection_id") String collectionId, @Body AddEmbeddingsRequest embedding);

    @POST("/api/v1/collections/{collection_id}/query")
    @Headers({"Content-Type: application/json"})
    Call<QueryResponse> queryCollection(@Path("collection_id") String collectionId, @Body QueryRequest queryRequest);
}
