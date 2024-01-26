package dev.langchain4j.model.qianfan.client;

import retrofit2.Call;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;
public class AsyncRequestExecutor<Response, ResponseContent> {
    private final Call<Response> call;
    private final Function<Response, ResponseContent> responseContentExtractor;

    AsyncRequestExecutor(Call<Response> call, Function<Response, ResponseContent> responseContentExtractor) {
        this.call = call;
        this.responseContentExtractor = responseContentExtractor;
    }

    AsyncResponseHandling onResponse(final Consumer<ResponseContent> responseHandler) {
        return new AsyncResponseHandling() {
            public ErrorHandling onError(final Consumer<Throwable> errorHandler) {
                return new ErrorHandling() {
                    public void execute() {
                        try {
                            retrofit2.Response<Response> retrofitResponse = AsyncRequestExecutor.this.call.execute();
                            if (retrofitResponse.isSuccessful()) {
                                Response response = retrofitResponse.body();
                                ResponseContent responseContent = AsyncRequestExecutor.this.responseContentExtractor.apply(response);
                                responseHandler.accept(responseContent);
                            } else {
                                errorHandler.accept(Utils.toException(retrofitResponse));
                            }
                        } catch (IOException var4) {
                            errorHandler.accept(var4);
                        }

                    }
                };
            }

            public ErrorHandling ignoreErrors() {
                return new ErrorHandling() {
                    public void execute() {
                        try {
                            retrofit2.Response<Response> retrofitResponse = AsyncRequestExecutor.this.call.execute();
                            if (retrofitResponse.isSuccessful()) {
                                Response response = retrofitResponse.body();
                                ResponseContent responseContent = AsyncRequestExecutor.this.responseContentExtractor.apply(response);
                                responseHandler.accept(responseContent);
                            }
                        } catch (IOException var4) {
                        }

                    }
                };
            }
        };
    }
}
