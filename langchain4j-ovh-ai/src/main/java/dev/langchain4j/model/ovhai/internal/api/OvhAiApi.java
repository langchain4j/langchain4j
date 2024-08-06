package dev.langchain4j.model.ovhai.internal.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface OvhAiApi {

  @POST("api/text2vec")
  @Headers({"Content-Type: application/json"})
  Call<float[]> embed(@Body EmbeddingRequest request, @Header("Authorization") String authorizationHeader);
}
