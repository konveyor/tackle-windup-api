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
package io.tackle.windup.rest.graph.model;

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.jboss.windup.graph.Adjacency;
import org.jboss.windup.graph.Property;
import org.jboss.windup.graph.model.TypeValue;
import org.jboss.windup.graph.model.WindupConfigurationModel;
import org.jboss.windup.graph.model.WindupVertexFrame;
import org.jboss.windup.rules.apps.java.model.WindupJavaConfigurationModel;
import org.jboss.windup.web.services.model.ExecutionState;

import java.util.Map;

@TypeValue(WindupExecutionModel.TYPE)
public interface WindupExecutionModel extends WindupVertexFrame {

    String TYPE = "WindupExecutionModel";
    String WINDUP_EXECUTION_ID = "windupExecutionModelId";
    String VERSION = "version";
    String USES_CONFIGURATION = "uses";
    String USES_JAVA_CONFIGURATION = "usesJavaConfiguration";
    /**
     * not using "outputPath" to avoid clashing with {@link org.jboss.windup.graph.model.WindupConfigurationModel#OUTPUT_PATH WindupConfigurationModel.OUTPUT_PATH}
     */
    String OUTPUT_PATH = "resultPath";
    String OUTPUT_DIRECTORY_NAME = "outputDirectoryName";
    String APPLICATION_LIST_RELATIVE_PATH = "applicationListRelativePath";
    String TIME_QUEUED = "timeQueued";
    String TIME_STARTED = "timeStarted";
    String TIME_FINISHED = "timeFinished";
    String WORK_TOTAL = "workTotal";
    String WORK_COMPLETED = "workCompleted";
    String CURRENT_TASK = "currentTask";
    String LAST_MODIFIED = "lastModified";
    String STATE = "state";
    String APPLICATION_FILE_NAME = "applicationFileName";
    String TOTAL_STORY_POINT = "totalStoryPoint";
    String NUMBER_ISSUES_PER_CATEGORY = "numberIssuesPerCategory";

    @Property(WINDUP_EXECUTION_ID)
    long getWindupExecutionId();

    @Property(WINDUP_EXECUTION_ID)
    void setWindupExecutionId(final Long windupExecutionId);

    @Property(VERSION)
    int getVersion();

    @Property(VERSION)
    void setVersion(int version);

    @Adjacency(label = USES_CONFIGURATION, direction = Direction.OUT)
    WindupConfigurationModel getConfiguration();

    @Adjacency(label = USES_CONFIGURATION, direction = Direction.OUT)
    void setConfiguration(WindupConfigurationModel configuration);

    @Adjacency(label = USES_JAVA_CONFIGURATION, direction = Direction.OUT)
    WindupJavaConfigurationModel getJavaConfiguration();

    @Adjacency(label = USES_JAVA_CONFIGURATION, direction = Direction.OUT)
    void setJavaConfiguration(WindupJavaConfigurationModel javaConfiguration);

    /**
     * Contains the path to the output directory for windup (containing the reports and graph data).
     */
    @Property(OUTPUT_PATH)
    String getOutputPath();

    /**
     * Contains the path to the output directory for windup (containing the reports and graph data).
     */
    @Property(OUTPUT_PATH)
    void setOutputPath(String outputPath);

    /**
     * Gets the directory name of the output as computed from the full path.
     */
    @Property(OUTPUT_DIRECTORY_NAME)
    String getOutputDirectoryName();

    /**
     * This should never be called directory (it is only here to aid in Jackson serialization).
     */
    @Property(OUTPUT_DIRECTORY_NAME)
    void setOutputDirectoryName(String dirName);

    /**
     * Gets the relative path to the application list in a format suitable for a URL.
     */
    @Property(APPLICATION_LIST_RELATIVE_PATH)
    String getApplicationListRelativePath();

