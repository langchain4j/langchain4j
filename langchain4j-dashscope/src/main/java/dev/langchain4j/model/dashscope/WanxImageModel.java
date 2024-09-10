package dev.langchain4j.model.dashscope;

import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.alibaba.dashscope.exception.NoApiKeyException;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.dashscope.spi.WanxImageModelBuilderFactory;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.util.List;

import static dev.langchain4j.model.dashscope.WanxHelper.imageUrl;
import static dev.langchain4j.model.dashscope.WanxHelper.imagesFrom;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * Represents a Wanx models to generate artistic images.
 * More details are available <a href="https://help.aliyun.com/zh/dashscope/developer-reference/api-details-9">here</a>.
 */
public class WanxImageModel implements ImageModel {
    private final String apiKey;
    private final String modelName;
    // The generation method of the reference image. The optional values are
    // 'repaint' and 'refonly'; repaint represents the reference content and
    // refonly represents the reference style. Default is 'repaint'.
    private final WanxImageRefMode refMode;
    // The similarity between the expected output result and the reference image,
    // the value range is [0.0, 1.0]. The larger the number, the more similar the
    // generated result is to the reference image. Default is 0.5.
    private final Float refStrength;
    private final Integer seed;
    // The resolution of the generated image currently only supports '1024*1024',
    // '720*1280', and '1280*720' resolutions. Default is '1024*1024'.
    private final WanxImageSize size;
    private final WanxImageStyle style;
    private final ImageSynthesis imageSynthesis;

    @Builder
    public WanxImageModel(String baseUrl,
                          String apiKey,
                          String modelName,
                          WanxImageRefMode refMode,
                          Float refStrength,
                          Integer seed,
                          WanxImageSize size,
                          WanxImageStyle style) {
        if (Utils.isNullOrBlank(apiKey)) {
            throw new IllegalArgumentException("DashScope api key must be defined. It can be generated here: https://dashscope.console.aliyun.com/apiKey");
        }
        this.modelName = Utils.isNullOrBlank(modelName) ? WanxModelName.WANX_V1 : modelName;
        this.apiKey = apiKey;
        this.refMode = refMode;
        this.refStrength = refStrength;
        this.seed = seed;
        this.size = size;
        this.style = style;
        this.imageSynthesis = Utils.isNullOrBlank(baseUrl) ? new ImageSynthesis() : new ImageSynthesis("text2image", baseUrl);
    }

    @Override
    public Response<Image> generate(String prompt) {
        ImageSynthesisParam param = requestBuilder(prompt).n(1).build();

        try {
            ImageSynthesisResult result = imageSynthesis.call(param);
            return Response.from(imagesFrom(result).get(0));
        } catch (NoApiKeyException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Response<List<Image>> generate(String prompt, int n) {
        ImageSynthesisParam param = requestBuilder(prompt).n(n).build();

        try {
            ImageSynthesisResult result = imageSynthesis.call(param);
            return Response.from(imagesFrom(result));
        } catch (NoApiKeyException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Response<Image> edit(Image image, String prompt) {
        String imageUrl = imageUrl(image, modelName, apiKey);

        ImageSynthesisParam.ImageSynthesisParamBuilder<?, ?> builder = requestBuilder(prompt)
                .refImage(imageUrl)
                .n(1);

        if (imageUrl.startsWith("oss://")) {
            builder.header("X-DashScope-OssResourceResolve", "enable");
        }

        try {
            ImageSynthesisResult result = imageSynthesis.call(builder.build());
            return Response.from(imagesFrom(result).get(0));
        } catch (NoApiKeyException e) {
            throw new RuntimeException(e);
        }
    }

    private ImageSynthesisParam.ImageSynthesisParamBuilder<?, ?> requestBuilder(String prompt) {
        ImageSynthesisParam.ImageSynthesisParamBuilder<?, ?> builder = ImageSynthesisParam.builder()
                .apiKey(apiKey)
                .model(modelName)
                .prompt(prompt);

        if (seed != null) {
            builder.seed(seed);
        }

        if (size != null) {
            builder.size(size.toString());
        }

        if (style != null) {
            builder.style(style.toString());
        }

        if (refMode != null) {
            builder.parameter("ref_mode", refMode.toString());
        }

        if (refStrength != null) {
            builder.parameter("ref_strength", refStrength);
        }

        return builder;
    }

    public static WanxImageModel.WanxImageModelBuilder builder() {
        for (WanxImageModelBuilderFactory factory : loadFactories(WanxImageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new WanxImageModel.WanxImageModelBuilder();
    }

    public static class WanxImageModelBuilder {
        public WanxImageModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
