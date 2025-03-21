package dev.langchain4j.model.chat.common;

import static dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities.SupportStatus.DISABLED;
import static dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities.SupportStatus.NOT_SUPPORTED;
import static dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities.SupportStatus.SUPPORTED;

public abstract class AbstractChatModelAndCapabilities<M> {

    private final M model;
    private final String mnemonicName;
    private final SupportStatus supportsModelNameParameter;
    private final SupportStatus supportsMaxOutputTokensParameter;
    private final SupportStatus supportsDefaultRequestParameters;
    private final SupportStatus supportsTools;
    private final SupportStatus supportsToolChoiceRequiredWithMultipleTools;
    private final SupportStatus supportsToolChoiceRequiredWithSingleTool;
    private final SupportStatus supportsToolChoiceRequired;
    private final SupportStatus supportsJsonResponseFormat;
    private final SupportStatus supportsJsonResponseFormatWithSchema;
    private final SupportStatus supportsSingleImageInputAsBase64EncodedString;
    private final SupportStatus supportsMultipleImageInputsAsBase64EncodedStrings;
    private final SupportStatus supportsSingleImageInputAsPublicURL;
    private final SupportStatus supportsMultipleImageInputsAsPublicURLs;
    private final SupportStatus supportsStopSequencesParameter;
    private final SupportStatus supportsCommonParametersWrappedInIntegrationSpecificClass;
    private final SupportStatus supportsToolsAndJsonResponseFormatWithSchema;
    private final boolean assertResponseId;
    private final boolean assertResponseModel;
    private final boolean assertTokenUsage;
    private final boolean assertFinishReason;
    private final boolean assertThreads;
    private final boolean assertExceptionType;
    private final boolean assertTimesOnPartialResponseWasCalled;

