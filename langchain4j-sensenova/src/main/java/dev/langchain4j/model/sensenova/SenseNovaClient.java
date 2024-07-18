package dev.langchain4j.model.sensenova;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.sensenova.chat.ChatCompletionChoice;
import dev.langchain4j.model.sensenova.chat.ChatCompletionRequest;
import dev.langchain4j.model.sensenova.chat.ChatCompletionResponse;
import dev.langchain4j.model.sensenova.chat.ToolCall;
import dev.langchain4j.model.sensenova.embedding.EmbeddingRequest;
import dev.langchain4j.model.sensenova.embedding.EmbeddingResponse;
import dev.langchain4j.model.sensenova.shared.Usage;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.sensenova.DefaultSenseNovaHelper.*;
import static dev.langchain4j.model.sensenova.Json.GSON;

public class SenseNovaClient {

	private static final Logger log = LoggerFactory.getLogger(SenseNovaClient.class);

	private final String baseUrl;
	private final SenseNovaApi senseNovaApi;
	private final OkHttpClient okHttpClient;
	private final Boolean logResponses;


	public SenseNovaClient(Builder builder) {
		this.baseUrl = builder.baseUrl;
		OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
				.callTimeout(builder.callTimeout)
				.connectTimeout(builder.connectTimeout)
				.readTimeout(builder.readTimeout)
				.writeTimeout(builder.writeTimeout)
				.addInterceptor(new AuthorizationInterceptor(builder.apiKeyId, builder.apiKeySecret));

		if (builder.logRequests) {
			okHttpClientBuilder.addInterceptor(new RequestLoggingInterceptor());
		}

		this.logResponses = builder.logResponses;
		if (builder.logResponses) {
			okHttpClientBuilder.addInterceptor(new ResponseLoggingInterceptor());
		}

		this.okHttpClient = okHttpClientBuilder.build();
		Retrofit retrofit = (new Retrofit.Builder())
				.baseUrl(formattedUrlForRetrofit(this.baseUrl))
				.client(this.okHttpClient)
				.addConverterFactory(GsonConverterFactory.create(GSON))
				.build();
		this.senseNovaApi = retrofit.create(SenseNovaApi.class);
	}


	private static String formattedUrlForRetrofit(String baseUrl) {
		return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
	}

	public static Builder builder() {
		return new Builder();
	}

