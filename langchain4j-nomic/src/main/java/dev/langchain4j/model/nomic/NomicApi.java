package dev.langchain4j.model.nomic;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

interface NomicApi {

    @POST("embedding/text")
    @Headers({"Content-Type: application/json"})
    Call<EmbeddingResponse> embed(@Body EmbeddingRequest request, @Header("Authorization") String authorizationHeader);
}
