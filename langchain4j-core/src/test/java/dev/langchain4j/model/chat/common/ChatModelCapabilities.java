package dev.langchain4j.model.chat.common;

import static dev.langchain4j.model.chat.common.ChatModelCapabilities.Capability.DISABLED;
import static dev.langchain4j.model.chat.common.ChatModelCapabilities.Capability.FAIL;
import static dev.langchain4j.model.chat.common.ChatModelCapabilities.Capability.SUPPORT;

public abstract class ChatModelCapabilities<M> {

    private final M model;
    private final String mnemonicName;
    private final Capability supportsModelNameParameter;
    private final Capability supportsMaxOutputTokensParameter;
    private final Capability supportsDefaultRequestParameters;
    private final Capability supportsTools;
    private final Capability supportsToolChoiceRequiredWithMultipleTools;
    private final Capability supportsToolChoiceRequiredWithSingleTool;
    private final Capability supportsToolChoiceRequired;
    private final Capability supportsJsonResponseFormat;
    private final Capability supportsJsonResponseFormatWithSchema;
    private final Capability supportsSingleImageInputAsBase64EncodedString;
    private final Capability supportsMultipleImageInputsAsBase64EncodedStrings;
    private final Capability supportsSingleImageInputAsPublicURL;
    private final Capability supportsMultipleImageInputsAsPublicURLs;
    private final Capability supportsStopSequencesParameter;
    private final Capability supportsCommonParametersWrappedInIntegrationSpecificClass;
    private final Capability supportsToolsAndJsonResponseFormatWithSchema;
    private final boolean assertResponseId;
    private final boolean assertResponseModel;
    private final boolean assertTokenUsage;
    private final boolean assertFinishReason;
    private final boolean assertThreads;
    private final boolean assertExceptionType;
    private final boolean assertTimesOnPartialResponseWasCalled;

    /**
     * Constructeur protégé qui initialise les champs à partir du builder.
     */
    protected ChatModelCapabilities(AbstractBuilder<?, M> builder) {
        this.model = builder.model;
        this.supportsModelNameParameter = builder.supportsModelNameParameter;
        this.mnemonicName = builder.mnemonicName;
        this.supportsMaxOutputTokensParameter = builder.supportsMaxOutputTokensParameter;
        this.supportsDefaultRequestParameters = builder.supportsDefaultRequestParameters;
        this.supportsTools = builder.supportsTools;
        this.supportsToolChoiceRequiredWithMultipleTools = builder.supportsToolChoiceRequiredWithMultipleTools;
        this.supportsToolChoiceRequiredWithSingleTool = builder.supportsToolChoiceRequiredWithSingleTool;
        this.supportsToolChoiceRequired = builder.supportsToolChoiceRequired;
        this.supportsJsonResponseFormat = builder.supportsJsonResponseFormat;
        this.supportsJsonResponseFormatWithSchema = builder.supportsJsonResponseFormatWithSchema;
        this.supportsSingleImageInputAsBase64EncodedString = builder.supportsSingleImageInputAsBase64EncodedString;
        this.supportsMultipleImageInputsAsBase64EncodedStrings =
                builder.supportsMultipleImageInputsAsBase64EncodedStrings;
        this.supportsSingleImageInputAsPublicURL = builder.supportsSingleImageInputAsPublicURL;
        this.supportsMultipleImageInputsAsPublicURLs = builder.supportsMultipleImageInputsAsPublicURLs;
        this.supportsStopSequencesParameter = builder.supportsStopSequencesParameter;
        this.supportsCommonParametersWrappedInIntegrationSpecificClass =
                builder.supportsCommonParametersWrappedInIntegrationSpecificClass;
        this.supportsToolsAndJsonResponseFormatWithSchema = builder.supportsToolsAndJsonResponseFormatWithSchema;
        this.assertResponseId = builder.assertResponseId;
        this.assertResponseModel = builder.assertResponseModel;
        this.assertTokenUsage = builder.assertTokenUsage;
        this.assertFinishReason = builder.assertFinishReason;
        this.assertThreads = builder.assertThreads;
        this.assertExceptionType = builder.assertExceptionType;
        this.assertTimesOnPartialResponseWasCalled = builder.assertTimesOnPartialResponseWasCalled;
    }

    public M model() {
        return model;
    }

    public String mnemonicName() {
        return mnemonicName;
    }

    public Capability supportsModelNameParameter() {
        return supportsModelNameParameter;
    }

    public Capability supportsMaxOutputTokensParameter() {
        return supportsMaxOutputTokensParameter;
    }

    public Capability supportsDefaultRequestParameters() {
        return supportsDefaultRequestParameters;
    }

    public Capability supportsTools() {
        return supportsTools;
    }

    public Capability supportsToolChoiceRequiredWithMultipleTools() {
        return supportsToolChoiceRequiredWithMultipleTools;
    }

    public Capability supportsToolChoiceRequiredWithSingleTool() {
        return supportsToolChoiceRequiredWithSingleTool;
    }

