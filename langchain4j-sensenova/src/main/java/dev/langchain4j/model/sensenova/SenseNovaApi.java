package dev.langchain4j.model.sensenova;

import dev.langchain4j.model.sensenova.chat.ChatCompletionRequest;
import dev.langchain4j.model.sensenova.chat.ChatCompletionResponse;
import dev.langchain4j.model.sensenova.embedding.EmbeddingRequest;
import dev.langchain4j.model.sensenova.embedding.EmbeddingResponse;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Streaming;

interface SenseNovaApi {

	@POST("v1/llm/chat-completions")
	@Headers({"Content-Type: application/json"})
	Call<ChatCompletionResponse> chatCompletion(@Body ChatCompletionRequest request);

	@Streaming
	@POST("v1/llm/chat-completions")
	@Headers({"Content-Type: application/json"})
	Call<ResponseBody> streamingChatCompletion(@Body ChatCompletionRequest request);

	@POST("v1/llm/embeddings")
	@Headers({"Content-Type: application/json"})
	Call<EmbeddingResponse> embeddings(@Body EmbeddingRequest request);

}
