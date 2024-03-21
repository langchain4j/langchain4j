package dev.langchain4j.model.dashscope.extension.aigc.generation;

import com.alibaba.dashscope.api.SynchronizeHalfDuplexApi;
import com.alibaba.dashscope.base.HalfDuplexServiceParam;
import com.alibaba.dashscope.common.*;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.protocol.*;
import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class Generation {
  private final SynchronizeHalfDuplexApi<HalfDuplexServiceParam> syncApi;
  private final ApiServiceOption serviceOption;

  public static class Models {
    /** @deprecated use QWEN_TURBO instead */
    @Deprecated public static final String QWEN_V1 = "qwen-v1";

    public static final String QWEN_TURBO = "qwen-turbo";

    public static final String BAILIAN_V1 = "bailian-v1";
    public static final String DOLLY_12B_V2 = "dolly-12b-v2";

    /** @deprecated use QWEN_PLUS instead */
    @Deprecated public static final String QWEN_PLUS_V1 = "qwen-plus-v1";

    public static final String QWEN_PLUS = "qwen-plus";
    public static final String QWEN_MAX = "qwen-max";
  }

  private ApiServiceOption defaultApiServiceOption() {
    return ApiServiceOption.builder()
        .protocol(Protocol.HTTP)
        .httpMethod(HttpMethod.POST)
        .streamingMode(StreamingMode.OUT)
        .outputMode(OutputMode.ACCUMULATE)
        .taskGroup(TaskGroup.AIGC.getValue())
        .task(Task.TEXT_GENERATION.getValue())
        .function(Function.GENERATION.getValue())
        .build();
  }

  public Generation() {
    serviceOption = defaultApiServiceOption();
    syncApi = new SynchronizeHalfDuplexApi<>(serviceOption);
  }

  public Generation(String protocol) {
    serviceOption = defaultApiServiceOption();
    serviceOption.setProtocol(Protocol.of(protocol));
    syncApi =
        new SynchronizeHalfDuplexApi<>(
            ClientOptions.builder().protocol(protocol).build(), serviceOption);
  }

  public Generation(String protocol, String baseUrl) {
    serviceOption = defaultApiServiceOption();
    serviceOption.setProtocol(Protocol.of(protocol));
    if (protocol.equals(Protocol.HTTP.getValue())) {
      serviceOption.setBaseHttpUrl(baseUrl);
    } else {
      serviceOption.setBaseWebSocketUrl(baseUrl);
    }
    syncApi =
        new SynchronizeHalfDuplexApi<>(
            ClientOptions.builder().protocol(protocol).build(), serviceOption);
  }

  /**
   * Call the server to get the whole result, only http protocol
   *
   * @param param The input param of class `ConversationParam`.
   * @return The output structure of `QWenConversationResult`.
   * @throws NoApiKeyException Can not find api key
   * @throws InputRequiredException Missing inputs.
   */
  public GenerationResult call(HalfDuplexServiceParam param)
      throws ApiException, NoApiKeyException, InputRequiredException {
    param.validate();
    serviceOption.setIsSSE(false);
    serviceOption.setStreamingMode(StreamingMode.NONE);
    return GenerationResult.fromDashScopeResult(syncApi.call(param));
  }

  /**
   * Call the server to get the result in the callback function.
   *
   * @param param The input param of class `GenerationParam`.
   * @param callback The callback to receive response, the template class is `GenerationResult`.
   * @throws NoApiKeyException Can not find api key
   * @throws ApiException The request failed, possibly due to a network or data error.
   * @throws InputRequiredException Missing inputs.
   */
  public void call(HalfDuplexServiceParam param, ResultCallback<GenerationResult> callback)
      throws ApiException, NoApiKeyException, InputRequiredException {
    param.validate();
    serviceOption.setIsSSE(false);
    serviceOption.setStreamingMode(StreamingMode.NONE);
    syncApi.call(
        param,
        new ResultCallback<DashScopeResult>() {
          @Override
          public void onEvent(DashScopeResult message) {
            callback.onEvent(GenerationResult.fromDashScopeResult(message));
          }

          @Override
          public void onComplete() {
            callback.onComplete();
          }

          @Override
          public void onError(Exception e) {
            callback.onError(e);
          }
        });
  }

  /**
   * Call the server to get the result by stream. http and websocket.
   *
   * @param param The input param of class `ConversationParam`.
   * @return A `Flowable` of the output structure.
   * @throws NoApiKeyException Can not find api key
   * @throws ApiException The request failed, possibly due to a network or data error.
   * @throws InputRequiredException Missing inputs.
   */
  public Flowable<GenerationResult> streamCall(HalfDuplexServiceParam param)
      throws ApiException, NoApiKeyException, InputRequiredException {
    param.validate();
    serviceOption.setIsSSE(true);
    serviceOption.setStreamingMode(StreamingMode.OUT);
    return syncApi.streamCall(param).map(item -> GenerationResult.fromDashScopeResult(item));
  }

  public void streamCall(HalfDuplexServiceParam param, ResultCallback<GenerationResult> callback)
      throws ApiException, NoApiKeyException, InputRequiredException {
    param.validate();
    serviceOption.setIsSSE(true);
    serviceOption.setStreamingMode(StreamingMode.OUT);
    syncApi.streamCall(
        param,
        new ResultCallback<DashScopeResult>() {
          @Override
          public void onEvent(DashScopeResult msg) {
            callback.onEvent(GenerationResult.fromDashScopeResult(msg));
          }

          @Override
          public void onComplete() {
            callback.onComplete();
          }

          @Override
          public void onError(Exception e) {
            callback.onError(e);
          }
        });
  }
}
