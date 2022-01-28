package io.tackle.windup.rest.graph;

import com.syncleus.ferma.DelegatingFramedGraph;
import com.syncleus.ferma.FramedGraph;
import com.syncleus.ferma.ReflectionCache;
import com.syncleus.ferma.framefactories.annotation.MethodHandler;
import com.syncleus.ferma.typeresolvers.PolymorphicTypeResolver;
import io.quarkus.runtime.Startup;
import io.tackle.windup.rest.graph.model.AnalysisModel;
import io.tackle.windup.rest.graph.model.WindupExecutionModel;
import io.tackle.windup.rest.resources.WindupBroadcasterResource;
import org.apache.commons.configuration2.ConfigurationUtils;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.tinkerpop.gremlin.process.traversal.Order;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.janusgraph.core.Cardinality;
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.JanusGraphFactory;
import org.janusgraph.core.PropertyKey;
import org.janusgraph.core.schema.JanusGraphManagement;
import org.janusgraph.core.schema.Mapping;
import org.janusgraph.util.system.ConfigurationUtil;
import org.jboss.logging.Logger;
import org.jboss.windup.graph.GraphTypeManager;
import org.jboss.windup.graph.MapInAdjacentPropertiesHandler;
import org.jboss.windup.graph.MapInAdjacentVerticesHandler;
import org.jboss.windup.graph.MapInPropertiesHandler;
import org.jboss.windup.graph.SetInPropertiesHandler;
import org.jboss.windup.graph.WindupAdjacencyMethodHandler;
import org.jboss.windup.graph.WindupPropertyMethodHandler;
import org.jboss.windup.graph.javahandler.JavaHandlerHandler;
import org.jboss.windup.graph.model.WindupConfigurationModel;
import org.jboss.windup.graph.model.WindupEdgeFrame;
import org.jboss.windup.graph.model.WindupFrame;
import org.jboss.windup.graph.model.WindupVertexFrame;
import org.jboss.windup.reporting.category.IssueCategoryModel;
import org.jboss.windup.reporting.model.EffortReportModel;
import org.jboss.windup.web.services.model.WindupExecution;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.NotFoundException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.tackle.windup.rest.resources.WindupResource.PATH_PARAM_ANALYSIS_ID;

@Startup
@ApplicationScoped
public class GraphService {
    private static final Logger LOG = Logger.getLogger(GraphService.class);
    private static final String DEFAULT_CENTRAL_GRAPH_CONFIGURATION_FILE_NAME = "src/main/resources/centralGraphConfiguration.properties";

    @ConfigProperty(defaultValue = DEFAULT_CENTRAL_GRAPH_CONFIGURATION_FILE_NAME, name = "io.tackle.windup.rest.graph.central.properties.file.path")
    File centralGraphProperties;

    @ConfigProperty(defaultValue= "/opt/windup/central-graph", name = "io.tackle.windup.rest.central-graph.base.path")
    String centralGraphBasePath;

    @Inject
    WindupBroadcasterResource windupBroadcasterResource;

    private JanusGraph janusGraph;
    private FramedGraph framedGraph;

    @PostConstruct
    void init() throws Exception {
        janusGraph = openCentralJanusGraph();
        final ReflectionCache reflections = new ReflectionCache();
        final AnnotationFrameFactory frameFactory = new AnnotationFrameFactory(reflections, getMethodHandlers());
        framedGraph = new DelegatingFramedGraph<>(janusGraph, frameFactory, new PolymorphicTypeResolver(reflections/*, WindupFrame.TYPE_PROP*/));
    }

    @PreDestroy
    void destroy() throws Exception {
        LOG.infof("Is central Janus Graph transaction open? %b", janusGraph.tx().isOpen());
        LOG.infof("Closing Central Janus Graph properties file %s", centralGraphProperties);
        janusGraph.close();
        LOG.infof("Is central Janus Graph transaction still open? %b", janusGraph.tx().isOpen());
    }

