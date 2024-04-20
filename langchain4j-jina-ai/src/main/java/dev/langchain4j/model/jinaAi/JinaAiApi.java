package dev.langchain4j.model.jinaAi;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface JinaAiApi {
    // TODO CHECK IF THIS IS REQ
    @POST("embedding/text")
    @Headers({"Content-Type: application/json"})
    Call<EmbeddingResponse> embed(@Body EmbeddingRequest request, @Header("Authorization") String authorizationHeader);

}
