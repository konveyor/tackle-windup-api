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

import io.tackle.windup.rest.dto.ExecutionDTO;
import io.tackle.windup.rest.graph.model.WindupExecutionModel;
import org.jboss.windup.graph.model.TechnologyReferenceModel;
import org.jboss.windup.rules.apps.java.model.PackageModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Mapper(componentModel = "cdi")
public interface WindupExecutionMapper {

    @Mappings({
            @Mapping(source = "windupExecutionId", target = "id"),
            @Mapping(source = "lastModified", target = "timeLastUpdate"),
            @Mapping(source = "configuration.sourceTechnologies", target = "sources"),
            @Mapping(source = "configuration.targetTechnologies", target = "targets"),
            @Mapping(source = "javaConfiguration.scanJavaPackages", target = "packages"),
            @Mapping(source = "javaConfiguration.sourceMode", target = "sourceMode")
    })
    ExecutionDTO toExecutionDTO(WindupExecutionModel windupExecutionModel);

    default Map<String, Long> mapNumberIssuesPerCategory(Map<Object, Long> numberIssuesPerCategories) {
        return Optional.ofNullable(numberIssuesPerCategories)
                .map(numberIssuesPerCategory ->
                        numberIssuesPerCategory.entrySet()
                                .stream()
                                .collect(Collectors.toMap(
                                        entry -> entry.getKey().toString(),
                                        Map.Entry::getValue
                                )))
                .orElse(Collections.emptyMap());
    }

    default List<String> mapTechnologyReferences(List<TechnologyReferenceModel> technologyReferences) {
        return Optional.ofNullable(technologyReferences)
                .map(technologyReferenceModels ->
                        technologyReferenceModels.stream().map(technologyReferenceModel -> {
                                    final String technologyID = technologyReferenceModel.getTechnologyID();
                                    return technologyReferenceModel.getVersionRange() == null ? technologyID : String.format("%s:%s", technologyID, technologyReferenceModel.getVersionRange());
                                })
                                .collect(Collectors.toList())
                )
                .orElse(Collections.emptyList());
    }

    default List<String> mapPackages(List<PackageModel> packages) {
        return Optional.ofNullable(packages)
                .map(packageModels -> packageModels.stream()
                        .map(PackageModel::getPackageName)
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }
}
