
package dev.langchain4j.model.spark.text.entity;
import com.alibaba.fastjson.annotation.JSONField;
import dev.langchain4j.model.spark.text.entity.builder.CorrectionTextRequestBuilder;
import dev.langchain4j.model.spark.text.constant.SparkTextRequestType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CorrectionTextRequest extends SparkTextRequest {
    private Payload payload;
    private Parameter parameter;
    private Header header;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
   public static class Header {
        private String uid;
        @JSONField(name="app_id")
        private String appid;
        private Integer status = 3;

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Parameter {
        private S9A87E3Ec s9a87e3ec;
        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class S9A87E3Ec {
            private Result result;
            @JSONField(name = "res_id")
            private String resId;

            public S9A87E3Ec(Result result) {
                this.result = result;
            }

            @Data
            @AllArgsConstructor
            @NoArgsConstructor
            public static class Result {
                private String compress;
                private String format;
                private String encoding;
            }
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Payload {
        private Input input;
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Input {
            private String compress;
            private String format;
            private String text;
            private String encoding;
            private Integer status = 3;

        }
    }
    public static CorrectionTextRequestBuilder builder() {
        return new CorrectionTextRequestBuilder();
    }

}