    protected AbstractChatModelAndCapabilities(AbstractBuilder<?, M> builder) {
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

    public SupportStatus supportsModelNameParameter() {
        return supportsModelNameParameter;
    }

    public SupportStatus supportsMaxOutputTokensParameter() {
        return supportsMaxOutputTokensParameter;
    }

    public SupportStatus supportsDefaultRequestParameters() {
        return supportsDefaultRequestParameters;
    }

    public SupportStatus supportsTools() {
        return supportsTools;
    }

    public SupportStatus supportsToolChoiceRequiredWithMultipleTools() {
        return supportsToolChoiceRequiredWithMultipleTools;
    }

    public SupportStatus supportsToolChoiceRequiredWithSingleTool() {
        return supportsToolChoiceRequiredWithSingleTool;
    }

    public SupportStatus supportsToolChoiceRequired() {
        return supportsToolChoiceRequired;
    }

    public SupportStatus supportsJsonResponseFormat() {
        return supportsJsonResponseFormat;
    }

    public SupportStatus supportsJsonResponseFormatWithSchema() {
        return supportsJsonResponseFormatWithSchema;
    }

    public SupportStatus supportsSingleImageInputAsBase64EncodedString() {
        return supportsSingleImageInputAsBase64EncodedString;
    }

    public SupportStatus supportsMultipleImageInputsAsBase64EncodedStrings() {
        return supportsMultipleImageInputsAsBase64EncodedStrings;
    }

    public SupportStatus supportsSingleImageInputAsPublicURL() {
        return supportsSingleImageInputAsPublicURL;
    }

    public SupportStatus supportsMultipleImageInputsAsPublicURLs() {
        return supportsMultipleImageInputsAsPublicURLs;
    }

    public SupportStatus supportsStopSequencesParameter() {
        return supportsStopSequencesParameter;
    }

    public SupportStatus supportsCommonParametersWrappedInIntegrationSpecificClass() {
        return supportsCommonParametersWrappedInIntegrationSpecificClass;
    }

    public SupportStatus supportsToolsAndJsonResponseFormatWithSchema() {
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
     * Generic and abstract builder for constructing an instance of ChatModelCapabilities.
     * The {@code self()} method returns the concrete type of the builder to facilitate chaining.
     */
    public abstract static class AbstractBuilder<T extends AbstractBuilder<T, M>, M> {
        protected M model;
        private String mnemonicName = null;
        private SupportStatus supportsModelNameParameter = SUPPORTED;
        private SupportStatus supportsMaxOutputTokensParameter = SUPPORTED;
        private SupportStatus supportsDefaultRequestParameters = SUPPORTED;
        private SupportStatus supportsTools = SUPPORTED;
        private SupportStatus supportsToolChoiceRequiredWithMultipleTools = SUPPORTED;
        private SupportStatus supportsToolChoiceRequiredWithSingleTool = SUPPORTED;
        private SupportStatus supportsToolChoiceRequired = SUPPORTED;
        private SupportStatus supportsJsonResponseFormat = SUPPORTED;
        private SupportStatus supportsJsonResponseFormatWithSchema = SUPPORTED;
        private SupportStatus supportsSingleImageInputAsBase64EncodedString = SUPPORTED;
        private SupportStatus supportsMultipleImageInputsAsBase64EncodedStrings = SUPPORTED;
        private SupportStatus supportsSingleImageInputAsPublicURL = SUPPORTED;
        private SupportStatus supportsMultipleImageInputsAsPublicURLs = SUPPORTED;
        private SupportStatus supportsStopSequencesParameter = SUPPORTED;
        private SupportStatus supportsCommonParametersWrappedInIntegrationSpecificClass = SUPPORTED;
        private SupportStatus supportsToolsAndJsonResponseFormatWithSchema = SUPPORTED;
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

        public T supportsModelNameParameter(SupportStatus value) {
            this.supportsModelNameParameter = value;
            return self();
        }

        public T supportsMaxOutputTokensParameter(SupportStatus value) {
            this.supportsMaxOutputTokensParameter = value;
            return self();
        }

        public T supportsDefaultRequestParameters(SupportStatus value) {
            this.supportsDefaultRequestParameters = value;
            return self();
        }

        /**
         * if tools support is set to NOT_SUPPORTED or DISABLED : <br />
         * - supportsToolChoiceRequiredWithMultipleTools<br />
         * - supportsToolChoiceRequiredWithSingleTool<br />
         * - supportsToolChoiceRequired<br />
         * - supportsToolsAndJsonResponseFormatWithSchema<br />
         * will also be set to NOT_SUPPORTED or DISABLED
         *
         * @param value the tools support status
         */
        public T supportsTools(SupportStatus value) {
            this.supportsTools = value;
            if (value.equals(NOT_SUPPORTED)) {
                this.supportsToolChoiceRequiredWithMultipleTools = NOT_SUPPORTED;
                this.supportsToolChoiceRequiredWithSingleTool = NOT_SUPPORTED;
                this.supportsToolChoiceRequired = NOT_SUPPORTED;
                this.supportsToolsAndJsonResponseFormatWithSchema = NOT_SUPPORTED;
            }
            if (value.equals(DISABLED)) {
                this.supportsToolChoiceRequiredWithMultipleTools = DISABLED;
                this.supportsToolChoiceRequiredWithSingleTool = DISABLED;
                this.supportsToolChoiceRequired = DISABLED;
                this.supportsToolsAndJsonResponseFormatWithSchema = DISABLED;
            }
            return self();
        }

        public T supportsToolChoiceRequiredWithMultipleTools(SupportStatus value) {
            this.supportsToolChoiceRequiredWithMultipleTools = value;
            return self();
        }

        public T supportsToolChoiceRequiredWithSingleTool(SupportStatus value) {
            this.supportsToolChoiceRequiredWithSingleTool = value;
            return self();
        }

        /**
         * if tool choice required support is set to NOT_SUPPORTED or DISABLED : <br />
         * - supportsToolChoiceRequiredWithMultipleTools<br />
         * - supportsToolChoiceRequiredWithSingleTool<br />
         * will also be set to NOT_SUPPORTED or DISABLED
         *
         * @param value the tool choice required support status
         */
        public T supportsToolChoiceRequired(SupportStatus value) {
            this.supportsToolChoiceRequired = value;
            if (value.equals(NOT_SUPPORTED)) {
                this.supportsToolChoiceRequiredWithMultipleTools = NOT_SUPPORTED;
                this.supportsToolChoiceRequiredWithSingleTool = NOT_SUPPORTED;
            }
            if (value.equals(DISABLED)) {
                this.supportsToolChoiceRequiredWithMultipleTools = DISABLED;
                this.supportsToolChoiceRequiredWithSingleTool = DISABLED;
            }
            return self();
        }

        public T supportsJsonResponseFormat(SupportStatus value) {
            this.supportsJsonResponseFormat = value;
            return self();
        }

        /**
         * if json response format with schema support is set to NOT_SUPPORTED or DISABLED : <br />
         * - supportsToolsAndJsonResponseFormatWithSchema<br />
         * will also be set to NOT_SUPPORTED or DISABLED
         *
         * @param value the json response format with schema support status
         */
        public T supportsJsonResponseFormatWithSchema(SupportStatus value) {
            this.supportsJsonResponseFormatWithSchema = value;
            if (value.equals(NOT_SUPPORTED)) {
                this.supportsToolsAndJsonResponseFormatWithSchema = NOT_SUPPORTED;
            }
            if (value.equals(DISABLED)) {
                this.supportsToolsAndJsonResponseFormatWithSchema = DISABLED;
            }
            return self();
        }

        /**
         * if image as B64 encoding support is set to NOT_SUPPORTED or DISABLED : <br />
         * - supportsMultipleImageInputsAsBase64EncodedStrings<br />
         * will also be set to NOT_SUPPORTED or DISABLED
         *
         * @param value the image as B64 encoding support status
         */
        public T supportsSingleImageInputAsBase64EncodedString(SupportStatus value) {
            this.supportsSingleImageInputAsBase64EncodedString = value;
            if (value.equals(NOT_SUPPORTED)) {
                this.supportsMultipleImageInputsAsBase64EncodedStrings = NOT_SUPPORTED;
            }
            if (value.equals(DISABLED)) {
                this.supportsMultipleImageInputsAsBase64EncodedStrings = DISABLED;
            }
            return self();
        }

        public T supportsMultipleImageInputsAsBase64EncodedStrings(SupportStatus value) {
            this.supportsMultipleImageInputsAsBase64EncodedStrings = value;
            return self();
        }

        /**
         * if image as public URL support is set to NOT_SUPPORTED or DISABLED : <br />
         * - supportsMultipleImageInputsAsPublicURLs<br />
         * will also be set to NOT_SUPPORTED or DISABLED
         *
         * @param value the image as public URL support status
         */
        public T supportsSingleImageInputAsPublicURL(SupportStatus value) {
            this.supportsSingleImageInputAsPublicURL = value;
            if (value.equals(NOT_SUPPORTED)) {
                this.supportsMultipleImageInputsAsPublicURLs = NOT_SUPPORTED;
            }
            if (value.equals(DISABLED)) {
                this.supportsMultipleImageInputsAsPublicURLs = DISABLED;
            }
            return self();
        }

        public T supportsMultipleImageInputsAsPublicURLs(SupportStatus value) {
            this.supportsMultipleImageInputsAsPublicURLs = value;
            return self();
        }

        public T supportsStopSequencesParameter(SupportStatus value) {
            this.supportsStopSequencesParameter = value;
            return self();
        }

        public T supportsCommonParametersWrappedInIntegrationSpecificClass(SupportStatus value) {
            this.supportsCommonParametersWrappedInIntegrationSpecificClass = value;
            return self();
        }

        public T supportsToolsAndJsonResponseFormatWithSchema(SupportStatus value) {
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

        protected abstract T self();

        public abstract AbstractChatModelAndCapabilities<M> build();
    }

    public enum SupportStatus {
        SUPPORTED,
        NOT_SUPPORTED,
        /**
         * Tests relative to this functionnality will be disabled.
         * This value should only be used in cases that are too complex to resolve or during the development phase.
         * Its presence must be justified during code reviews.
         */
        DISABLED
    }
}