	public ChatCompletionResponse chatCompletion(ChatCompletionRequest request) {
		try {
			retrofit2.Response<ChatCompletionResponse> retrofitResponse
					= senseNovaApi.chatCompletion(request).execute();
			if (retrofitResponse.isSuccessful()) {
				return retrofitResponse.body();
			} else {
				throw toException(retrofitResponse);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public EmbeddingResponse embedAll(EmbeddingRequest request) {
		try {
			retrofit2.Response<EmbeddingResponse> responseResponse = senseNovaApi.embeddings(request).execute();
			if (responseResponse.isSuccessful()) {
				return responseResponse.body();
			} else {
				throw toException(responseResponse);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	void streamingChatCompletion(ChatCompletionRequest request, StreamingResponseHandler<AiMessage> handler) {
		EventSourceListener eventSourceListener = new EventSourceListener() {
			final StringBuffer contentBuilder = new StringBuffer();
			List<ToolExecutionRequest> specifications;
			TokenUsage tokenUsage;
			FinishReason finishReason;

			@Override
			public void onOpen(@NotNull EventSource eventSource, @NotNull okhttp3.Response response) {
				if (logResponses) {
					log.debug("onOpen()");
				}
			}

			@Override
			public void onEvent(@NotNull EventSource eventSource, String id, String type, @NotNull String data) {
				if (logResponses) {
					log.debug("onEvent() {}", data);
				}
				if ("[DONE]".equals(data)) {
					AiMessage aiMessage;
					if (isNullOrEmpty(specifications)) {
						aiMessage = AiMessage.from(contentBuilder.toString());
					} else {
						aiMessage = AiMessage.from(specifications);
					}
					Response<AiMessage> response = Response.from(
							aiMessage,
							tokenUsage,
							finishReason
					);
					handler.onComplete(response);
				} else {
					try {
						ChatCompletionResponse chatCompletionResponse = Json.fromJson(data, ChatCompletionResponse.class);
						ChatCompletionChoice chatCompletionChoice = chatCompletionResponse.getData().getChoices().get(0);
						String chunk = chatCompletionChoice.getDelta();
						contentBuilder.append(chunk);
						handler.onNext(chunk);
						Usage usageInfo = chatCompletionResponse.getData().getUsage();
						if (usageInfo != null) {
							this.tokenUsage = tokenUsageFrom(usageInfo);
						}

						String finishReasonString = chatCompletionChoice.getFinishReason();
						if (finishReasonString != null) {
							this.finishReason = finishReasonFrom(finishReasonString);
						}

						// function
						List<ToolCall> toolCalls = chatCompletionChoice.getToolCalls();
						if (!isNullOrEmpty(toolCalls)) {
							this.specifications = specificationsFrom(toolCalls);
						}
					} catch (Exception e) {
						handler.onError(e);
						throw new RuntimeException(e);
					}
				}
			}

			@Override
			public void onFailure(@NotNull EventSource eventSource, Throwable t, okhttp3.Response response) {
				if (logResponses) {
					log.debug("onFailure()", t);
				}

				if (t != null) {
					handler.onError(t);
				} else {
					handler.onError(new RuntimeException(String.format("status code: %s; body: %s", response.code(), response.body())));
				}
			}

			@Override
			public void onClosed(@NotNull EventSource eventSource) {
				if (logResponses) {
					log.debug("onClosed()");
				}
			}
		};
		EventSources.createFactory(this.okHttpClient)
				.newEventSource(
						senseNovaApi.streamingChatCompletion(request).request(),
						eventSourceListener
				);
	}

	private RuntimeException toException(retrofit2.Response<?> retrofitResponse) throws IOException {
		int code = retrofitResponse.code();
		if (code >= 400) {
			ResponseBody errorBody = retrofitResponse.errorBody();
			if (errorBody != null) {
				String errorBodyString = errorBody.string();
				String errorMessage = String.format("status code: %s; body: %s", code, errorBodyString);
				log.error("Error response: {}", errorMessage);
				return new RuntimeException(errorMessage);
			}
		}
		return new RuntimeException(retrofitResponse.message());
	}

	public static class Builder {
		private String baseUrl;
		private String apiKeyId;
		private String apiKeySecret;
		private Duration callTimeout;
		private Duration connectTimeout;
		private Duration readTimeout;
		private Duration writeTimeout;
		private boolean logRequests;
		private boolean logResponses;

		private Builder() {
			this.baseUrl = "https://api.sensenova.cn/";
			this.callTimeout = Duration.ofSeconds(60L);
			this.connectTimeout = Duration.ofSeconds(60L);
			this.readTimeout = Duration.ofSeconds(60L);
			this.writeTimeout = Duration.ofSeconds(60L);
		}

		public Builder baseUrl(String baseUrl) {
			if (baseUrl != null && !baseUrl.trim().isEmpty()) {
				this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
				return this;
			} else {
				throw new IllegalArgumentException("baseUrl cannot be null or empty");
			}
		}

		public Builder apiKeyId(String apiKeyId) {
			if (apiKeyId != null && !apiKeyId.trim().isEmpty()) {
				this.apiKeyId = apiKeyId;
				return this;
			} else {
				throw new IllegalArgumentException("apiKeyId cannot be null or empty. ");
			}
		}

		public Builder apiKeySecret(String apiKeySecret) {
			if (apiKeySecret != null && !apiKeySecret.trim().isEmpty()) {
				this.apiKeySecret = apiKeySecret;
				return this;
			} else {
				throw new IllegalArgumentException("apiKeySecret cannot be null or empty. ");
			}
		}

		public Builder callTimeout(Duration callTimeout) {
			if (callTimeout == null) {
				throw new IllegalArgumentException("callTimeout cannot be null");
			} else {
				this.callTimeout = callTimeout;
				return this;
			}
		}

		public Builder connectTimeout(Duration connectTimeout) {
			if (connectTimeout == null) {
				throw new IllegalArgumentException("connectTimeout cannot be null");
			} else {
				this.connectTimeout = connectTimeout;
				return this;
			}
		}

		public Builder readTimeout(Duration readTimeout) {
			if (readTimeout == null) {
				throw new IllegalArgumentException("readTimeout cannot be null");
			} else {
				this.readTimeout = readTimeout;
				return this;
			}
		}

		public Builder writeTimeout(Duration writeTimeout) {
			if (writeTimeout == null) {
				throw new IllegalArgumentException("writeTimeout cannot be null");
			} else {
				this.writeTimeout = writeTimeout;
				return this;
			}
		}

		public Builder logRequests(Boolean logRequests) {
			if (logRequests == null) {
				logRequests = false;
			}

			this.logRequests = logRequests;
			return this;
		}

		public Builder logResponses(Boolean logResponses) {
			if (logResponses == null) {
				logResponses = false;
			}

			this.logResponses = logResponses;
			return this;
		}

		public SenseNovaClient build() {
			return new SenseNovaClient(this);
		}
	}
}
