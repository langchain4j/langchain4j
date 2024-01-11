package dev.langchain4j.model.wenxin;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WenxinEmbeddingModelIT  {

    //see your client id and secret key here: https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application
    private String clientId ="your client id";
    private String secretKey ="your secret key";

    WenxinEmbeddingModel model = WenxinEmbeddingModel.builder().user("111").clientId(clientId)
            .secretKey(secretKey).serviceName("embedding-v1").build();


    @Test
    void should_embed_text() {

        // given
        String text = "hello";

        // when
        Response<Embedding> response = model.embed(text);
        System.out.println(response);

        // then
        Embedding embedding = response.content();
        assertThat(embedding.dimension()).isEqualTo(384);

        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isNull();
    }
}