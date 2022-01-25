package io.tackle.windup.rest.graph.model;

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.jboss.windup.graph.Adjacency;
import org.jboss.windup.graph.Property;
import org.jboss.windup.graph.model.TypeValue;
import org.jboss.windup.graph.model.WindupVertexFrame;
import org.jboss.windup.web.services.model.WindupExecution;

import java.util.Set;

@TypeValue(AnalysisModel.TYPE)
public interface AnalysisModel extends WindupVertexFrame {

    String TYPE = "AnalysisModel";
    /**
     * not using "analysisId" to avoid clashing with {@link io.tackle.windup.rest.rest.WindupResource#PATH_PARAM_ANALYSIS_ID WindupResource.PATH_PARAM_ANALYSIS_ID}
     */
    String ANALYSIS_ID = "analysisModelId";
    String VERSION = "version";
    String CREATED = "created";
    String OWNS = "owns";

    @Property(ANALYSIS_ID)
    Long getAnalysisId();

    @Property(ANALYSIS_ID)
    void setAnalysisId(final Long analysisId);

    @Property(VERSION)
    int getVersion();

    @Property(VERSION)
    void setVersion(final int version);

    /**
     * Contains creation date
     */
    @Property(CREATED)
    long getCreated();

    /**
     * Sets creation date
     */
    @Property(CREATED)
    void setCreated(long created);

    /**
     * Contains a collection of {@link WindupExecution}s.
     */
    @Adjacency(label = OWNS, direction = Direction.OUT)
    Set<WindupExecutionModel> getWindupExecutions();
    
    /**
     * Adds execution
     */
    @Adjacency(label = OWNS, direction = Direction.OUT)
    void addWindupExecution(WindupExecutionModel windupExecutionModel);
}
