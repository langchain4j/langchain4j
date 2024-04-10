package dev.langchain4j.model.spark.text.entity.builder;

import dev.langchain4j.model.spark.text.entity.CorrectionTextRequest;

import java.util.Base64;

public  class CorrectionTextRequestBuilder {

    private CorrectionTextRequest correctionRequest;

    public CorrectionTextRequestBuilder() {
        correctionRequest = new CorrectionTextRequest();
        correctionRequest.setHeader(new CorrectionTextRequest.Header());
        correctionRequest.setParameter(new CorrectionTextRequest.Parameter(new CorrectionTextRequest.Parameter.S9A87E3Ec(new CorrectionTextRequest.Parameter.S9A87E3Ec.Result())));
        correctionRequest.setPayload(new CorrectionTextRequest.Payload(new CorrectionTextRequest.Payload.Input()));
    }

    public CorrectionTextRequestBuilder appId(String appId) {
        correctionRequest.getHeader().setAppid(appId);
        return this;
    }
    public CorrectionTextRequestBuilder status(Integer status) {
        correctionRequest.getHeader().setStatus(status);
        return this;
    }
    public CorrectionTextRequestBuilder text(String text) {
//        String base64Text = Base64.getEncoder().encodeToString(text.getBytes());
        correctionRequest.getPayload().getInput().setText(text);
        return this;
    }

    /**
     * 黑白名单功能时这个值是必传的
     * @param resId
     * @return
     */
    public CorrectionTextRequestBuilder resId(String resId) {
        correctionRequest.getParameter().getS9a87e3ec().setResId(resId);
        return this;
    }



    public CorrectionTextRequest build() {
        return correctionRequest;
    }

}
