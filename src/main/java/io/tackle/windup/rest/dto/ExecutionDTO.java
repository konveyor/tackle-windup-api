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
package io.tackle.windup.rest.dto;

import org.jboss.windup.web.services.model.ExecutionState;

import java.util.List;
import java.util.Map;

public class ExecutionDTO {
    public String id;
    public Long timeQueued;
    public Long timeStarted;
    public Long timeLastUpdate;
    public Long timeFinished;
    public Integer workTotal;
    public Integer workCompleted;
    public String currentTask;
    public ExecutionState state;
    public Long totalStoryPoints;
    public Map<String, Long> numberIssuesPerCategory;
    /**
     * These fields reflect the fields sent in {@link io.tackle.windup.rest.resources.AnalysisMultipartBody}
     * so better keep the same names to let the client easily match them.
     */
    public String applicationFileName;
    public List<String> sources;
    public List<String> targets;
    public List<String> packages;
    public Boolean sourceMode;
}
