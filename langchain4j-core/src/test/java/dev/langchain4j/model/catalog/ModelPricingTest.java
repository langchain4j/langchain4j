package dev.langchain4j.model.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class ModelPricingTest {

    @Test
    void should_build_pricing_with_all_fields() {
        ModelPricing pricing = ModelPricing.builder()
                .inputPricePerMillionTokens(new BigDecimal("3.00"))
                .outputPricePerMillionTokens(new BigDecimal("15.00"))
                .currency("USD")
                .pricingUrl("https://example.com/pricing")
                .build();

        assertThat(pricing.getInputPricePerMillionTokens()).isEqualTo(new BigDecimal("3.00"));
        assertThat(pricing.getOutputPricePerMillionTokens()).isEqualTo(new BigDecimal("15.00"));
        assertThat(pricing.getCurrency()).isEqualTo(Currency.getInstance("USD"));
        assertThat(pricing.getPricingUrl()).isEqualTo("https://example.com/pricing");
    }

    @Test
    void should_default_to_usd_when_currency_not_specified() {
        ModelPricing pricing = ModelPricing.builder()
                .inputPricePerMillionTokens(new BigDecimal("1.00"))
                .outputPricePerMillionTokens(new BigDecimal("2.00"))
                .build();

        assertThat(pricing.getCurrency()).isEqualTo(Currency.getInstance("USD"));
    }

    @Test
    void should_support_currency_object() {
        Currency eur = Currency.getInstance("EUR");

        ModelPricing pricing = ModelPricing.builder()
                .inputPricePerMillionTokens(new BigDecimal("2.50"))
                .outputPricePerMillionTokens(new BigDecimal("10.00"))
                .currency(eur)
                .build();

        assertThat(pricing.getCurrency()).isEqualTo(eur);
    }

    @Test
    void should_implement_equals_and_hashCode() {
        ModelPricing pricing1 = ModelPricing.builder()
                .inputPricePerMillionTokens(new BigDecimal("3.00"))
                .outputPricePerMillionTokens(new BigDecimal("15.00"))
                .currency("USD")
                .build();

        ModelPricing pricing2 = ModelPricing.builder()
                .inputPricePerMillionTokens(new BigDecimal("3.00"))
                .outputPricePerMillionTokens(new BigDecimal("15.00"))
                .currency("USD")
                .build();

        ModelPricing pricing3 = ModelPricing.builder()
                .inputPricePerMillionTokens(new BigDecimal("1.00"))
                .outputPricePerMillionTokens(new BigDecimal("5.00"))
                .currency("USD")
                .build();

        assertThat(pricing1).isEqualTo(pricing2);
        assertThat(pricing1).hasSameHashCodeAs(pricing2);
        assertThat(pricing1).isNotEqualTo(pricing3);
    }

    @Test
    void should_implement_toString() {
        ModelPricing pricing = ModelPricing.builder()
                .inputPricePerMillionTokens(new BigDecimal("3.00"))
                .outputPricePerMillionTokens(new BigDecimal("15.00"))
                .currency("USD")
                .build();

        String str = pricing.toString();

        assertThat(str).contains("3.00");
        assertThat(str).contains("15.00");
        assertThat(str).contains("USD");
        assertThat(str).contains("per 1M tokens");
    }

    @Test
    void should_handle_null_pricing_fields() {
        ModelPricing pricing = ModelPricing.builder().build();

        assertThat(pricing.getInputPricePerMillionTokens()).isNull();
        assertThat(pricing.getOutputPricePerMillionTokens()).isNull();
        assertThat(pricing.getCurrency()).isEqualTo(Currency.getInstance("USD"));
        assertThat(pricing.getPricingUrl()).isNull();
    }
}