    /**
     * This should never be called directory (it is only here to aid in Jackson serialization).
     */
    @Property(APPLICATION_LIST_RELATIVE_PATH)
    void setApplicationListRelativePath(String path);

//    /**
//     * Gets the relative path to the rule providers report in a format suitable for a URL.
//     */
//    @Property(PROPERTY_FOO)
//    String getRuleProvidersExecutionOverviewRelativePath();
//
//    /**
//     * This should never be called directory (it is only here to aid in Jackson serialization).
//     */
//    @Property(PROPERTY_FOO)
//    void setRuleProvidersExecutionOverviewRelativePath(String path);

    /**
     * Contains the time at which this execution was put into the executions queue.
     */
    @Property(TIME_QUEUED)
    long getTimeQueued();

    /**
     * Contains the time at which this execution was put into the executions queue.
     */
    @Property(TIME_QUEUED)
    void setTimeQueued(long timeQueued);

    /**
     * Contains the time that this execution run was started.
     */
    @Property(TIME_STARTED)
    long getTimeStarted();

    /**
     * Contains the time that this execution run was started.
     */
    @Property(TIME_STARTED)
    void setTimeStarted(long timeStarted);

    /**
     * Contains the time that this execution run was finished (no matter the state).
     */
    @Property(TIME_FINISHED)
    long getTimeFinished();

    /**
     * Contains the time that this execution run was finished (no matter the state).
     */
    @Property(TIME_FINISHED)
    void setTimeFinished(long timeCompleted);

    /**
     * Contains the total number of units of work that must be executed.
     */
    @Property(WORK_TOTAL)
    int getWorkTotal();

    /**
     * Contains the total number of units of work that must be executed.
     */
    @Property(WORK_TOTAL)
    void setWorkTotal(int totalWork);

    /**
     * Contains the number of units of work that have been executed.
     */
    @Property(WORK_COMPLETED)
    int getWorkCompleted();

    /**
     * Contains the number of units of work that have been executed.
     */
    @Property(WORK_COMPLETED)
    void setWorkCompleted(int workCompleted);

    /**
     * Contains the name of the current task being executed.
     */
    @Property(CURRENT_TASK)
    String getCurrentTask();

    /**
     * Contains the name of the current task being executed.
     */
    @Property(CURRENT_TASK)
    void setCurrentTask(String currentTask);

    /**
     * Contains the last date that this entry was modified.
     */
    @Property(LAST_MODIFIED)
    long getLastModified();

    /**
     * Contains the last date that this entry was modified.
     */
    @Property(LAST_MODIFIED)
    void setLastModified(long lastUpdate);

    /**
     * Contains the status of execution (currently being executed or completed, etc).
     */
    @Property(STATE)
    ExecutionState getState();

    /**
     * Contains the status of execution (currently being executed or completed, etc).
     */
    @Property(STATE)
    void setState(ExecutionState status);

    /**
     * Get the number of story points identified in the execution.
     */
    @Property(TOTAL_STORY_POINT)
    Long getTotalStoryPoints();

    /**
     * Set the number of story points identified in the execution.
     */
    @Property(TOTAL_STORY_POINT)
    void setTotalStoryPoints(Long totalStoryPoint);

    /**
     * Get the number of issues identified in the execution per each issue category.
     */
    @Property(NUMBER_ISSUES_PER_CATEGORY)
    Map<Object, Long> getNumberIssuesPerCategory();

    /**
     * Set the number of issues identified in the execution per each issue category.
     */
    @Property(NUMBER_ISSUES_PER_CATEGORY)
    void setNumberIssuesPerCategory(Map<Object, Long> numberIssuePerCategory);

    /**
     * Get the analyzed application file name (based on the one application per analysis assumption)
     */
    @Property(APPLICATION_FILE_NAME)
    String getApplicationFileName();

    /**
     * Set the analyzed application file name (based on the one application per analysis assumption)
     */
    @Property(APPLICATION_FILE_NAME)
    void setApplicationFileName(String applicationFileName);
}
