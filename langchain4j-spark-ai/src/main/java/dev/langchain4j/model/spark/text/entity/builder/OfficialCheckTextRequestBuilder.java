package dev.langchain4j.model.spark.text.entity.builder;

import dev.langchain4j.model.spark.text.entity.OfficialCheckTextRequest;

import java.util.Base64;

public  class OfficialCheckTextRequestBuilder {

    private OfficialCheckTextRequest officialCheckTextRequest;

    public OfficialCheckTextRequestBuilder() {
        officialCheckTextRequest = new OfficialCheckTextRequest();
        officialCheckTextRequest.setHeader(new OfficialCheckTextRequest.Header());
        officialCheckTextRequest.setPayload(new OfficialCheckTextRequest.Payload(new OfficialCheckTextRequest.Payload.Text()));
        officialCheckTextRequest.setParameter(new OfficialCheckTextRequest.Parameter(new OfficialCheckTextRequest.Parameter.MiduCorrect(new OfficialCheckTextRequest.Parameter.MiduCorrect.OutputResult())));
    }

    public OfficialCheckTextRequestBuilder appId(String appId) {
        officialCheckTextRequest.getHeader().setAppid(appId);
        return this;
    }

    public OfficialCheckTextRequestBuilder status(Integer status) {
        officialCheckTextRequest.getHeader().setStatus(status);
        officialCheckTextRequest.getPayload().getText().setStatus(status);
        return this;
    }



    public OfficialCheckTextRequestBuilder text(String text) {
//        String base64Text = Base64.getEncoder().encodeToString(text.getBytes());
        officialCheckTextRequest.getPayload().getText().setText(text);
        return this;
    }

    public OfficialCheckTextRequest build() {
        return officialCheckTextRequest;
    }

}
