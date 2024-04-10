package dev.langchain4j.model.spark.chat.constant;

import java.util.LinkedHashMap;
import java.util.Map;



/**
 * @ClassName: SparkErrorCode
 * @Description: 错误码描述
 * @author: sunjiuxiang
 * @date: 2024/4/10
 */
public class SparkResponseErrorCode {
    public static final Map<Integer, String> RESPONSE_ERROR_MAP = new LinkedHashMap<>(32);

    static {
        RESPONSE_ERROR_MAP.put(10000, "升级为ws出现错误");
        RESPONSE_ERROR_MAP.put(10001, "通过ws读取用户的消息出错");
        RESPONSE_ERROR_MAP.put(10002, "通过ws向用户发送消息错误");
        RESPONSE_ERROR_MAP.put(10003, "用户的消息格式有错误");
        RESPONSE_ERROR_MAP.put(10004, "用户数据的schema错误");
        RESPONSE_ERROR_MAP.put(10005, "用户参数值有错误");
        RESPONSE_ERROR_MAP.put(10006, "用户并发错误：当前用户已连接，同一用户不能多处同时连接");
        RESPONSE_ERROR_MAP.put(10007, "用户流量受限：服务正在处理用户当前的问题，需等待处理完成后再发送新的请求");
        RESPONSE_ERROR_MAP.put(10008, "服务容量不足，联系工作人员");
        RESPONSE_ERROR_MAP.put(10009, "和引擎建立连接失败");
        RESPONSE_ERROR_MAP.put(10010, "接收引擎数据的错误");
        RESPONSE_ERROR_MAP.put(10011, "发送数据给引擎的错误");
        RESPONSE_ERROR_MAP.put(10012, "引擎内部错误");
        RESPONSE_ERROR_MAP.put(10013, "输入内容审核不通过，涉嫌违规，请重新调整输入内容");
        RESPONSE_ERROR_MAP.put(10014, "输出内容涉及敏感信息，审核不通过，后续结果无法展示给用户");
        RESPONSE_ERROR_MAP.put(10015, "appid在黑名单中");
        RESPONSE_ERROR_MAP.put(10016, "appid授权类的错误。未开通此功能，未开通对应版本，token不足，并发超过授权等等");
        RESPONSE_ERROR_MAP.put(10017, "清除历史失败");
        RESPONSE_ERROR_MAP.put(10019, "本次会话内容有涉及违规信息的倾向");
        RESPONSE_ERROR_MAP.put(10110, "服务忙，请稍后再试");
        RESPONSE_ERROR_MAP.put(10163, "请求引擎的参数异常，引擎的schema检查不通过");
        RESPONSE_ERROR_MAP.put(10222, "引擎网络异常");
        RESPONSE_ERROR_MAP.put(10907, "token数量超过上限。对话历史+问题的字数太多，需要精简输入");
        RESPONSE_ERROR_MAP.put(11200, "授权错误：该appId没有相关功能的授权或者业务量超过限制");
        RESPONSE_ERROR_MAP.put(11201, "授权错误：日流控超限。超过当日最大访问量的限制");
        RESPONSE_ERROR_MAP.put(11202, "授权错误：秒级流控超限。秒级并发超过授权路数限制");
        RESPONSE_ERROR_MAP.put(11203, "授权错误：并发流控超限。并发路数超过授权路数限制");

        RESPONSE_ERROR_MAP.put(11500, "鉴权错误");
        RESPONSE_ERROR_MAP.put(11501, "空请求数据");
    }
}
