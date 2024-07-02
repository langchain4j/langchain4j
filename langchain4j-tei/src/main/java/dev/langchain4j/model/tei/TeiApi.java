package dev.langchain4j.model.tei;

import dev.langchain4j.model.tei.client.EmbeddingRequest;
import dev.langchain4j.model.tei.client.EmbeddingResponse;
import dev.langchain4j.model.tei.client.ReRankResult;
import dev.langchain4j.model.tei.client.RerankRequest;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

import java.util.List;

public interface TeiApi {

    @POST("/embeddings")
    @Headers({"Content-Type: application/json"})
    Call<EmbeddingResponse> embedding(@Body EmbeddingRequest request);

    @POST("/rerank")
    @Headers({"Content-Type: application/json"})
    Call<List<ReRankResult>> rerank(@Body RerankRequest chatRequest);

}
