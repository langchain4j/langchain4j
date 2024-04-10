package dev.langchain4j.model.text;

import com.alibaba.fastjson.JSONObject;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.spark.text.SparkTextModel;
import dev.langchain4j.model.spark.text.constant.SparkTextRequestType;
import org.junit.Test;
import java.util.Base64;
import java.util.Map;

import static dev.langchain4j.data.message.UserMessage.userMessage;

/**
 * @ClassName: SparkTextModelDemo
 * @Description: 支持文本纠错\文本改写\公文校对
 * @author: sunjiuxiang
 * @date: 2024/4/10
 */
public class SparkTextModelDemo {
    /**
     * baseUrl
     * official:   https://cn-huadong-1.xf-yun.com/v1/private/s37b42a45
     * rewrite:    https://api.xf-yun.com/v1/private/se3acbe7f
     * correction: https://api.xf-yun.com/v1/private/s9a87e3ec
     *
     * Integer level,  // 改写reWrite时必填项取值1-6，其他的不填
     * String resId   // 纠错Correction时并且加黑白名单时必填，其他情况可以不填
     */
    ChatLanguageModel correctionTextModel = SparkTextModel.builder()
            .hostUrl("https://api.xf-yun.com/v1/private/s9a87e3ec")
            .apiKey("apiSecret")
            .apiSecret("apiSecret")
            .appId("apiSecret")
            .requestType(SparkTextRequestType.CORRECTION)
            .status(3)
//            .resId()
            .build();

    ChatLanguageModel reWriteTextModel = SparkTextModel.builder()
            .hostUrl("https://api.xf-yun.com/v1/private/se3acbe7f")
            .apiKey("apiSecret")
            .apiSecret("apiSecret")
            .appId("apiSecret")
            .level(5)
            .requestType(SparkTextRequestType.REWRITE)
            .status(3)
            .build();

    ChatLanguageModel officialTextModel = SparkTextModel.builder()
            .hostUrl("https://cn-huadong-1.xf-yun.com/v1/private/s37b42a45")
            .apiKey("apiSecret")
            .apiSecret("apiSecret")
            .appId("apiSecret")
            .requestType(SparkTextRequestType.OFFICIALCHECK)
            .status(3)
//            .resId()
//            .level(1)
            .build();
    @Test
    public void correctionTest(){
        String text = "画蛇天足,战士";
        String base64Text = Base64.getEncoder().encodeToString(text.getBytes());
        UserMessage userMessage = userMessage(base64Text);
        Response<AiMessage> response = correctionTextModel.generate(userMessage);
        String result = response.content().text();
        Map map = JSONObject.parseObject(result, Map.class);
        System.out.println(map);
    }

   @Test
   public void reWriteTest(){
       String text = "随着我国城市化脚步的不断加快，园林工程建设的数量也在不断上升，城市对于园林工程的质量要求也随之上升，" +
               "然而就当前我国园林工程管理的实践而言，就园林工程质量管理这一环节还存在许多不足之处，本文在探讨园林工程质量内涵的基础上，" +
               "深入进行质量管理策略探讨，目的是保障我国园林工程施工质量和提升整体发展效率。";
       String base64Text = Base64.getEncoder().encodeToString(text.getBytes());
       UserMessage userMessage = userMessage(base64Text);
       Response<AiMessage> response = reWriteTextModel.generate(userMessage);
       System.out.println(response);
   }

    @Test
    public void officialofficialTest(){
        String text = "第二个百年目标";
        String base64Text = Base64.getEncoder().encodeToString(text.getBytes());
        UserMessage userMessage = userMessage(base64Text);
        Response<AiMessage> response = officialTextModel.generate(userMessage);
        System.out.println(response);
    }
}
