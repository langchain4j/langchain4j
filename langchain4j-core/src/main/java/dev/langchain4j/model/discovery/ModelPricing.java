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

    /**
     * Cost per 1 million input (prompt) tokens.
     * May be null if input pricing is not available or not applicable.
     */
    public BigDecimal getInputPricePerMillionTokens() {
        return inputPricePerMillionTokens;
    }

    /**
     * Cost per 1 million output (completion) tokens.
     * May be null if output pricing is not available or not applicable.
     */
    public BigDecimal getOutputPricePerMillionTokens() {
        return outputPricePerMillionTokens;
    }

    /**
     * Currency in which prices are denominated.
     * Defaults to USD if not explicitly set.
     *
     * @return the currency, never null
     */
    public Currency getCurrency() {
        return currency;
    }

    /**
     * URL to the provider's official pricing page for detailed or updated pricing information.
     * May be null if no pricing URL is available.
     */
    public String getPricingUrl() {
        return pricingUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModelPricing)) return false;
        ModelPricing that = (ModelPricing) o;
        return Objects.equals(inputPricePerMillionTokens, that.inputPricePerMillionTokens)
                && Objects.equals(outputPricePerMillionTokens, that.outputPricePerMillionTokens)
                && Objects.equals(currency, that.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inputPricePerMillionTokens, outputPricePerMillionTokens, currency);
    }

    @Override
    public String toString() {
        return "ModelPricing{" + "input="
                + inputPricePerMillionTokens + ", output="
                + outputPricePerMillionTokens + " "
                + currency.getCurrencyCode() + " per 1M tokens"
                + '}';
    }

    public static class Builder {
        private BigDecimal inputPricePerMillionTokens;
        private BigDecimal outputPricePerMillionTokens;
        private Currency currency;
        private String pricingUrl;

        /**
         * Sets the cost per 1 million input (prompt) tokens.
         *
         * @param inputPricePerMillionTokens the price, may be null
         */
        public Builder inputPricePerMillionTokens(BigDecimal inputPricePerMillionTokens) {
            this.inputPricePerMillionTokens = inputPricePerMillionTokens;
            return this;
        }

        /**
         * Sets the cost per 1 million output (completion) tokens.
         *
         * @param outputPricePerMillionTokens the price, may be null
         */
        public Builder outputPricePerMillionTokens(BigDecimal outputPricePerMillionTokens) {
            this.outputPricePerMillionTokens = outputPricePerMillionTokens;
            return this;
        }

        /**
         * Sets the currency in which prices are denominated.
         * If not set, defaults to USD.
         *
         * @param currency the currency
         */
        public Builder currency(Currency currency) {
            this.currency = currency;
            return this;
        }

        /**
         * Sets the currency in which prices are denominated using an ISO 4217 currency code.
         * If not set, defaults to USD.
         *
         * @param currencyCode ISO 4217 currency code (e.g., "USD", "EUR")
         * @throws IllegalArgumentException if the currency code is invalid
         */
        public Builder currency(String currencyCode) {
            this.currency = Currency.getInstance(currencyCode);
            return this;
        }

        /**
         * Sets the URL to the provider's official pricing page.
         *
         * @param pricingUrl the URL, may be null
         */
        public Builder pricingUrl(String pricingUrl) {
            this.pricingUrl = pricingUrl;
            return this;
        }

        public ModelPricing build() {
            return new ModelPricing(this);
        }
    }
}
