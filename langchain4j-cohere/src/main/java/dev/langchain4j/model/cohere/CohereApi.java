package dev.langchain4j.model.cohere;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

interface CohereApi {

    @POST("embed")
    @Headers({"accept: application/json", "content-type: application/json"})
    Call<EmbedResponse> embed(@Body EmbedRequest request, @Header("Authorization") String authorizationHeader);

    @POST("rerank")
    @Headers({"accept: application/json", "content-type: application/json"})
    Call<RerankResponse> rerank(@Body RerankRequest request, @Header("Authorization") String authorizationHeader);
}
