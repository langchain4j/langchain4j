package dev.langchain4j.model.mistralai.internal.api;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface MistralAiApi {

    @POST("chat/completions")
    @Headers({"Content-Type: application/json"})
    Call<MistralAiChatCompletionResponse> chatCompletion(@Body MistralAiChatCompletionRequest request);

    @POST("chat/completions")
    @Headers({"Content-Type: application/json"})
    @Streaming
    Call<ResponseBody> streamingChatCompletion(@Body MistralAiChatCompletionRequest request);

    @POST("embeddings")
    @Headers({"Content-Type: application/json"})
    Call<MistralAiEmbeddingResponse> embedding(@Body MistralAiEmbeddingRequest request);

    @GET("models")
    @Headers({"Content-Type: application/json"})
    Call<MistralAiModelResponse> models();
}
