package dev.langchain4j.web.search.search_api;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
class SearchApiSearchRequest {

    // Required parameters
    private String apiKey;
    private String query;
    private Engine engine;

    // Optional parameters
    private Device device;
    private String location;
    private String uule;
    private GoogleDomain google_domain;
    private Country gl;
    private Language hl;
    private LanguageRestriction lr;
    private CountryRestriction cr;

    // Filters
    private Integer nfpr;
    private Integer filter;
    private SafeSearch safe;

    // Time period parameters
    private TimePeriod time_period;
    private String time_period_min;
    private String time_period_max;

    // Pagination
    private Integer num;
    private Integer page;

    public enum Engine {
        GOOGLE("google");

        private final String value;

        Engine(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public enum Device {
        DESKTOP("desktop"),
        MOBILE("mobile"),
        TABLET("tablet");

        private final String value;

        Device(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public enum GoogleDomain {
        GOOGLE_COM("google.com"),
        // TODO: Add all domains
        GOOGLE_FR("google.fr"),
        GOOGLE_DE("google.de");

        private final String value;

        GoogleDomain(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public enum Country {
        US("us"),
        // TODO: Add all countries
        FR("fr"),
        DE("de");

        private final String value;

        Country(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public enum Language {
        EN("en"),
        // TODO: Add all languages
        FR("fr"),
        DE("de");

        private final String value;

        Language(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public enum LanguageRestriction {
        LANG_EN("lang_en"),
        // TODO: Add all languages
        LANG_FR("lang_fr"),
        LANG_DE("lang_de");

        private final String value;

        LanguageRestriction(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public enum CountryRestriction {
        COUNTRY_US("countryUS"),
        // TODO: Add all countries
        COUNTRY_FR("countryFR"),
        COUNTRY_DE("countryDE");

        private final String value;

        CountryRestriction(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public enum SafeSearch {
        ACTIVE("active"),
        OFF("off");

        private final String value;

        SafeSearch(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public enum TimePeriod {
        LAST_HOUR("last_hour"),
        LAST_DAY("last_day"),
        LAST_WEEK("last_week"),
        LAST_MONTH("last_month"),
        LAST_YEAR("last_year");

        private final String value;

        TimePeriod(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
