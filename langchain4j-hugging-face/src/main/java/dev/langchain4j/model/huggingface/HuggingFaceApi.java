package dev.langchain4j.model.huggingface;

import dev.langchain4j.model.huggingface.client.EmbeddingRequest;
import dev.langchain4j.model.huggingface.client.TextGenerationRequest;
import dev.langchain4j.model.huggingface.client.TextGenerationResponse;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;

import java.util.List;

interface HuggingFaceApi {

    @POST("models/{modelId}")
    @Headers({"Content-Type: application/json"})
    Call<List<TextGenerationResponse>> generate(@Body TextGenerationRequest request, @Path("modelId") String modelId);

    @POST("pipeline/feature-extraction/{modelId}")
    @Headers({"Content-Type: application/json"})
    Call<List<float[]>> embed(@Body EmbeddingRequest request, @Path("modelId") String modelId);
}
