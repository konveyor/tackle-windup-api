package io.tackle.windup.rest.graph;

import com.syncleus.ferma.DelegatingFramedGraph;
import com.syncleus.ferma.FramedGraph;
import com.syncleus.ferma.ReflectionCache;
import com.syncleus.ferma.framefactories.annotation.MethodHandler;
import com.syncleus.ferma.typeresolvers.PolymorphicTypeResolver;
import io.quarkus.runtime.Startup;
import io.tackle.windup.rest.rest.WindupBroadcasterResource;
import org.apache.commons.configuration2.ConfigurationUtils;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
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
import org.jboss.windup.graph.MapInAdjacentPropertiesHandler;
import org.jboss.windup.graph.MapInAdjacentVerticesHandler;
import org.jboss.windup.graph.MapInPropertiesHandler;
import org.jboss.windup.graph.SetInPropertiesHandler;
import org.jboss.windup.graph.WindupAdjacencyMethodHandler;
import org.jboss.windup.graph.WindupPropertyMethodHandler;
import org.jboss.windup.graph.javahandler.JavaHandlerHandler;
import org.jboss.windup.graph.model.WindupEdgeFrame;
import org.jboss.windup.graph.model.WindupFrame;
import org.jboss.windup.graph.model.WindupVertexFrame;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static io.tackle.windup.rest.rest.WindupResource.PATH_PARAM_ANALYSIS_ID;

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

    @PostConstruct
    void init() throws Exception {
        janusGraph = openCentralJanusGraph();
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

    public GraphTraversalSource getCentralGraphTraversalSource() {
        return getCentralJanusGraph().traversal();
    }

    public void updateCentralJanusGraph(String sourceGraph, String analysisId) {
        LOG.infof("Start...");
        final ReflectionCache reflections = new ReflectionCache();
        final AnnotationFrameFactory frameFactory = new AnnotationFrameFactory(reflections, getMethodHandlers());
        final Map<Object, Object> verticesBeforeAndAfter = new HashMap<>();
        try (JanusGraph janusGraph = openJanusGraph(sourceGraph);
             FramedGraph framedGraph = new DelegatingFramedGraph<>(janusGraph, frameFactory, new PolymorphicTypeResolver(reflections))) {
            long elementsToBeImported = janusGraph.traversal().V().count().next() + janusGraph.traversal().E().count().next();
            final GraphTraversalSource centralGraphTraversalSource = getCentralGraphTraversalSource();
            // Delete the previous graph for the PATH_PARAM_ANALYSIS_ID provided
            LOG.infof("Delete the previous vertices with Analysis ID %s", analysisId);
            if (LOG.isDebugEnabled())
                LOG.debugf("Before deleting vertices with Analysis ID %s, central graph has %d vertices and %d edges",
                        analysisId,
                        centralGraphTraversalSource.V().count().next(),
                        centralGraphTraversalSource.E().count().next());
            final GraphTraversal<Vertex, Vertex> previousVertexGraph = centralGraphTraversalSource.V();
            previousVertexGraph.has(PATH_PARAM_ANALYSIS_ID, analysisId);
            previousVertexGraph.drop().iterate();
            if (LOG.isDebugEnabled())
                LOG.debugf("After deletion of vertices with Analysis ID %s, central graph has %d vertices and %d edges",
                        analysisId,
                        centralGraphTraversalSource.V().count().next(),
                        centralGraphTraversalSource.E().count().next());

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
