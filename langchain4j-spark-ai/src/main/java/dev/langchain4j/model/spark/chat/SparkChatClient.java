package dev.langchain4j.model.spark.chat;


import dev.langchain4j.model.spark.chat.entity.SparkSyncChatResponse;
import dev.langchain4j.model.spark.chat.entity.request.SparkRequest;
import dev.langchain4j.model.spark.chat.exception.SparkException;
import dev.langchain4j.model.spark.chat.listener.SparkChatListener;
import dev.langchain4j.model.spark.chat.listener.SparkChatSyncListener;
import lombok.Data;
import lombok.NoArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * @ClassName: SparkChatClient
 * @Description: 讯飞聊天模型的client
 * @author: sunjiuxiang
 * @date: 2024/4/10
 */
@Data
@NoArgsConstructor
public class SparkChatClient {

    public String baseUrl;
    public OkHttpClient okHttpClient = new OkHttpClient.Builder().build();

    public SparkChatClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void chatStream(SparkRequest sparkRequest, SparkChatListener listener) {
        listener.setSparkRequest(sparkRequest);
        // 创建请求
        Request request = new Request.Builder().url(baseUrl).build();
        // 创建websocket连接，发送请求【都用socket了，竟然用短连接！】
        okHttpClient.newWebSocket(request, listener);
    }

    public SparkSyncChatResponse chatSync(SparkRequest sparkRequest) {
        SparkSyncChatResponse chatResponse = new SparkSyncChatResponse();
        SparkChatSyncListener syncChatListener = new SparkChatSyncListener(chatResponse);
        chatStream(sparkRequest, syncChatListener);
        while (!chatResponse.isOk()) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
            }
        }
        Throwable exception = chatResponse.getException();
        if (exception != null) {
            if (!(exception instanceof SparkException)) {
                exception = new SparkException(500, exception.getMessage());
            }
            throw (SparkException) exception;
        }
        return chatResponse;
    }

}
