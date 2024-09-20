package dev.langchain4j.model.anthropic.internal.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.anthropic.internal.api.AnthropicApi;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageResponse;
import dev.langchain4j.model.anthropic.internal.api.AnthropicToolResultContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicToolUseContent;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okhttp3.sse.EventSources;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

public class DefaultAnthropicClient extends AnthropicClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(INDENT_OUTPUT);

    private final AnthropicApi anthropicApi;
    private final OkHttpClient okHttpClient;

    private final String apiKey;
    private final String version;
    private final String beta;
    private final boolean logResponses;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AnthropicClient.Builder<DefaultAnthropicClient, Builder> {

        public DefaultAnthropicClient build() {
            return new DefaultAnthropicClient(this);
        }
    }

    DefaultAnthropicClient(Builder builder) {
        if (isNullOrBlank(builder.apiKey)) {
            throw new IllegalArgumentException("Anthropic API key must be defined. " +
                    "It can be generated here: https://console.anthropic.com/settings/keys");
        }

        this.apiKey = builder.apiKey;
        this.version = ensureNotBlank(builder.version, "version");
        this.beta = builder.beta;
        this.logResponses = builder.logResponses;

        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
                .callTimeout(builder.timeout)
                .connectTimeout(builder.timeout)
                .readTimeout(builder.timeout)
                .writeTimeout(builder.timeout);

        if (builder.logRequests) {
            okHttpClientBuilder.addInterceptor(new AnthropicRequestLoggingInterceptor());
        }
        if (logResponses) {
            okHttpClientBuilder.addInterceptor(new AnthropicResponseLoggingInterceptor());
        }

        this.okHttpClient = okHttpClientBuilder.build();


        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Utils.ensureTrailingForwardSlash(ensureNotBlank(builder.baseUrl, "baseUrl")))
                .client(okHttpClient)
                .addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
                .build();

        this.anthropicApi = retrofit.create(AnthropicApi.class);
    }

    @Override
    public AnthropicCreateMessageResponse createMessage(AnthropicCreateMessageRequest request) {
        try {
            retrofit2.Response<AnthropicCreateMessageResponse> retrofitResponse
                    = anthropicApi.createMessage(apiKey, version, toBeta(request), request).execute();
            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                try (ResponseBody errorBody = retrofitResponse.errorBody()) {
                    if (errorBody != null) {
                        throw new AnthropicHttpException(retrofitResponse.code(), errorBody.string());
                    }
                }
                throw new AnthropicHttpException(retrofitResponse.code(), null);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String toBeta(AnthropicCreateMessageRequest request) {
        return hasTools(request) ? beta : null;
    }

    private static boolean hasTools(AnthropicCreateMessageRequest request) {
        return !isNullOrEmpty(request.tools) || request.messages.stream()
                .flatMap(message -> message.content.stream())
                .anyMatch(content ->
                        (content instanceof AnthropicToolUseContent) || (content instanceof AnthropicToolResultContent));
    }

    @Override
    public void createMessage(AnthropicCreateMessageRequest request, StreamingResponseHandler<AiMessage> handler) {
        AnthropicEventSourceListener eventSourceListener = new AnthropicEventSourceListener(handler, logResponses);
        Call<ResponseBody> call = anthropicApi.streamMessage(apiKey, version, request);
        EventSources.createFactory(okHttpClient).newEventSource(call.request(), eventSourceListener);
    }
}
