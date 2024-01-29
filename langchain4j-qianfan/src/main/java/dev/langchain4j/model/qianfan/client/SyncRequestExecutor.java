package dev.langchain4j.model.qianfan.client;

import okhttp3.Request;
import okio.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Call;

import java.io.IOException;
import java.util.function.Function;

public class SyncRequestExecutor<Response, ResponseContent> {
    private final static Logger LOG = LoggerFactory.getLogger(SyncRequestExecutor.class);
    private final Call<Response> call;
    private final Function<Response, ResponseContent> responseContentExtractor;

    SyncRequestExecutor(Call<Response> call, Function<Response, ResponseContent> responseContentExtractor) {
        this.call = call;
        this.responseContentExtractor = responseContentExtractor;
    }

    public ResponseContent execute() {
        try {
            retrofit2.Response<Response> retrofitResponse =  this.call.execute();
            if (retrofitResponse.isSuccessful()) {
                Response response = retrofitResponse.body();
                return this.responseContentExtractor.apply(response);
            } else {
                throw Utils.toException(retrofitResponse);
            }
        } catch (IOException var3) {
            throw new RuntimeException(var3);
        }
    }
    public  String getBody(Request request) {
        if("GET".equals(request.method())){
            return "";
        }
        try {
            Buffer buffer = new Buffer();
            request.body().writeTo(buffer);
            return buffer.readUtf8();
        } catch (Exception var2) {
            return "[Exception happened while reading request body. Check logs for more details.]";
        }
    }
}
