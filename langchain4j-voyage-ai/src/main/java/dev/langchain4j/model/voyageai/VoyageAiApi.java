package dev.langchain4j.model.voyageai;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

interface VoyageAiApi {

    String DEFAULT_BASE_URL = "https://api.voyageai.com/v1/";

    @POST("embeddings")
    @Headers({"accept: application/json", "content-type: application/json"})
    Call<EmbeddingResponse> embed(@Body EmbeddingRequest request);

    @POST("rerank")
    @Headers({"accept: application/json", "content-type: application/json"})
    Call<RerankResponse> rerank(@Body RerankRequest request);
}
