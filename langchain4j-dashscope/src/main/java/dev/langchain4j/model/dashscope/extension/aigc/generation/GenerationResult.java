package dev.langchain4j.model.dashscope.extension.aigc.generation;

import com.alibaba.dashscope.aigc.generation.GenerationUsage;
import com.alibaba.dashscope.common.DashScopeResult;
import com.alibaba.dashscope.utils.JsonUtils;
import com.google.gson.JsonObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public final class GenerationResult {
  private String requestId;
  private GenerationUsage usage;
  private GenerationOutput output;

  private GenerationResult() {}

  public static GenerationResult fromDashScopeResult(DashScopeResult dashScopeResult) {
    GenerationResult result = new GenerationResult();
    result.setRequestId(dashScopeResult.getRequestId());
    if (dashScopeResult.getUsage() != null) {
      result.setUsage(
          JsonUtils.fromJsonObject(
              dashScopeResult.getUsage().getAsJsonObject(), GenerationUsage.class));
    }
    if (dashScopeResult.getOutput() != null) {
      result.setOutput(
          JsonUtils.fromJsonObject(
              (JsonObject) dashScopeResult.getOutput(), GenerationOutput.class));
    } else {
      log.error(String.format("Result no output: %s", dashScopeResult));
    }
    return result;
  }
}