    public Capability supportsToolChoiceRequired() {
        return supportsToolChoiceRequired;
    }

    public Capability supportsJsonResponseFormat() {
        return supportsJsonResponseFormat;
    }

    public Capability supportsJsonResponseFormatWithSchema() {
        return supportsJsonResponseFormatWithSchema;
    }

    public Capability supportsSingleImageInputAsBase64EncodedString() {
        return supportsSingleImageInputAsBase64EncodedString;
    }

    public Capability supportsMultipleImageInputsAsBase64EncodedStrings() {
        return supportsMultipleImageInputsAsBase64EncodedStrings;
    }

    public Capability supportsSingleImageInputAsPublicURL() {
        return supportsSingleImageInputAsPublicURL;
    }

    public Capability supportsMultipleImageInputsAsPublicURLs() {
        return supportsMultipleImageInputsAsPublicURLs;
    }

    public Capability supportsStopSequencesParameter() {
        return supportsStopSequencesParameter;
    }

    public Capability supportsCommonParametersWrappedInIntegrationSpecificClass() {
        return supportsCommonParametersWrappedInIntegrationSpecificClass;
    }

    public Capability supportsToolsAndJsonResponseFormatWithSchema() {
        return supportsToolsAndJsonResponseFormatWithSchema;
    }

    public boolean assertResponseId() {
        return assertResponseId;
    }

    public boolean assertResponseModel() {
        return assertResponseModel;
    }

    public boolean assertTokenUsage() {
        return assertTokenUsage;
    }

    public boolean assertFinishReason() {
        return assertFinishReason;
    }

    public boolean assertThreads() {
        return assertThreads;
    }

    public boolean assertExceptionType() {
        return assertExceptionType;
    }

    public boolean assertTimesOnPartialResponseWasCalled() {
        return assertTimesOnPartialResponseWasCalled;
    }

    /**
     * Builder générique et abstrait permettant de construire une instance de ChatModelCapabilities.
     * La méthode {@code self()} permet de retourner le type concret du builder afin de faciliter le chainage.
     */
    public abstract static class AbstractBuilder<T extends AbstractBuilder<T, M>, M> {
        protected M model;
        private String mnemonicName = null;
        private Capability supportsModelNameParameter = SUPPORT;
        private Capability supportsMaxOutputTokensParameter = SUPPORT;
        private Capability supportsDefaultRequestParameters = SUPPORT;
        private Capability supportsTools = SUPPORT;
        private Capability supportsToolChoiceRequiredWithMultipleTools = SUPPORT;
        private Capability supportsToolChoiceRequiredWithSingleTool = SUPPORT;
        private Capability supportsToolChoiceRequired = SUPPORT;
        private Capability supportsJsonResponseFormat = SUPPORT;
        private Capability supportsJsonResponseFormatWithSchema = SUPPORT;
        private Capability supportsSingleImageInputAsBase64EncodedString = SUPPORT;
        private Capability supportsMultipleImageInputsAsBase64EncodedStrings = SUPPORT;
        private Capability supportsSingleImageInputAsPublicURL = SUPPORT;
        private Capability supportsMultipleImageInputsAsPublicURLs = SUPPORT;
        private Capability supportsStopSequencesParameter = SUPPORT;
        private Capability supportsCommonParametersWrappedInIntegrationSpecificClass = SUPPORT;
        private Capability supportsToolsAndJsonResponseFormatWithSchema = SUPPORT;
        private boolean assertResponseId = true;
        private boolean assertResponseModel = true;
        private boolean assertTokenUsage = true;
        private boolean assertFinishReason = true;
        private boolean assertThreads = true;
        private boolean assertExceptionType = true;
        private boolean assertTimesOnPartialResponseWasCalled = true;

        public T model(M model) {
            this.model = model;
            return self();
        }

        /**
         * Sets a mnemonic name for this object.
         * <p>
         * This name will be used by the object's toString() method, making it particularly useful
         * in parameterized tests where test execution names are derived from the toString()
         * representation of test parameters.
         * <p>
         * For example, in a test like:
         * <pre>
         * {@code
         * @ParameterizedTest
         * @MethodSource("provideObjects")
         * void myTest(MyObject obj) {
         *     // test logic
         * }
         * }
         * </pre>
         * The test execution will be named using this mnemonic name if set.
         *
         * @param value the name to be used for identification in test reports
         */
        public T mnemonicName(String value) {
            this.mnemonicName = value;
            return self();
        }

        public T supportsModelNameParameter(Capability value) {
            this.supportsModelNameParameter = value;
            return self();
        }

        public T supportsMaxOutputTokensParameter(Capability value) {
            this.supportsMaxOutputTokensParameter = value;
            return self();
        }

        public T supportsDefaultRequestParameters(Capability value) {
            this.supportsDefaultRequestParameters = value;
            return self();
        }

