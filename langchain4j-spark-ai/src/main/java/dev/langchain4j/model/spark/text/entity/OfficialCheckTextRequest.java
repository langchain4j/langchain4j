// OfficialCheckRequest.java

// YApi QuickType插件生成，具体参考文档:https://plugins.jetbrains.com/plugin/18847-yapi-quicktype/documentation

package dev.langchain4j.model.spark.text.entity;
import com.alibaba.fastjson.annotation.JSONField;
import dev.langchain4j.model.spark.text.constant.SparkTextRequestType;
import dev.langchain4j.model.spark.text.entity.builder.OfficialCheckTextRequestBuilder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OfficialCheckTextRequest extends SparkTextRequest {
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
        @JSONField(name="midu_correct")
        private MiduCorrect miduCorrect;
        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class MiduCorrect {
            @JSONField(name="output_result")
            private OutputResult outputResult;
            @Data
            @AllArgsConstructor
            @NoArgsConstructor
            public static class OutputResult {
                private String compress;
                private String format;
                private String encoding;

            }
        }
    }
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Payload {
        private Text text;
        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class Text {
            private String compress;
            private String format;
            private String text;
            private String encoding;
            private long status;
        }
    }
    public static OfficialCheckTextRequestBuilder builder() {
        return new OfficialCheckTextRequestBuilder();
    }

}








