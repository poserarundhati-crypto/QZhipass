package org.microsoft.qintelipass.request;

public record ChatOptionsRequest(Double temperature, Integer numPredict) {
    public static final double DEFAULT_TEMPERATURE = 0.7d;
    public static final int DEFAULT_NUM_PREDICT = 2048;

    public double effectiveTemperature() {
        return temperature == null ? DEFAULT_TEMPERATURE : temperature;
    }

    public int effectiveNumPredict() {
        return numPredict == null ? DEFAULT_NUM_PREDICT : numPredict;
    }
}
