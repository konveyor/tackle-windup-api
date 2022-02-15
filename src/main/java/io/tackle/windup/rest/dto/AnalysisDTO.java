package io.tackle.windup.rest.dto;

import java.util.List;

public class AnalysisDTO {

    public String id;
    // TODO use AnalysisStatusDTO once converted to use Mapstruct
    public String status;
    public String lastSSEUpdate;
    public String timeCreated;
    public List<ExecutionDTO> executions;

}
