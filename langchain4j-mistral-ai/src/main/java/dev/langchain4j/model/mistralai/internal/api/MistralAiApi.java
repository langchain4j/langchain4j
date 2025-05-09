package dev.langchain4j.model.mistralai.internal.api;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface MistralAiApi {

    @POST("chat/completions")
    @Headers({"Content-Type: application/json", "Accept: application/json", "User-Agent: langchain4j-mistralai"})
    Call<MistralAiChatCompletionResponse> chatCompletion(@Body MistralAiChatCompletionRequest request);

    @POST("chat/completions")
    @Headers({"Content-Type: application/json", "Accept: text/event-stream", "User-Agent: langchain4j-mistralai"})
    @Streaming
    Call<ResponseBody> streamingChatCompletion(@Body MistralAiChatCompletionRequest request);

    @POST("embeddings")
    @Headers({"Content-Type: application/json", "Accept: application/json", "User-Agent: langchain4j-mistralai"})
    Call<MistralAiEmbeddingResponse> embedding(@Body MistralAiEmbeddingRequest request);

    @POST("moderations")
    @Headers({"Content-Type: application/json", "Accept: application/json", "User-Agent: langchain4j-mistralai"})
    Call<MistralAiModerationResponse> moderations(@Body MistralAiModerationRequest request);

    @GET("models")
    @Headers({"Content-Type: application/json", "Accept: application/json", "User-Agent: langchain4j-mistralai"})
    Call<MistralAiModelResponse> models();

    @POST("fim/completions")
    @Headers({"Content-Type: application/json", "Accept: application/json", "User-Agent: langchain4j-mistralai"})
    Call<MistralAiChatCompletionResponse> fimCompletion(@Body MistralAiFimCompletionRequest request);

    @POST("fim/completions")
    @Headers({"Content-Type: application/json", "Accept: text/event-stream", "User-Agent: langchain4j-mistralai"})
    @Streaming
    Call<ResponseBody> streamingFimCompletion(@Body MistralAiFimCompletionRequest request);
}
