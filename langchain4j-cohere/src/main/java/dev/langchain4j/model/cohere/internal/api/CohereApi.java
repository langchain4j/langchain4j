package dev.langchain4j.model.cohere.internal.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface CohereApi {

    @POST("chat")
    @Headers({"Content-Type: application/json"})
    Call<CohereChatResponse> chat(@Body CohereChatRequest request, @Header("Authorization") String authorizationHeader);

    @POST("rerank")
    @Headers({"Content-Type: application/json"})
    Call<RerankResponse> rerank(@Body RerankRequest request, @Header("Authorization") String authorizationHeader);
}
