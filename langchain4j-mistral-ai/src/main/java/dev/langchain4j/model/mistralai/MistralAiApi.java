package dev.langchain4j.model.mistralai;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

interface MistralAiApi {

    @POST("chat/completions")
    @Headers({"Content-Type: application/json"})
    Call<MistralChatCompletionResponse> chatCompletion(@Body MistralChatCompletionRequest request);

    @POST("chat/completions")
    @Headers({"Content-Type: application/json"})
    @Streaming
    Call<ResponseBody> streamingChatCompletion(@Body MistralChatCompletionRequest request);

    @POST("embeddings")
    @Headers({"Content-Type: application/json"})
    Call<MistralEmbeddingResponse> embedding(@Body MistralEmbeddingRequest request);

    @GET("models")
    @Headers({"Content-Type: application/json"})
    Call<MistralModelResponse> models();

}
