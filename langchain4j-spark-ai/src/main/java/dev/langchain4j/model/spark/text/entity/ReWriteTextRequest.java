
package dev.langchain4j.model.spark.text.entity;
import com.alibaba.fastjson.annotation.JSONField;
import dev.langchain4j.model.spark.text.constant.SparkTextRequestType;
import dev.langchain4j.model.spark.text.entity.builder.ReWriteTextRequestBuilder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReWriteTextRequest extends SparkTextRequest {
    private Payload payload;
    private Parameter parameter;
    private Header header;
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Header {
        @JSONField(name="app_id")
        private String appid;
        private Integer status = 3;
    }
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Parameter {
        private Se3Acbe7F se3acbe7f;
        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class Se3Acbe7F {
            private Result result;
            private Integer level;
            @Data
            @AllArgsConstructor
            @NoArgsConstructor
            public static class Result {
                private String compress;
                private String format;
                private String encoding;
            }

            public Se3Acbe7F(Result result) {
                this.result = result;
            }
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Payload {
        private Input1 input1;
        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class Input1 {
            private String compress;
            private String format;
            private String text;
            private String encoding;
            private Integer status;
        }
    }
    public static ReWriteTextRequestBuilder builder() {
        return new ReWriteTextRequestBuilder();
    }

}











