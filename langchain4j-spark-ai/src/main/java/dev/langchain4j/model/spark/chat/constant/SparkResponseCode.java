package dev.langchain4j.model.spark.chat.constant;

/**
 * @ClassName: SparkResponseStatus
 * @Description: 类描述
 * @author: sunjiuxiang
 * @date: 2024/4/10 16:09
 */
public interface SparkResponseCode {

    /**
     * status状态码
     * 取值为[0,1,2]；
     * 0代表首次结果；
     * 1代表中间结果；
     * 2代表最后一个结果取值为
     *
     */
    Integer STATUSZERO = 0;
    Integer STATUSONE = 1;
    Integer STATUSTWO = 2;

    /**
     * 错误码，0表示正常，非0表示出错
     */
    Integer CODEZERO = 0;


}
