package dev.langchain4j.image.openai.dalle;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface OpenAiDalleApi {
  @Headers({ "Content-Type: application/json" })
  @POST("images/generations")
  Call<OpenAiDalleResponse> generateImage(@Body OpenAiDalleRequest request);
}
