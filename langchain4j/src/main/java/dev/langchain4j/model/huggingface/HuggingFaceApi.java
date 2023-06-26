package dev.langchain4j.model.huggingface;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

import java.util.List;

interface HuggingFaceApi {

    @POST(".")
    @Headers({"Content-Type: application/json"})
    Call<List<float[]>> embed(@Body EmbeddingRequest request);
}
