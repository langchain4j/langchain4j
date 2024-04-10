package dev.langchain4j.model.spark.chat.listener;


import com.alibaba.fastjson.JSONObject;

import dev.langchain4j.model.spark.chat.constant.SparkResponseCode;
import dev.langchain4j.model.spark.chat.entity.request.SparkRequest;
import dev.langchain4j.model.spark.chat.exception.SparkException;
import dev.langchain4j.model.spark.chat.entity.SparkMessage;
import dev.langchain4j.model.spark.chat.entity.response.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Slf4j
@Data
public class SparkChatListener extends WebSocketListener {


    private SparkRequest sparkRequest;
    public SparkChatListener() {
    }

    /**
     * 收到回答时会调用此方法
     *
     * @param content       回答内容
     * @param sparkRequest  本次会话的请求参数
     * @param sparkResponse 本次回调的响应数据
     * @param webSocket     本次会话的webSocket连接
     */
    public void onMessage(String content, SparkRequest sparkRequest, SparkResponse sparkResponse, WebSocket webSocket) {
        // TODO 重写此方法，实现业务逻辑
    }

    /**
     * 收到functionCall调用此方法
     *
     * @param functionCall  functionCall
     * @param sparkRequest  本次会话的请求参数
     * @param sparkResponse 本次回调的响应数据
     * @param webSocket     本次会话的webSocket连接
     */
    public void onFunctionCall(SparkResponseFunctionCall functionCall,SparkRequest sparkRequest, SparkResponse sparkResponse, WebSocket webSocket) {
        //TODO  重写此方法，实现业务逻辑
    }

    @Override
    public final void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
        // 发送消息
        try {
            String requestJson =JSONObject.toJSONString(sparkRequest);
            webSocket.send(requestJson);
        } catch (Exception e) {
            throw new SparkException(400, "请求数据 SparkRequest 序列化失败", e);
        }
    }

    @Override
    public final void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
        SparkResponse sparkResponse;
        // 解析响应
        try {
            sparkResponse =  JSONObject.parseObject(text, SparkResponse.class);
        } catch (Exception e) {
            webSocket.close(1000, "");
            throw new SparkException(500, "响应数据 SparkResponse 解析失败：" + text, e);
        }
        SparkResponseHeader header = sparkResponse.getHeader();
        if (null == header) {
            webSocket.close(1000, "");
            throw new SparkException(500, "响应数据不完整 SparkResponse.header为null，完整响应：" + text);
        }

        // 业务状态判断
        Integer code = header.getCode();
        if (SparkResponseCode.CODEZERO != code) {
            webSocket.close(1000, "");
            throw SparkException.bizFailed(code);
        }

        // 回答文本
        SparkResponseChoices choices = sparkResponse.getPayload().getChoices();
        List<SparkMessage> messages = choices.getText();

        StringBuilder stringBuilder = new StringBuilder();
        SparkResponseFunctionCall functionCall = null;
        Integer status = header.getStatus();
        for (SparkMessage message : messages) {
            if (message.getFunctionCall() != null) {
                functionCall = message.getFunctionCall();
                break;
            }
            stringBuilder.append(message.getContent());
        }
        if (functionCall != null) {
            this.onFunctionCall(functionCall, sparkRequest, sparkResponse, webSocket);
        } else {
            String content = stringBuilder.toString();
            this.onMessage(content,sparkRequest, sparkResponse, webSocket);
        }

        // 最后一条结果，关闭连接
        if (SparkResponseCode.STATUSTWO == status) {
            webSocket.close(1000, "");
        }
    }

    @Override
    public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, Response response) {
        log.error("讯飞星火api发生异常：", t);
    }
}
