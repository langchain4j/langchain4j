package dev.langchain4j.model.spark.chat.listener;


import dev.langchain4j.model.spark.chat.constant.SparkResponseCode;
import dev.langchain4j.model.spark.chat.entity.SparkSyncChatResponse;
import dev.langchain4j.model.spark.chat.entity.request.SparkRequest;
import dev.langchain4j.model.spark.chat.entity.response.SparkResponse;
import dev.langchain4j.model.spark.chat.entity.response.SparkResponseFunctionCall;
import dev.langchain4j.model.spark.chat.entity.response.SparkResponseUsage;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class SparkChatSyncListener extends SparkChatListener {
    private final StringBuilder stringBuilder = new StringBuilder();

    private final SparkSyncChatResponse sparkSyncChatResponse;

    public SparkChatSyncListener(SparkSyncChatResponse sparkSyncChatResponse) {
        this.sparkSyncChatResponse = sparkSyncChatResponse;
    }

    @Override
    public void onMessage(String content, SparkRequest sparkRequest, SparkResponse sparkResponse, WebSocket webSocket) {
        stringBuilder.append(content);
        Integer status = sparkResponse.getHeader().getStatus();
        SparkResponseUsage usage = sparkResponse.getPayload().getUsage();
        if (SparkResponseCode.STATUSTWO == status) { //会话状态，取值为[0,1,2]；0代表首次结果；1代表中间结果；2代表最后一个结果
            sparkSyncChatResponse.setContent(stringBuilder.toString());
            sparkSyncChatResponse.setTextUsage(usage.getText());
            sparkSyncChatResponse.setOk(true);
        }
    }

    @Override
    public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, Response response) {
        log.error("讯飞星火api发生异常", t);
        sparkSyncChatResponse.setOk(true);
        sparkSyncChatResponse.setException(t);
    }

    /**
     * 收到functionCall调用此方法
     *
     * @param functionCall  functionCall
     * @param sparkRequest  本次会话的请求参数
     * @param sparkResponse 本次回调的响应数据
     * @param webSocket     本次会话的webSocket连接
     */
    @Override
    public void onFunctionCall(SparkResponseFunctionCall functionCall,SparkRequest sparkRequest, SparkResponse sparkResponse, WebSocket webSocket) {
        Integer status = sparkResponse.getHeader().getStatus();
        SparkResponseUsage usage = sparkResponse.getPayload().getUsage();
        if (SparkResponseCode.STATUSTWO == status) {
            sparkSyncChatResponse.setContent(stringBuilder.toString());
            sparkSyncChatResponse.setTextUsage(usage.getText());
            sparkSyncChatResponse.setFunctionCall(functionCall);
            sparkSyncChatResponse.setOk(true);
        }
    }
}
