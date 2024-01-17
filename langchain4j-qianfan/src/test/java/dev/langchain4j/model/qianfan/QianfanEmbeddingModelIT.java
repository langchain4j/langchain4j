package dev.langchain4j.model.qianfan;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class QianfanEmbeddingModelIT {

    //see your api key and secret key here: https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application
    private String apiKey ="your api key";
    private String secretKey ="your secret key";

    QianfanEmbeddingModel model = QianfanEmbeddingModel.builder().user("111").apiKey(apiKey)
            .secretKey(secretKey).endpoint("embedding-v1").build();


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