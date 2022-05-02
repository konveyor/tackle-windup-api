/*
 * Copyright © 2021 the Konveyor Contributors (https://konveyor.io/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
