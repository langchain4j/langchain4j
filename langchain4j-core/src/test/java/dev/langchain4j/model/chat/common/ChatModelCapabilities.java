package dev.langchain4j.model.chat.common;

public abstract class ChatModelCapabilities<M> {

    private final M model;
    private final boolean supportsModelNameParameter;
    private final boolean supportsMaxOutputTokensParameter;
    private final boolean supportsDefaultRequestParameters;
    private final boolean supportsTools;
    private final boolean supportsToolChoiceRequiredWithMultipleTools;
    private final boolean supportsToolChoiceRequiredWithSingleTool;
    private final boolean supportsToolChoiceRequired;
    private final boolean supportsJsonResponseFormat;
    private final boolean supportsJsonResponseFormatWithSchema;
    private final boolean supportsSingleImageInputAsBase64EncodedString;
    private final boolean supportsMultipleImageInputsAsBase64EncodedStrings;
    private final boolean supportsSingleImageInputAsPublicURL;
    private final boolean supportsMultipleImageInputsAsPublicURLs;
    private final boolean supportsStopSequencesParameter;
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
        this.assertResponseId = builder.assertResponseId;
        this.assertResponseModel = builder.assertResponseModel;
        this.assertTokenUsage = builder.assertTokenUsage;
        this.assertFinishReason = builder.assertFinishReason;
        this.assertThreads = builder.assertThreads;
        this.assertExceptionType = builder.assertExceptionType;
        this.assertTimesOnPartialResponseWasCalled = builder.assertTimesOnPartialResponseWasCalled;
    }

    // Getters pour chaque champ
    public M model() {
        return model;
    }

    public boolean supportsModelNameParameter() {
        return supportsModelNameParameter;
    }

    public boolean supportsMaxOutputTokensParameter() {
        return supportsMaxOutputTokensParameter;
    }

    public boolean supportsDefaultRequestParameters() {
        return supportsDefaultRequestParameters;
    }

    public boolean supportsTools() {
        return supportsTools;
    }

    public boolean supportsToolChoiceRequiredWithMultipleTools() {
        return supportsToolChoiceRequiredWithMultipleTools;
    }

    public boolean supportsToolChoiceRequiredWithSingleTool() {
        return supportsToolChoiceRequiredWithSingleTool;
    }

    public boolean supportsToolChoiceRequired() {
        return supportsToolChoiceRequired;
    }

    public boolean supportsJsonResponseFormat() {
        return supportsJsonResponseFormat;
    }

    public boolean supportsJsonResponseFormatWithSchema() {
        return supportsJsonResponseFormatWithSchema;
    }

    public boolean supportsSingleImageInputAsBase64EncodedString() {
        return supportsSingleImageInputAsBase64EncodedString;
    }

    public boolean supportsMultipleImageInputsAsBase64EncodedStrings() {
        return supportsMultipleImageInputsAsBase64EncodedStrings;
    }

    public boolean supportsSingleImageInputAsPublicURL() {
        return supportsSingleImageInputAsPublicURL;
    }

    public boolean supportsMultipleImageInputsAsPublicURLs() {
        return supportsMultipleImageInputsAsPublicURLs;
    }

    public boolean supportsStopSequencesParameter() {
        return supportsStopSequencesParameter;
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
        private boolean supportsModelNameParameter = true;
        private boolean supportsMaxOutputTokensParameter = true;
        private boolean supportsDefaultRequestParameters = true;
        private boolean supportsTools = true;
        private boolean supportsToolChoiceRequiredWithMultipleTools = true;
        private boolean supportsToolChoiceRequiredWithSingleTool = true;
        private boolean supportsToolChoiceRequired = true;
        private boolean supportsJsonResponseFormat = true;
        private boolean supportsJsonResponseFormatWithSchema = true;
        private boolean supportsSingleImageInputAsBase64EncodedString = true;
        private boolean supportsMultipleImageInputsAsBase64EncodedStrings = true;
        private boolean supportsSingleImageInputAsPublicURL = true;
        private boolean supportsMultipleImageInputsAsPublicURLs = true;
        private boolean supportsStopSequencesParameter = true;
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

        public T supportsModelNameParameter(boolean value) {
            this.supportsModelNameParameter = value;
            return self();
        }

        public T supportsMaxOutputTokensParameter(boolean value) {
            this.supportsMaxOutputTokensParameter = value;
            return self();
        }

        public T supportsDefaultRequestParameters(boolean value) {
            this.supportsDefaultRequestParameters = value;
            return self();
        }

        public T supportsTools(boolean value) {
            this.supportsTools = value;
            if (!value) {
                this.supportsToolChoiceRequiredWithMultipleTools = false;
                this.supportsToolChoiceRequiredWithSingleTool = false;
                this.supportsToolChoiceRequired = false;
            }
            return self();
        }

        public T supportsToolChoiceRequiredWithMultipleTools(boolean value) {
            this.supportsToolChoiceRequiredWithMultipleTools = value;
            return self();
        }

        public T supportsToolChoiceRequiredWithSingleTool(boolean value) {
            this.supportsToolChoiceRequiredWithSingleTool = value;
            return self();
        }

        public T supportsToolChoiceRequired(boolean value) {
            this.supportsToolChoiceRequired = value;
            if (!value) {
                this.supportsToolChoiceRequiredWithMultipleTools = false;
                this.supportsToolChoiceRequiredWithSingleTool = false;
            }
            return self();
        }

        public T supportsJsonResponseFormat(boolean value) {
            this.supportsJsonResponseFormat = value;
            return self();
        }

        public T supportsJsonResponseFormatWithSchema(boolean value) {
            this.supportsJsonResponseFormatWithSchema = value;
            return self();
        }

        public T supportsSingleImageInputAsBase64EncodedString(boolean value) {
            this.supportsSingleImageInputAsBase64EncodedString = value;
            if (!value) {
                this.supportsMultipleImageInputsAsBase64EncodedStrings = false;
            }
            return self();
        }

        public T supportsMultipleImageInputsAsBase64EncodedStrings(boolean value) {
            this.supportsMultipleImageInputsAsBase64EncodedStrings = value;
            return self();
        }

        public T supportsSingleImageInputAsPublicURL(boolean value) {
            this.supportsSingleImageInputAsPublicURL = value;
            if (!value) {
                this.supportsMultipleImageInputsAsPublicURLs = false;
            }
            return self();
        }

        public T supportsMultipleImageInputsAsPublicURLs(boolean value) {
            this.supportsMultipleImageInputsAsPublicURLs = value;
            return self();
        }

        public T supportsStopSequencesParameter(boolean value) {
            this.supportsStopSequencesParameter = value;
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
}
