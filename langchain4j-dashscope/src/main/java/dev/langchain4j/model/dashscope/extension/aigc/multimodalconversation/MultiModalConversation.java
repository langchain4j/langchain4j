package dev.langchain4j.model.dashscope.extension.aigc.multimodalconversation;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationMessage;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.api.SynchronizeHalfDuplexApi;
import com.alibaba.dashscope.common.*;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.protocol.*;
import com.alibaba.dashscope.utils.PreprocessMessageInput;
import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class MultiModalConversation {
  /* Auto history messages */
  private final SynchronizeHalfDuplexApi<MultiModalConversationParam> syncApi;
  private final ApiServiceOption serviceOption;

  public static class Models {
    public static final String QWEN_VL_CHAT_V1 = "qwen-vl-chat-v1";
    public static final String QWEN_VL_PLUS = "qwen-vl-plus";
  }

  private ApiServiceOption defaultApiServiceOption() {
    return ApiServiceOption.builder()
        .protocol(Protocol.HTTP)
        .httpMethod(HttpMethod.POST)
        .streamingMode(StreamingMode.NONE)
        .outputMode(OutputMode.ACCUMULATE)
        .taskGroup(TaskGroup.AIGC.getValue())
        .task(Task.MULTIMODAL_GENERATION.getValue())
        .function(Function.GENERATION.getValue())
        .build();
  }

  public MultiModalConversation() {
    serviceOption = defaultApiServiceOption();
    syncApi = new SynchronizeHalfDuplexApi<>(serviceOption);
  }

  public MultiModalConversation(String protocol) {
    serviceOption = defaultApiServiceOption();
    serviceOption.setProtocol(Protocol.of(protocol));
    syncApi =
        new SynchronizeHalfDuplexApi<>(
            ClientOptions.builder().protocol(protocol).build(), serviceOption);
  }

  public MultiModalConversation(String protocol, String baseUrl) {
    serviceOption = defaultApiServiceOption();
    serviceOption.setProtocol(Protocol.of(protocol));
    if (Protocol.HTTP.getValue().equals(protocol)) {
      serviceOption.setBaseHttpUrl(baseUrl);
    } else {
      serviceOption.setBaseWebSocketUrl(baseUrl);
    }
    syncApi =
        new SynchronizeHalfDuplexApi<>(
            ClientOptions.builder().protocol(protocol).build(), serviceOption);
  }
  /**
   * Call the server to get the whole result.
   *
   * @param param The input param of class `MultiModalConversationParam`.
   * @return The output structure of `MultiModalConversationResult`.
   * @throws NoApiKeyException Can not find api key
   * @throws UploadFileException
   */
  public MultiModalConversationResult call(MultiModalConversationParam param)
      throws ApiException, NoApiKeyException, UploadFileException {
    serviceOption.setIsSSE(false);
    serviceOption.setStreamingMode(StreamingMode.NONE);
    preprocessInput(param);
    return MultiModalConversationResult.fromDashScopeResult(syncApi.call(param));
  }

  /**
   * Call the server to get the result in the callback function.
   *
   * @param param The input param of class `MultiModalConversationParam`.
   * @param callback The callback to receive response, the template class is
   *     `MultiModalConversationResult`.
   * @throws NoApiKeyException Can not find api key
   * @throws ApiException The request failed, possibly due to a network or data error.
   * @throws UploadFileException
   */
  public void call(
      MultiModalConversationParam param, ResultCallback<com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult> callback)
      throws ApiException, NoApiKeyException, UploadFileException {
    serviceOption.setIsSSE(false);
    serviceOption.setStreamingMode(StreamingMode.NONE);
    preprocessInput(param);
    syncApi.call(
        param,
        new ResultCallback<DashScopeResult>() {
          @Override
          public void onEvent(DashScopeResult message) {
            callback.onEvent(com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult.fromDashScopeResult(message));
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
   * Call the server to get the result by stream.
   *
   * @param param The input param of class `MultiModalConversationParam`.
   * @return A `Flowable` of the output structure.
   * @throws NoApiKeyException Can not find api key
   * @throws ApiException The request failed, possibly due to a network or data error.
   * @throws UploadFileException
   */
  public Flowable<com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult> streamCall(MultiModalConversationParam param)
      throws ApiException, NoApiKeyException, UploadFileException {
    serviceOption.setIsSSE(true);
    serviceOption.setStreamingMode(StreamingMode.OUT);
    preprocessInput(param);
    return syncApi
        .streamCall(param)
        .map(item -> com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult.fromDashScopeResult(item));
  }

  /**
   * Call the server to get the result by stream.
   *
   * @param param The input param of class `MultiModalConversationParam`.
   * @param callback The result callback.
   * @throws NoApiKeyException Can not find api key
   * @throws ApiException The request failed, possibly due to a network or data error.
   * @throws InputRequiredException The input field is missing.
   * @throws UploadFileException
   */
  public void streamCall(
      MultiModalConversationParam param, ResultCallback<MultiModalConversationResult> callback)
      throws ApiException, NoApiKeyException, InputRequiredException, UploadFileException {
    param.validate();
    serviceOption.setIsSSE(true);
    serviceOption.setStreamingMode(StreamingMode.OUT);
    preprocessInput(param);
    syncApi.streamCall(
        param,
        new ResultCallback<DashScopeResult>() {
          @Override
          public void onEvent(DashScopeResult msg) {
            callback.onEvent(MultiModalConversationResult.fromDashScopeResult(msg));
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

  private void preprocessInput(MultiModalConversationParam param)
      throws NoApiKeyException, UploadFileException {
    boolean hasUpload = false;
    for (Object msg : param.getMessages()) {
      boolean isUpload = false;
      if (msg instanceof MultiModalConversationMessage) {
        isUpload =
            PreprocessMessageInput.preProcessMessageInputs(
                param.getModel(),
                ((MultiModalConversationMessage) msg).getContent(),
                param.getApiKey());

      } else {
        isUpload =
            PreprocessMessageInput.preProcessMultiModalMessageInputs(
                param.getModel(), (MultiModalMessage) msg, param.getApiKey());
      }
      if (isUpload && !hasUpload) {
        hasUpload = true;
      }
    }
    if (hasUpload) {
      param.putHeader("X-DashScope-OssResourceResolve", "enable");
    }
  }
}
