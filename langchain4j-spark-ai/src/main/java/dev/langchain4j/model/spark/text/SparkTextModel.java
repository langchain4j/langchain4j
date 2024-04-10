package dev.langchain4j.model.spark.text;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.spark.chat.exception.SparkException;
import dev.langchain4j.model.spark.text.constant.SparkTextRequestType;
import dev.langchain4j.model.spark.text.entity.*;
import dev.langchain4j.model.spark.utils.AuthUtil;
import lombok.Builder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 */
public class SparkTextModel implements ChatLanguageModel {

    private final SparkTextClient client;
    private final String appId;
    private final Integer status;
    private final Integer level;
    private final String resId;
    private final String requestType;
    /**
     * baseUrl
     * official:   https://cn-huadong-1.xf-yun.com/v1/private/s37b42a45
     * rewrite:    https://api.xf-yun.com/v1/private/se3acbe7f
     * correction: https://api.xf-yun.com/v1/private/s9a87e3ec
     */
    @Builder
    public SparkTextModel(String hostUrl,
                          String appId,
                          String apiSecret,
                          String apiKey,
                          String requestType,
                          Integer status,
                          // 改写时必填项取值1-6，其他的不填
                          Integer level,
                          //纠错时并且加黑白名单时必填，其他情况可以不填
                          String resId) {
        //验证得到真正的url
        try {
             String baseUrl = AuthUtil.getTextAuthUrl(hostUrl, apiKey, apiSecret);
             this.client = new SparkTextClient(baseUrl);
        } catch (Exception e) {
            e.printStackTrace();
            throw SparkException.bizFailed(11500);
        }
        this.appId = appId;
        this.requestType = requestType;
        this.status = status;
        this.level = level;
        this.resId = resId;
    }


    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {

        if (CollectionUtil.isEmpty(messages)){
            throw SparkException.bizFailed(11501);
        }
        ChatMessage chatMessage = messages.get(0);
        SparkTextRequest request = new SparkTextRequest();
        switch (requestType){
            case SparkTextRequestType.REWRITE:
                request = ReWriteTextRequest.builder()
                        .appId(appId)
                        .level(level)
                        .status(status)
                        .text(chatMessage.toString())
                        .build();
                break;
            case SparkTextRequestType.CORRECTION:
                request = CorrectionTextRequest.builder()
                        .appId(appId)
                        .status(status)
                        .resId(resId)
                        .text(chatMessage.text()) //调用时处理一下：
                        .build();
                break;
            case SparkTextRequestType.OFFICIALCHECK:
                request = OfficialCheckTextRequest.builder()
                        .appId(appId)
                        .status(status)
                        .text(chatMessage.text())
                        .build();
                break;
        }
        SparkTextResponse response = client.doPost(request);
        SparkTextResponse.Payload.Result result = response.getPayload().getResult();
        if (ObjectUtil.isEmpty(result)){
            result = response.getPayload().getOutputResult();
        }
        String base64Text = new String(Base64.getDecoder().decode(result.getText()), StandardCharsets.UTF_8);
        return Response.from(AiMessage.from(base64Text));
    }


    public static SparkTextModelBuilder builder() {
        return new SparkTextModelBuilder();
    }

    public static class SparkTextModelModelBuilder {
        public SparkTextModelModelBuilder() {
        }
    }
}