    private JanusGraph openCentralJanusGraph() throws ConfigurationException, IOException {
        LOG.infof("Opening Central Janus Graph properties file %s", centralGraphProperties);
        final PropertiesConfiguration configuration = ConfigurationUtil.loadPropertiesConfig(centralGraphProperties);
        Path graphPath = Path.of(centralGraphBasePath, "graph");
        Path graph = Files.createDirectories(graphPath);
        Path searchPath = Path.of(centralGraphBasePath, "search");
        Files.createDirectories(searchPath);
        configuration.setProperty("storage.directory", graphPath.toAbsolutePath().toString());
        configuration.setProperty("index.search.directory", searchPath.toAbsolutePath().toString());
        LOG.debugf("Central Janus Graph configuration:\n%s", ConfigurationUtils.toString(configuration));
        try {
            LOG.debugf("graph folder can write (%b), read (%b), execute (%b)", graph.toFile().canWrite(), graph.toFile().canRead(), graph.toFile().canExecute());
            // this generates a
            // java.io.IOException: Mount point not found
            //   at java.base/sun.nio.fs.LinuxFileStore.findMountEntry(LinuxFileStore.java:105)
            // due to https://gitlab.alpinelinux.org/alpine/aports/-/issues/7093
            Files.getFileStore(graph);
        } catch (IOException e) {
            e.printStackTrace();
        }
        final JanusGraph janusGraph = JanusGraphFactory.open(configuration);
        final JanusGraphManagement janusGraphManagement = janusGraph.openManagement();
        LOG.infof("Open instances: %s", janusGraphManagement.getOpenInstances());
        if (!janusGraphManagement.containsPropertyKey(WindupFrame.TYPE_PROP)) {
            final PropertyKey typePropPropertyKey = janusGraphManagement.makePropertyKey(WindupFrame.TYPE_PROP).dataType(String.class).cardinality(Cardinality.LIST).make();
            janusGraphManagement.buildIndex(WindupFrame.TYPE_PROP, Vertex.class).addKey(typePropPropertyKey).buildCompositeIndex();
            janusGraphManagement.buildIndex("edge-typevalue", Edge.class).addKey(typePropPropertyKey).buildCompositeIndex();

            final PropertyKey analysisIdPropertyKey = janusGraphManagement.makePropertyKey(PATH_PARAM_ANALYSIS_ID).dataType(String.class).cardinality(Cardinality.SINGLE).make();
            janusGraphManagement.buildIndex(PATH_PARAM_ANALYSIS_ID, Vertex.class).addKey(analysisIdPropertyKey, Mapping.STRING.asParameter()).buildMixedIndex("search");

            janusGraphManagement.commit();
        }
        // TODO how to count with `query.force-index = true` property
//        if (LOG.isDebugEnabled()) LOG.debugf("Central Graph vertex count at startup = %d", janusGraph.traversal().V().count().next());
/*
        try {
            ManagementSystem.awaitGraphIndexStatus(janusGraph, PATH_PARAM_ANALYSIS_ID).status(REGISTERED, ENABLED).call();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
*/
        return janusGraph;
    }

    public JanusGraph getCentralJanusGraph() {
        return janusGraph;
    }

    public FramedGraph getCentralFramedGraph() {
        return framedGraph;
    }

    public GraphTraversalSource getCentralGraphTraversalSource() {
        return getCentralJanusGraph().traversal();
    }

