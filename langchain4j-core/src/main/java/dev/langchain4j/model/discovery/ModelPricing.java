package dev.langchain4j.model.discovery;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * Represents pricing information for a model.
 *
 * <p>Prices are typically expressed per 1 million tokens.
 * Different pricing may apply for input (prompt) tokens and output (completion) tokens.
 */
public class ModelPricing {

    private final BigDecimal inputPricePerMillionTokens;
    private final BigDecimal outputPricePerMillionTokens;
    private final Currency currency;
    private final String pricingUrl;

    private ModelPricing(Builder builder) {
        this.inputPricePerMillionTokens = builder.inputPricePerMillionTokens;
        this.outputPricePerMillionTokens = builder.outputPricePerMillionTokens;
        this.currency = builder.currency != null ? builder.currency : Currency.getInstance("USD");
        this.pricingUrl = builder.pricingUrl;
    }

    public static Builder builder() {
        return new Builder();
    }

    public BigDecimal getInputPricePerMillionTokens() {
        return inputPricePerMillionTokens;
    }

    public BigDecimal getOutputPricePerMillionTokens() {
        return outputPricePerMillionTokens;
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getPricingUrl() {
        return pricingUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModelPricing)) return false;
        ModelPricing that = (ModelPricing) o;
        return Objects.equals(inputPricePerMillionTokens, that.inputPricePerMillionTokens) &&
               Objects.equals(outputPricePerMillionTokens, that.outputPricePerMillionTokens) &&
               Objects.equals(currency, that.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inputPricePerMillionTokens, outputPricePerMillionTokens, currency);
    }

    @Override
    public String toString() {
        return "ModelPricing{" +
               "input=" + inputPricePerMillionTokens +
               ", output=" + outputPricePerMillionTokens +
               " " + currency.getCurrencyCode() +
               " per 1M tokens" +
               '}';
    }

    public static class Builder {
        private BigDecimal inputPricePerMillionTokens;
        private BigDecimal outputPricePerMillionTokens;
        private Currency currency;
        private String pricingUrl;

        public Builder inputPricePerMillionTokens(BigDecimal inputPricePerMillionTokens) {
            this.inputPricePerMillionTokens = inputPricePerMillionTokens;
            return this;
        }

        public Builder outputPricePerMillionTokens(BigDecimal outputPricePerMillionTokens) {
            this.outputPricePerMillionTokens = outputPricePerMillionTokens;
            return this;
        }

        public Builder currency(Currency currency) {
            this.currency = currency;
            return this;
        }

        public Builder currency(String currencyCode) {
            this.currency = Currency.getInstance(currencyCode);
            return this;
        }

        public Builder pricingUrl(String pricingUrl) {
            this.pricingUrl = pricingUrl;
            return this;
        }

        public ModelPricing build() {
            return new ModelPricing(this);
        }
    }
}
