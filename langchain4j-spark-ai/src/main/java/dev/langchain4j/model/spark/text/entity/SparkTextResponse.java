package dev.langchain4j.model.spark.text.entity;
import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Data
public class SparkTextResponse {
    private Payload payload;
    private Header header;
    @Data
    public class Header {
        private long code;
        private String message;
        private String sid;

    }
    @Data
    public class Payload {

        //纠错和文本改写用的这个字段
        private Result result;
        //公文校对用的这个字段
        @JSONField(name="output_result")
        private Result outputResult;
        @Data
        public class Result {
            private String compress;
            private String format;
            private String text;
            private String encoding;
        }
    }
}




