package dev.langchain4j.model.chat;

import dev.langchain4j.data.message.*;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.spark.chat.SparkChatModel;
import dev.langchain4j.model.spark.utils.ImageUtil;
import org.junit.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static dev.langchain4j.data.message.UserMessage.userMessage;

/**
 * @ClassName: SparkChatModelDemo
 * @Description: langchain4j整合讯飞大模型调用示例
 * @author: sunjiuxiang
 * @date: 2024/4/10
 */
public class SparkChatModelDemo {

    //chatModel 支持文本对话
    ChatLanguageModel chatModel = SparkChatModel.builder()
            .hostUrl("https://spark-api.xf-yun.com/v2.1/chat")
            .appid("appid")
            .apiKey("apiKey")
            .apiSecret("apiSecret")
            .domain("generalv2")
            .temperature(0.2)
            .maxTokens(200)
            .topK(2)
            .build();

    //imageModel 支持图片理解
    ChatLanguageModel imageModel = SparkChatModel.builder()
            .hostUrl("https://spark-api.cn-huabei-1.xf-yun.com/v2.1/image")
            .appid("appid")
            .apiKey("apiKey")
            .apiSecret("apiSecret")
            .domain("image")
            .temperature(0.2)
            .maxTokens(200)
            .topK(2)
            .build();

    @Test
   public void should_generate_answer() {
        UserMessage userMessage = userMessage("你好，请问一下德国的首都是哪里呢？");

        Response<AiMessage> response = chatModel.generate(userMessage);
        System.out.println(response);
    }

    @Test
    public void should_generate_answer_from_history() {
        // init history
        List<ChatMessage> messages = new ArrayList<>();

        // given question first time
        UserMessage userMessage = userMessage("你好，请问一下德国的首都是哪里呢？");
        Response<AiMessage> response = chatModel.generate(userMessage);

        // given question with history
        messages.add(userMessage);
        messages.add(response.content());

        UserMessage secondUserMessage = userMessage("你能告诉我上个问题我问了你什么呢？");
        messages.add(secondUserMessage);
        Response<AiMessage> secondResponse = chatModel.generate(messages);

    }

    @Test
    public void generate_answer_from_image() throws IOException {
        List<ChatMessage> messages = new ArrayList<>();
        String base64Data = Base64.getEncoder().encodeToString(ImageUtil.read("D:\\xtkj\\image\\1.png"));
        ImageContent imageContent = ImageContent.from(base64Data,"png");
        TextContent textContent = TextContent.from("这张图片描述的什么内容");
        messages.add(new UserMessage(imageContent, textContent));
        UserMessage userMessage = userMessage("图中除了西瓜还有什么内容？");
        messages.add(userMessage);
        Response<AiMessage> response = imageModel.generate(messages);
        System.out.println(response);
    }
}
