package io.tackle.windup.rest.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import io.tackle.windup.rest.graph.model.AnalysisModel;

public class AnalysisStatusDTO {

    private String status;
    private String lastUpdate;

    public static AnalysisStatusDTO withAnalysisModel(AnalysisModel analysisModel) {
        final AnalysisStatusDTO instance = new AnalysisStatusDTO();
        instance.status = analysisModel.getStatus().toString();
        instance.lastUpdate = analysisModel.getLastUpdate();
        return instance;
    }

    public String getStatus() {
        return status;
    }

    @JsonRawValue
    public String getLastUpdate() {
        return lastUpdate;
    }
}