    public void updateCentralJanusGraph(String sourceGraph, String analysisId, String windupExecutionId) {
        LOG.infof("Start...");
        final ReflectionCache reflections = new ReflectionCache();
        final AnnotationFrameFactory frameFactory = new AnnotationFrameFactory(reflections, getMethodHandlers());
        final Map<Object, Object> verticesBeforeAndAfter = new HashMap<>();
        try (JanusGraph janusGraph = openJanusGraph(sourceGraph);
             FramedGraph framedGraph = new DelegatingFramedGraph<>(janusGraph, frameFactory, new PolymorphicTypeResolver(reflections))) {
            long elementsToBeImported = janusGraph.traversal().V().count().next() + janusGraph.traversal().E().count().next();
            final GraphTraversalSource centralGraphTraversalSource = getCentralGraphTraversalSource();
            // Delete the previous graph for the PATH_PARAM_ANALYSIS_ID provided
            deleteSubGraph(centralGraphTraversalSource, analysisId);

            final Iterator<WindupVertexFrame> vertexIterator = framedGraph.traverse(g -> g.V().has(WindupFrame.TYPE_PROP)).frame(WindupVertexFrame.class);
            long elementsImported = -1;
            while (vertexIterator.hasNext()) {
                if (++elementsImported % 1000 == 0) windupBroadcasterResource.broadcastMessage(String.format("{\"id\":%s,\"state\":\"MERGING\",\"currentTask\":\"Merging analysis graph into central graph\",\"totalWork\":%s,\"workCompleted\":%s}", analysisId, elementsToBeImported, elementsImported));
                WindupVertexFrame vertex = vertexIterator.next();
                LOG.debugf("Adding Vertex %s", vertex);
                GraphTraversal<Vertex, Vertex> importedVertex = centralGraphTraversalSource.addV();
                Iterator<VertexProperty<String>> types = vertex.getElement().properties(WindupFrame.TYPE_PROP);
                types.forEachRemaining(type -> type.ifPresent(value -> importedVertex.property(WindupFrame.TYPE_PROP, value)));
                vertex.getElement().keys()
                        .stream()
                        .filter(s -> !WindupFrame.TYPE_PROP.equals(s))
                        .forEach(property -> {
                            LOG.debugf("Vertex %d has property %s with values %s", vertex.getElement().id(), property, vertex.getProperty(/*).getElement().properties(*/property));
                            importedVertex.property(property, vertex.getProperty(/*).getElement().properties(*/property));
//                    importedVertex.setProperty(property, vertex.getProperty(/*).getElement().properties(*/property));
                        });
                importedVertex.property(PATH_PARAM_ANALYSIS_ID, analysisId);
                verticesBeforeAndAfter.put(vertex.getElement().id(), importedVertex.next().id());
            }
            if (LOG.isDebugEnabled())
                LOG.debugf("Central Graph count after %d", centralGraphTraversalSource.V().count().next());
//            centralGraphTraversalSource.V().toList().forEach(v -> LOG.infof("%s with property %s", v, v.property(PATH_PARAM_ANALYSIS_ID)));
            Iterator<WindupEdgeFrame> edgeIterator = framedGraph.traverse(GraphTraversalSource::E).frame(WindupEdgeFrame.class);
            while (edgeIterator.hasNext()) {
                if (++elementsImported % 1000 == 0) windupBroadcasterResource.broadcastMessage(String.format("{\"id\":%s,\"state\":\"MERGING\",\"currentTask\":\"Merging analysis graph into central graph\",\"totalWork\":%s,\"workCompleted\":%s}", analysisId, elementsToBeImported, elementsImported));
                WindupEdgeFrame edgeFrame = edgeIterator.next();
                LOG.debugf("Adding Edge %s", edgeFrame.toPrettyString());
                Edge edge = edgeFrame.getElement();

                Object outVertexId = edge.outVertex().id();
                Object importedOutVertexId = verticesBeforeAndAfter.get(outVertexId);
                if (outVertexId == null || importedOutVertexId == null)
                    LOG.warnf("outVertexId %s -> importedOutVertexId %s", outVertexId, importedOutVertexId);
                GraphTraversal<Vertex, Vertex> outVertexTraversal = centralGraphTraversalSource.V(importedOutVertexId);

                Object inVertexId = edge.inVertex().id();
                Object importedInVertexId = verticesBeforeAndAfter.get(inVertexId);
                if (inVertexId == null || importedInVertexId == null)
                    LOG.warnf("inVertexId %s -> importedInVertexId %s", inVertexId, importedInVertexId);
                GraphTraversal<Vertex, Vertex> edgeGraphTraversal = centralGraphTraversalSource.V(importedInVertexId);
                Vertex inVertex = null;
                if (edgeGraphTraversal.hasNext()) {
                    inVertex = edgeGraphTraversal.next();
                } else {
                    LOG.warnf("Missing IN vertex. It seems like the %s vertex has not been imported", inVertexId);
                    continue;
                }
                GraphTraversal<Vertex, Edge> importedEdgeTraversal = outVertexTraversal.addE(edge.label()).to(inVertex);

                Iterator<Property<String>> types = edge.properties(WindupEdgeFrame.TYPE_PROP);
                types.forEachRemaining(type -> type.ifPresent(value -> importedEdgeTraversal.property(WindupFrame.TYPE_PROP, value)));
                edge.keys()
                        .stream()
                        .filter(s -> !WindupEdgeFrame.TYPE_PROP.equals(s))
                        .forEach(property -> {
                            LOG.debugf("Edge %d has property %s with values %s", edge.id(), property, edgeFrame.getProperty(property));
                            importedEdgeTraversal.property(property, edgeFrame.getProperty(property));
                        });
                Edge importedEdge = importedEdgeTraversal.property(PATH_PARAM_ANALYSIS_ID, analysisId).next();
                LOG.debugf("Added Edge %s", importedEdge);
            }
            // now that the WindupConfigurationModel has been added to the graph
            // it can be set for the WindupExecutionModel
            WindupConfigurationModel windupConfigurationModel = framedGraph.frameElement(
                    getCentralGraphTraversalByType(WindupConfigurationModel.class)
                            .has(PATH_PARAM_ANALYSIS_ID, analysisId)
                            .next(),
                    WindupConfigurationModel.class);
            final WindupExecutionModel windupExecutionModel = findWindupExecutionModelByWindupExecutionId(Long.parseLong(windupExecutionId));
            windupExecutionModel.setConfiguration(windupConfigurationModel);
            LOG.debugf("Attached WindupConfigurationModel %s", windupConfigurationModel);
            final Long totalStoryPoint = getTotalStoryPoints(analysisId);
            windupExecutionModel.setTotalStoryPoints(totalStoryPoint);
            LOG.debugf("Total Story Point: %d", totalStoryPoint);
            final Map<Object, Long> numberIssuePerCategory = getNumberIssuesPerCategory(analysisId);
            windupExecutionModel.setNumberIssuesPerCategory(numberIssuePerCategory);
            if (LOG.isDebugEnabled()) numberIssuePerCategory.forEach((key, value) -> LOG.debugf("Category %s has %d issues", key, value));
            // it's "forcing" the numbers to be fine
            windupBroadcasterResource.broadcastMessage(String.format("{\"id\":%s,\"state\":\"MERGING\",\"currentTask\":\"Merging analysis graph into central graph\",\"totalWork\":%s,\"workCompleted\":%s}", analysisId, elementsToBeImported, elementsToBeImported));
            centralGraphTraversalSource.tx().commit();
        } catch (Exception e) {
            LOG.errorf("Exception occurred: %s", e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        LOG.infof("...end");
    }

    public void deleteAnalysisGraphFromCentralGraph(String analysisId) {
        GraphTraversalSource centralGraphTraversalSource = getCentralGraphTraversalSource();
        deleteSubGraph(centralGraphTraversalSource, analysisId);
        centralGraphTraversalSource.tx().commit();
    }

    private void deleteSubGraph(GraphTraversalSource centralGraphTraversalSource, String analysisId) {
        // Delete the previous graph for the PATH_PARAM_ANALYSIS_ID provided
        LOG.infof("Delete the previous vertices with Analysis ID %s", analysisId);
        if (LOG.isDebugEnabled())
            LOG.debugf("Before deleting vertices with Analysis ID %s, central graph has %d vertices and %d edges",
                    analysisId,
                    centralGraphTraversalSource.V().count().next(),
                    centralGraphTraversalSource.E().count().next());
        final GraphTraversal<Vertex, Vertex> previousVertexGraph = centralGraphTraversalSource.V();
        previousVertexGraph.has(PATH_PARAM_ANALYSIS_ID, analysisId).drop().iterate();
        if (LOG.isDebugEnabled())
            LOG.debugf("After deletion of vertices with Analysis ID %s, central graph has %d vertices and %d edges",
                    analysisId,
                    centralGraphTraversalSource.V().count().next(),
                    centralGraphTraversalSource.E().count().next());
    }

    public <MODEL extends WindupVertexFrame> MODEL create(Class<MODEL> model) {
        GraphTraversalSource centralGraphTraversalSource = getCentralGraphTraversalSource();
        GraphTraversal<Vertex, Vertex> vertex = centralGraphTraversalSource.addV();
        vertex.property(WindupFrame.TYPE_PROP, GraphTypeManager.getTypeValue(model));
        return framedGraph.frameElement(vertex.next(), model);
    }

    public WindupExecutionModel createFromWindupExecution(WindupExecution windupExecution) {
        final WindupExecutionModel windupExecutionModel = create(WindupExecutionModel.class);
        // TODO evaluate MapStruct
        windupExecutionModel.setWindupExecutionId(windupExecution.getId());
        windupExecutionModel.setTimeQueued(windupExecution.getTimeQueued().getTimeInMillis());
        windupExecutionModel.setState(windupExecution.getState());
        windupExecutionModel.setOutputPath(windupExecution.getOutputPath());
        windupExecutionModel.setOutputDirectoryName(windupExecution.getOutputDirectoryName());
        windupExecutionModel.setApplicationListRelativePath(windupExecution.getApplicationListRelativePath());
        return windupExecutionModel;
    }

    public AnalysisModel findAnalysisModelByAnalysisId(long analysisId) {
        final GraphTraversal<Vertex, Vertex> graphTraversal = getCentralGraphTraversalByType(AnalysisModel.class).has(AnalysisModel.ANALYSIS_ID, analysisId);
        if (graphTraversal.hasNext()) {
            return framedGraph.frameElement(graphTraversal.next(), AnalysisModel.class);
        } else {
            throw new NotFoundException();
        }
    }

    public WindupExecutionModel findWindupExecutionModelByWindupExecutionId(long windupExecutionId) {
        return framedGraph.frameElement(
                getCentralGraphTraversalByType(WindupExecutionModel.class)
                        .has(WindupExecutionModel.WINDUP_EXECUTION_ID, windupExecutionId)
                        .next(),
                WindupExecutionModel.class);
    }

    public List<Vertex> findWindupExecutionModelByAnalysisId(long analysisId) {
        return getCentralGraphTraversalByType(AnalysisModel.class)
                .has(AnalysisModel.ANALYSIS_ID, analysisId)
                .out(AnalysisModel.OWNS)
                .order().by(WindupExecutionModel.TIME_QUEUED, Order.desc)
                .toList();
    }

    public Long getTotalStoryPoints(String analysisId) {
        return getCentralGraphTraversalByType(EffortReportModel.class)
                .has(PATH_PARAM_ANALYSIS_ID, analysisId)
                .map(vertexTraverser -> vertexTraverser.get().property(EffortReportModel.EFFORT).value())
                .sum()
                .next()
                .longValue();
    }

    public Map<Object, Long> getNumberIssuesPerCategory(String analysisId) {
        return getCentralGraphTraversalByType(EffortReportModel.class)
                .has(PATH_PARAM_ANALYSIS_ID, analysisId)
                .out(EffortReportModel.ISSUE_CATEGORY)
                .values(IssueCategoryModel.NAME)
                .groupCount()
                .next();
    }

    public <MODEL extends WindupVertexFrame> GraphTraversal<Vertex, Vertex> getCentralGraphTraversalByType(Class<MODEL> model) {
        return getCentralGraphTraversalSource().V().has(WindupFrame.TYPE_PROP, GraphTypeManager.getTypeValue(model));
    }

    protected Set<MethodHandler> getMethodHandlers() {
        final Set<MethodHandler> handlers = new HashSet<>();
        handlers.add(new MapInPropertiesHandler());
        handlers.add(new MapInAdjacentPropertiesHandler());
        handlers.add(new MapInAdjacentVerticesHandler());
        handlers.add(new SetInPropertiesHandler());
        handlers.add(new JavaHandlerHandler());
        handlers.add(new WindupPropertyMethodHandler());
        handlers.add(new WindupAdjacencyMethodHandler());
        return handlers;
    }

    protected JanusGraph openJanusGraph(String sourceGraph) throws ConfigurationException {
        // temporary workaround to work locally
        sourceGraph += "/graph/TitanConfiguration.properties";
        LOG.infof("Opening Janus Graph properties file %s", sourceGraph);
        PropertiesConfiguration configuration = ConfigurationUtil.loadPropertiesConfig(sourceGraph);
        configuration.setProperty("storage.transactions", true);
        return JanusGraphFactory.open(configuration);
    }
}
