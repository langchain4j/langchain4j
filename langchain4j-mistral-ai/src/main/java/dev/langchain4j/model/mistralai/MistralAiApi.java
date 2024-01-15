package dev.langchain4j.model.mistralai;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

interface MistralAiApi {

    @POST("chat/completions")
    @Headers({"Content-Type: application/json"})
    Call<ChatCompletionResponse> chatCompletion(@Body  ChatCompletionRequest request);

    @POST("chat/completions")
    @Headers({"Content-Type: application/json"})
    @Streaming
    Call<ResponseBody> streamingChatCompletion(@Body  ChatCompletionRequest request);

    @POST("embeddings")
    @Headers({"Content-Type: application/json"})
    Call<EmbeddingResponse> embedding(@Body EmbeddingRequest request);

    @GET("models")
    @Headers({"Content-Type: application/json"})
    Call<ModelResponse> models();

}
