package dev.langchain4j.model.spark.chat;

import com.alibaba.fastjson.JSONObject;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;

import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.spark.chat.constant.SparkMessageRole;
import dev.langchain4j.model.spark.chat.entity.SparkMessage;
import dev.langchain4j.model.spark.chat.entity.SparkSyncChatResponse;
import dev.langchain4j.model.spark.chat.entity.request.SparkRequest;
import dev.langchain4j.model.spark.chat.spi.SparkChatModelBuilderFactory;
import dev.langchain4j.model.spark.utils.AuthUtil;
import lombok.Builder;
import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;


/**
 * @ClassName: SparkChatModel
 * @Description: 讯飞聊天模型的Model封装
 * @author: sunjiuxiang
 * @date: 2024/4/10
 */
public class SparkChatModel implements ChatLanguageModel {

    private final SparkChatClient sparkChatClient;
    private String appId;
    private Double temperature;
    private Integer maxTokens;
    private Integer topK;
    private String domain;

    @Builder
    public SparkChatModel(String hostUrl,
                          String appid,
                          String apiSecret,
                          String apiKey,
                          String domain,
                          Double temperature,
                          Integer maxTokens,
                          Integer topK) {
        String baseUrl = AuthUtil.getChatAuthUrl(hostUrl, apiKey, apiSecret);
        this.sparkChatClient = new SparkChatClient(baseUrl);
        this.appId = appid;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.topK = topK;
        this.domain=domain;
    }


    @Override
    public Response<AiMessage> generate(List<ChatMessage> chatMessageList) {
        //讯飞模型限制，不支持过长的历史消息
//        splitHistoryMessage(chatMessageList);
        // 消息列表
        List<SparkMessage> messagesaList = convertSparkMessage(chatMessageList);
        // 构造sparkRequest请求,根据实际情况动态化加工入参
        SparkRequest sparkRequest = SparkRequest.builder()
                //appId
                .appId(appId)
                // 消息列表
                .messages(messagesaList)
                // 模型回答的tokens的最大长度,非必传，默认为2048。
                .maxTokens(maxTokens)
                // 核采样阈值。用于决定结果随机性,取值越高随机性越强即相同的问题得到的不同答案的可能性越高 非必传,取值为[0,1],默认为0.5
                .temperature(temperature)
                .topK(topK)
                // 指定请求版本，默认使用3.0版本
                .domain(domain)
                .build();
        SparkSyncChatResponse chatResponse = sparkChatClient.chatSync(sparkRequest);
        return Response.from(AiMessage.from(JSONObject.toJSONString(chatResponse)));
    }


    private List<SparkMessage> convertSparkMessage(List<ChatMessage> chatMessageList){
        List<SparkMessage> sparkMessageList = new ArrayList<>(chatMessageList.size());
        for (ChatMessage chatMessage:chatMessageList){
            String role;
            switch (chatMessage.type()){
                case AI:
                    role = SparkMessageRole.ASSISTANT;
                    sparkMessageList.add( new SparkMessage(role,chatMessage.text()));
                    break;
                case USER:
                    role = SparkMessageRole.USER;
                    if (chatMessage instanceof UserMessage){
                        UserMessage userMessage = (UserMessage) chatMessage;
                        List<Content> contents = userMessage.contents();
                        for (Content content:contents){
                            ContentType contentType = content.type();
                            if (contentType==ContentType.TEXT){
                                sparkMessageList.add( new SparkMessage(role,((TextContent) content).text()));
                            }
                            if (contentType==ContentType.IMAGE){
                                ImageContent imageContent = (ImageContent)content;
                                String base64Data = imageContent.image().base64Data();
                                SparkMessage sparkMessage = new SparkMessage(role, base64Data);
                                sparkMessage.setContentType("image");
                                sparkMessageList.add(sparkMessage);
                            }
                        }

                    }
                    break;
                case SYSTEM:
                    role = SparkMessageRole.SYSTEM;
                    sparkMessageList.add(new SparkMessage(role,chatMessage.text()));
                    break;
                default:
                    break;
            }

        }
        return sparkMessageList;
    }

    /**
     * 由于历史记录最大上线1.2W左右，需要判断是能能加入历史
     * @param chatMessageList
     */
    private void splitHistoryMessage(List<ChatMessage> chatMessageList){
        int history_length=0;
        for(ChatMessage temp:chatMessageList){
            history_length=history_length+temp.text().length();
        }
        if(history_length>1200000000){ // 这里限制了总上下文携带，图片理解注意放大 ！！！
            chatMessageList.remove(2); //由于图片理解的第一条必须是图片，不能删，所以从第二轮对话开始删
            chatMessageList.remove(3);
            splitHistoryMessage(chatMessageList);
        }
    }

    public static SparkChatModelBuilder builder() {
        for (SparkChatModelBuilderFactory factory : loadFactories(SparkChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new SparkChatModelBuilder();
    }
    public static class SparkChatModelBuilder {
        public SparkChatModelBuilder() {
        }
    }
}
