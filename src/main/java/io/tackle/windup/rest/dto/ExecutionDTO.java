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
