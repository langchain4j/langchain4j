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

    @GET("api/v1/collections/{collection_name}")
    @Headers({"Content-Type: application/json"})
    Call<Collection> collection(@Path("collection_name") String collectionName);

    @POST("api/v1/collections")
    @Headers({"Content-Type: application/json"})
    Call<Collection> createCollection(@Body CreateCollectionRequest createCollectionRequest);

    @POST("api/v1/collections/{collection_id}/add")
    @Headers({"Content-Type: application/json"})
    Call<Boolean> addEmbeddings(@Path("collection_id") String collectionId, @Body AddEmbeddingsRequest embedding);

    @POST("api/v1/collections/{collection_id}/query")
    @Headers({"Content-Type: application/json"})
    Call<QueryResponse> queryCollection(@Path("collection_id") String collectionId, @Body QueryRequest queryRequest);

    @POST("api/v1/collections/{collection_id}/delete")
    @Headers({"Content-Type: application/json"})
    Call<List<String>> deleteEmbeddings(
            @Path("collection_id") String collectionId,
            @Body DeleteEmbeddingsRequest embedding
    );

    @DELETE("api/v1/collections/{collection_name}")
    @Headers({"Content-Type: application/json"})
    Call<Collection> deleteCollection(@Path("collection_name") String collectionName);
}
