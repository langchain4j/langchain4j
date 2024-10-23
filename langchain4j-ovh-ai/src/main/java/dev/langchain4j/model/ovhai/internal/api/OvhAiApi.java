package dev.langchain4j.model.ovhai.internal.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

import java.util.List;

public interface OvhAiApi {

  @POST("api/batch_text2vec")
  @Headers({"Content-Type: application/json"})
  Call<List<float[]>> embed(@Body EmbeddingRequest request, @Header("Authorization") String authorizationHeader);
}
