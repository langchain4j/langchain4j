package dev.langchain4j.agentic.carrentalassistant.domain;

public class Emergencies {

    private String medical;
    private String police;
    private String fire;

    public String getFire() {
        return fire;
    }

    public void setFire(final String fire) {
        this.fire = fire;
    }

    public String getMedical() {
        return medical;
    }

    public void setMedical(final String medical) {
        this.medical = medical;
    }

    public String getPolice() {
        return police;
    }

    public void setPolice(final String police) {
        this.police = police;
    }
}
