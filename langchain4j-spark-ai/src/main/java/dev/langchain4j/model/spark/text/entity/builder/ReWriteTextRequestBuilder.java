package dev.langchain4j.model.spark.text.entity.builder;

import dev.langchain4j.model.spark.text.entity.ReWriteTextRequest;

import java.util.Base64;

public  class ReWriteTextRequestBuilder {

    private ReWriteTextRequest reWriteTextRequest;

    public ReWriteTextRequestBuilder() {
        reWriteTextRequest = new ReWriteTextRequest();
        reWriteTextRequest.setHeader(new ReWriteTextRequest.Header());
        reWriteTextRequest.setPayload(new ReWriteTextRequest.Payload(new ReWriteTextRequest.Payload.Input1()));
        reWriteTextRequest.setParameter(new ReWriteTextRequest.Parameter(new ReWriteTextRequest.Parameter.Se3Acbe7F(new ReWriteTextRequest.Parameter.Se3Acbe7F.Result())));
    }

    public ReWriteTextRequestBuilder appId(String appId) {
        reWriteTextRequest.getHeader().setAppid(appId);
        return this;
    }

    public ReWriteTextRequestBuilder status(Integer status) {
        reWriteTextRequest.getHeader().setStatus(status);
        reWriteTextRequest.getPayload().getInput1().setStatus(status);
        return this;
    }

    public ReWriteTextRequestBuilder level(Integer level) {
        reWriteTextRequest.getParameter().getSe3acbe7f().setLevel(level);
        return this;
    }

    public ReWriteTextRequestBuilder text(String text) {
//        String base64Text = Base64.getEncoder().encodeToString(text.getBytes());
        reWriteTextRequest.getPayload().getInput1().setText(text);
        return this;
    }

    public ReWriteTextRequest build() {
        return reWriteTextRequest;
    }

}
