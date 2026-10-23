package dev.langchain4j.agentic.decision;

import static dev.langchain4j.agentic.decision.DecisionModels.openAi;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.decision.DecisionAgents.BannerCheck;
import dev.langchain4j.data.message.ImageContent;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Scenario 7: a decision agent judges an image produced by an earlier step, here a generated banner with or
 * without text, with the OpenAI decision model, the provider tested with images.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class DecisionImageCheckIT {

    private static ImageContent banner(String text) {
        BufferedImage image = new BufferedImage(600, 160, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(250, 240, 200));
        graphics.fillRect(0, 0, 600, 160);
        graphics.setColor(new Color(30, 90, 160));
        graphics.fillOval(420, 20, 120, 120);
        if (text != null) {
            graphics.setColor(Color.BLACK);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 56));
            graphics.drawString(text, 20, 100);
        }
        graphics.dispose();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bytes);
            return ImageContent.from(Base64.getEncoder().encodeToString(bytes.toByteArray()), "image/png");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static UntypedAgent bannerCheck() {
        BannerCheck check = AgenticServices.decisionAgentBuilder(BannerCheck.class)
                .decisionModel(openAi())
                .build();
        return AgenticServices.sequenceBuilder()
                .subAgents(check)
                .outputKey("containsText")
                .build();
    }

    @Test
    void detects_the_text_of_a_banner() {
        assertThat(bannerCheck().invoke(Map.of("banner", banner("SUMMER SALE"))))
                .isEqualTo(true);
    }

    @Test
    void accepts_a_banner_without_text() {
        assertThat(bannerCheck().invoke(Map.of("banner", banner(null)))).isEqualTo(false);
    }
}
