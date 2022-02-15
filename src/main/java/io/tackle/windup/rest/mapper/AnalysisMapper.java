package io.tackle.windup.rest.mapper;

import io.tackle.windup.rest.dto.AnalysisDTO;
import io.tackle.windup.rest.graph.model.AnalysisModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(componentModel = "cdi", uses = {WindupExecutionMapper.class})
public interface AnalysisMapper {

    @Mappings({
            @Mapping(source = "analysisId", target = "id"),
            @Mapping(source = "lastUpdate", target = "lastSSEUpdate"),
            @Mapping(source = "created", target = "timeCreated"),
            @Mapping(source = "windupExecutions", target = "executions")
    })
    AnalysisDTO toAnalysisDTO(AnalysisModel analysisModel);

}