        public T supportsTools(Capability value) {
            this.supportsTools = value;
            if (value.equals(FAIL)) {
                this.supportsToolChoiceRequiredWithMultipleTools = FAIL;
                this.supportsToolChoiceRequiredWithSingleTool = FAIL;
                this.supportsToolChoiceRequired = FAIL;
                this.supportsToolsAndJsonResponseFormatWithSchema = FAIL;
            }
            if (value.equals(DISABLED)) {
                this.supportsToolChoiceRequiredWithMultipleTools = DISABLED;
                this.supportsToolChoiceRequiredWithSingleTool = DISABLED;
                this.supportsToolChoiceRequired = DISABLED;
                this.supportsToolsAndJsonResponseFormatWithSchema = DISABLED;
            }
            return self();
        }

        public T supportsToolChoiceRequiredWithMultipleTools(Capability value) {
            this.supportsToolChoiceRequiredWithMultipleTools = value;
            return self();
        }

        public T supportsToolChoiceRequiredWithSingleTool(Capability value) {
            this.supportsToolChoiceRequiredWithSingleTool = value;
            return self();
        }

        public T supportsToolChoiceRequired(Capability value) {
            this.supportsToolChoiceRequired = value;
            if (value.equals(FAIL)) {
                this.supportsToolChoiceRequiredWithMultipleTools = FAIL;
                this.supportsToolChoiceRequiredWithSingleTool = FAIL;
            }
            if (value.equals(DISABLED)) {
                this.supportsToolChoiceRequiredWithMultipleTools = DISABLED;
                this.supportsToolChoiceRequiredWithSingleTool = DISABLED;
            }
            return self();
        }

        public T supportsJsonResponseFormat(Capability value) {
            this.supportsJsonResponseFormat = value;
            return self();
        }

        public T supportsJsonResponseFormatWithSchema(Capability value) {
            this.supportsJsonResponseFormatWithSchema = value;
            if (value.equals(FAIL)) {
                this.supportsToolsAndJsonResponseFormatWithSchema = FAIL;
            }
            if (value.equals(DISABLED)) {
                this.supportsToolsAndJsonResponseFormatWithSchema = DISABLED;
            }
            return self();
        }

        public T supportsSingleImageInputAsBase64EncodedString(Capability value) {
            this.supportsSingleImageInputAsBase64EncodedString = value;
            if (value.equals(FAIL)) {
                this.supportsMultipleImageInputsAsBase64EncodedStrings = FAIL;
            }
            if (value.equals(DISABLED)) {
                this.supportsMultipleImageInputsAsBase64EncodedStrings = DISABLED;
            }
            return self();
        }

        public T supportsMultipleImageInputsAsBase64EncodedStrings(Capability value) {
            this.supportsMultipleImageInputsAsBase64EncodedStrings = value;
            return self();
        }

        public T supportsSingleImageInputAsPublicURL(Capability value) {
            this.supportsSingleImageInputAsPublicURL = value;
            if (value.equals(FAIL)) {
                this.supportsMultipleImageInputsAsPublicURLs = FAIL;
            }
            if (value.equals(DISABLED)) {
                this.supportsMultipleImageInputsAsPublicURLs = DISABLED;
            }
            return self();
        }

        public T supportsMultipleImageInputsAsPublicURLs(Capability value) {
            this.supportsMultipleImageInputsAsPublicURLs = value;
            return self();
        }

        public T supportsStopSequencesParameter(Capability value) {
            this.supportsStopSequencesParameter = value;
            return self();
        }

        public T supportsCommonParametersWrappedInIntegrationSpecificClass(Capability value) {
            this.supportsCommonParametersWrappedInIntegrationSpecificClass = value;
            return self();
        }

        public T supportsToolsAndJsonResponseFormatWithSchema(Capability value) {
            this.supportsToolsAndJsonResponseFormatWithSchema = value;
            return self();
        }

        public T assertResponseId(boolean value) {
            this.assertResponseId = value;
            return self();
        }

        public T assertResponseModel(boolean value) {
            this.assertResponseModel = value;
            return self();
        }

        public T assertTokenUsage(boolean value) {
            this.assertTokenUsage = value;
            return self();
        }

        public T assertFinishReason(boolean value) {
            this.assertFinishReason = value;
            return self();
        }

        public T assertThreads(boolean value) {
            this.assertThreads = value;
            return self();
        }

        public T assertExceptionType(boolean value) {
            this.assertExceptionType = value;
            return self();
        }

        public T assertTimesOnPartialResponseWasCalled(boolean value) {
            this.assertTimesOnPartialResponseWasCalled = value;
            return self();
        }

        /**
         * Méthode permettant de retourner le builder concret.
         */
        protected abstract T self();

        /**
         * Construit l'instance concrète de ChatModelCapabilities.
         */
        public abstract ChatModelCapabilities<M> build();
    }

    public enum Capability {
        SUPPORT,
        FAIL,
        DISABLED
    }
}
