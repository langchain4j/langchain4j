package dev.langchain4j.model.dashscope.extension.aigc.multimodalconversation;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationUsage;
import com.alibaba.dashscope.common.DashScopeResult;
import com.alibaba.dashscope.utils.JsonUtils;
import com.google.gson.JsonObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class MultiModalConversationResult {
  private String requestId;
  private MultiModalConversationUsage usage;
  private MultiModalConversationOutput output;

  private MultiModalConversationResult() {}

  public static MultiModalConversationResult fromDashScopeResult(DashScopeResult dashScopeResult) {
    MultiModalConversationResult result = new MultiModalConversationResult();
    result.setRequestId(dashScopeResult.getRequestId());
    if (dashScopeResult.getUsage() != null) {
      result.setUsage(
          JsonUtils.fromJsonObject(
              dashScopeResult.getUsage().getAsJsonObject(), MultiModalConversationUsage.class));
    }
    if (dashScopeResult.getOutput() != null) {
      result.setOutput(
          JsonUtils.fromJsonObject(
              (JsonObject) dashScopeResult.getOutput(), MultiModalConversationOutput.class));
    } else {
      log.error(String.format("Result no output: %s", dashScopeResult));
    }
    return result;
  }
}
