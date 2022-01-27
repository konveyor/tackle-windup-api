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
     * not using "analysisId" to avoid clashing with {@link io.tackle.windup.rest.resources.WindupResource#PATH_PARAM_ANALYSIS_ID WindupResource.PATH_PARAM_ANALYSIS_ID}
     */
    String ANALYSIS_ID = TYPE + "-Id";
    String STATUS = TYPE + "-Status";
    String LAST_UPDATE = TYPE + "-LastUpdate";
    String CREATED = TYPE + "-Created";
    String OWNS = TYPE + "-Owns";

    @Property(ANALYSIS_ID)
    Long getAnalysisId();

    @Property(ANALYSIS_ID)
    void setAnalysisId(final Long analysisId);

    @Property(STATUS)
    Status getStatus();

    @Property(STATUS)
    void setStatus(final Status status);

    @Property(LAST_UPDATE)
    String getLastUpdate();

    @Property(LAST_UPDATE)
    void setLastUpdate(final String lastUpdate);

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

    enum Status {
        INIT,
        STARTED,
        MERGING,
        CANCELLED,
        COMPLETED,
        DELETED
    }
}
