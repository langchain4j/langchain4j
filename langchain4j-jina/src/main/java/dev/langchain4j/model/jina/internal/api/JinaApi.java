package dev.langchain4j.model.jina.internal.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface JinaApi {

    @POST("v1/embeddings")
    @Headers({"Content-Type: application/json"})
    Call<JinaEmbeddingResponse> embed(@Body JinaEmbeddingRequest request,
                                      @Header("Authorization") String authorizationHeader);

    @POST("rerank")
    @Headers({"Content-Type: application/json"})
    Call<JinaRerankingResponse> rerank(@Body JinaRerankingRequest request,
                                       @Header("Authorization") String authorizationHeader);
}
