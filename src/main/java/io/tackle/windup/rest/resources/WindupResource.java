package io.tackle.windup.rest.resources;

import com.syncleus.ferma.DelegatingFramedGraph;
import com.syncleus.ferma.FramedGraph;
import com.syncleus.ferma.ReflectionCache;
import com.syncleus.ferma.framefactories.annotation.MethodHandler;
import com.syncleus.ferma.typeresolvers.PolymorphicTypeResolver;
import io.tackle.windup.rest.dto.AnalysisStatusDTO;
import io.tackle.windup.rest.graph.GraphService;
import io.tackle.windup.rest.graph.model.AnalysisModel;
import io.tackle.windup.rest.graph.model.AnalysisModel.Status;
import io.tackle.windup.rest.graph.model.WindupExecutionModel;
import io.tackle.windup.rest.jms.WindupExecutionProducer;
import io.tackle.windup.rest.mapper.AnalysisMapper;
import io.tackle.windup.rest.mapper.WindupExecutionMapper;
import io.tackle.windup.rest.util.WindupUtil;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.process.traversal.Order;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.janusgraph.core.JanusGraph;
import org.jboss.logging.Logger;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.jboss.windup.graph.AnnotationFrameFactory;
import org.jboss.windup.graph.MapInAdjacentPropertiesHandler;
import org.jboss.windup.graph.MapInAdjacentVerticesHandler;
import org.jboss.windup.graph.MapInPropertiesHandler;
import org.jboss.windup.graph.SetInPropertiesHandler;
import org.jboss.windup.graph.WindupAdjacencyMethodHandler;
import org.jboss.windup.graph.WindupApiAnnotationFrameFactory;
import org.jboss.windup.graph.WindupPropertyMethodHandler;
import org.jboss.windup.graph.frames.FramedVertexIterable;
import org.jboss.windup.graph.javahandler.JavaHandlerHandler;
import org.jboss.windup.graph.model.WindupFrame;
import org.jboss.windup.graph.model.WindupVertexFrame;
import org.jboss.windup.reporting.model.InlineHintModel;
import org.jboss.windup.web.addons.websupport.rest.graph.GraphResource;
import org.jboss.windup.web.services.model.WindupExecution;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.tackle.windup.rest.graph.model.WindupExecutionModel.TIME_QUEUED;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.out;

@Path("/windup")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class WindupResource {
    private static final Logger LOG = Logger.getLogger(WindupResource.class);
    private static final String DEFAULT_GRAPH_CONFIGURATION_FILE_NAME = "graphConfiguration.properties";
    private static final String DEFAULT_CENTRAL_GRAPH_CONFIGURATION_FILE_NAME = "centralGraphConfiguration.properties";
    public static final String PATH_PARAM_ANALYSIS_ID = "analysisId";

    @ConfigProperty(defaultValue = DEFAULT_GRAPH_CONFIGURATION_FILE_NAME, name = "io.tackle.windup.rest.graph.properties.file.path")
    File graphProperties;

    @ConfigProperty(defaultValue = DEFAULT_CENTRAL_GRAPH_CONFIGURATION_FILE_NAME, name = "io.tackle.windup.rest.graph.central.properties.file.path")
    File centralGraphProperties;

    @ConfigProperty(name = "io.tackle.windup.rest.shared-folder.path")
    String sharedFolderPath;

    @Inject
    GraphService graphService;

    @Inject
    WindupExecutionProducer windupExecutionProducer;

    @Inject
    WindupBroadcasterResource windupBroadcasterResource;

    @Inject
    AnalysisMapper analysisMapper;

    @Inject
    WindupExecutionMapper windupExecutionMapper;

    @GET
    @Path("/issue")
    public Response issues(@QueryParam(PATH_PARAM_ANALYSIS_ID) String analysisId) {
        return analysisIssues(analysisId);
    }

    @GET
    @Path("/analysis/{" + PATH_PARAM_ANALYSIS_ID + "}/issues")
    public Response analysisIssues(@PathParam(PATH_PARAM_ANALYSIS_ID) String analysisId) {
        final ReflectionCache reflections = new ReflectionCache();
        final AnnotationFrameFactory frameFactory = new WindupApiAnnotationFrameFactory(Thread.currentThread().getContextClassLoader(), reflections, getMethodHandlers());
        try {
            JanusGraph centralGraph = graphService.getCentralJanusGraph();
            // https://github.com/JanusGraph/janusgraph/issues/500#issuecomment-327868102
            centralGraph.tx().rollback();
            FramedGraph framedGraph = new DelegatingFramedGraph<>(centralGraph, frameFactory, new PolymorphicTypeResolver(reflections));
            LOG.info("...running the query...");
            final GraphTraversal<Vertex, Vertex> hints = graphService.getCentralGraphTraversalByType(InlineHintModel.class);
            if (StringUtils.isNotBlank(analysisId)) hints.has(PATH_PARAM_ANALYSIS_ID, analysisId);
            final List<Vertex> issues = hints.toList();
            LOG.infof("Found %d hints for application ID %s", issues.size(), analysisId);
            return Response.ok(frameIterableToResult(1L, new FramedVertexIterable<>(framedGraph, issues, InlineHintModel.class), 1)).build();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Response.serverError().build();
    }

    @POST
    @Path("/analysis/")
    @Consumes({ MediaType.MULTIPART_FORM_DATA })
    @Produces({ MediaType.APPLICATION_JSON })
    public Response runAnalysis(@MultipartForm AnalysisMultipartBody analysisRequest) {
        try {
            // TODO: make this ID working when multi instances are deployed
            //  (and current time allows for conflicts)
            long analysisId = System.currentTimeMillis();
            AnalysisModel analysisModel = graphService.create(AnalysisModel.class);
            analysisModel.setAnalysisId(analysisId);
            analysisModel.setCreated(System.currentTimeMillis());
            graphService.getCentralGraphTraversalSource().tx().commit();
            return runAnalysis(analysisModel, analysisRequest);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Response.serverError().build();
    }

    @PUT
    @Path("/analysis/{" + PATH_PARAM_ANALYSIS_ID + "}/")
    @Consumes({ MediaType.MULTIPART_FORM_DATA })
    public Response overwriteAnalysis(@PathParam(PATH_PARAM_ANALYSIS_ID) String analysisId,
                                      @MultipartForm AnalysisMultipartBody analysisRequest) {
        try {
            return runAnalysis(graphService.findAnalysisModelByAnalysisId(Long.parseLong(analysisId)), analysisRequest);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Response.serverError().build();
    }

    @DELETE
    @Path("/analysis/{" + PATH_PARAM_ANALYSIS_ID + "}/")
    public Response deleteAnalysis(@PathParam(PATH_PARAM_ANALYSIS_ID) String analysisId) {
        try {
            windupBroadcasterResource.broadcastMessage(String.format("{\"id\":%s,\"state\":\"DELETE\",\"currentTask\":\"Cancel ongoing analysis\",\"totalWork\":1,\"workCompleted\":0}", analysisId));
            windupExecutionProducer.cancelAnalysis(Long.parseLong(analysisId));
            windupBroadcasterResource.broadcastMessage(String.format("{\"id\":%s,\"state\":\"DELETE\",\"currentTask\":\"Delete analysis graph\",\"totalWork\":1,\"workCompleted\":1}", analysisId));
            final AnalysisModel analysisModel = graphService.findAnalysisModelByAnalysisId(Long.parseLong(analysisId));
            final Status nextStatus = analysisModel.getStatus() == Status.COMPLETED ? Status.DELETED : Status.CANCELLED;
            final String outputPath = graphService.findLatestWindupExecutionOutputPathByAnalysisId(analysisId);
            graphService.deleteAnalysisGraphFromCentralGraph(analysisId);
            analysisModel.setStatus(nextStatus);
            graphService.getCentralGraphTraversalSource().tx().commit();
            switch (nextStatus) {
                case DELETED:
                    // if the nextStatus is deleted then it means it was completed and hence the whole output
                    // path can be safely deleted as there's no ongoing analysis
                    WindupUtil.deletePath(outputPath);
                    break;
                case CANCELLED:
                    // if the nextStatus is cancelled then it means it was ongoing and hence only the report files are worth
                    // trying to delete right now, letting the async cancel message to stop the execution so that the
                    // remaining files in the output path can be later deleted in WindupExecutionStatusConsumer
                    WindupUtil.deletePath(Paths.get(outputPath, "reports"));
                    Files.deleteIfExists(Paths.get(outputPath, "index.html"));
                    break;
                default:
                    break;
            }
            return Response.noContent().build();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Response.serverError().build();
    }

    @GET
    @Path("/analysis/")
    public Response retrieveAnalyses() {
        try {
            final JanusGraph centralGraph = graphService.getCentralJanusGraph();
            // https://github.com/JanusGraph/janusgraph/issues/500#issuecomment-327868102
            centralGraph.tx().rollback();
            LOG.info("...running the retrieveAnalyses \"query\"...");
            final List<Vertex> analyses = graphService.getCentralGraphTraversalByType(AnalysisModel.class)
                    .order()
                    .by(out(AnalysisModel.OWNS).values(TIME_QUEUED), Order.desc)
                    .toList();
            LOG.infof("Found %d Analysis", analyses.size());
            final List<Map<String, Object>> result = frameIterableToResult(1L, new FramedVertexIterable<>(graphService.getCentralFramedGraph(), analyses, AnalysisModel.class), 4);
            LOG.debugf("Framed %d Analysis", result.size());
            return Response.ok(result).build();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Response.serverError().build();
    }

    @GET
    @Path("/analysis/{" + PATH_PARAM_ANALYSIS_ID + "}/")
    public Response retrieveAnalysis(@PathParam(PATH_PARAM_ANALYSIS_ID) String analysisId) {
        try {
            final JanusGraph centralGraph = graphService.getCentralJanusGraph();
            // https://github.com/JanusGraph/janusgraph/issues/500#issuecomment-327868102
            centralGraph.tx().rollback();
            LOG.info("...running the retrieveAnalysis \"query\"...");
            final AnalysisModel analysisModel = graphService.findAnalysisModelByAnalysisId(Long.parseLong(analysisId));
            return Response.ok(analysisMapper.toAnalysisDTO(analysisModel)).build();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    @GET
    @Path("/analysis/{" + PATH_PARAM_ANALYSIS_ID + "}/status/")
    public Response retrieveAnalysisStatus(@PathParam(PATH_PARAM_ANALYSIS_ID) String analysisId) {
        try {
            final JanusGraph centralGraph = graphService.getCentralJanusGraph();
            // https://github.com/JanusGraph/janusgraph/issues/500#issuecomment-327868102
            centralGraph.tx().rollback();
            LOG.info("...running the retrieveAnalysisStatus \"query\"...");
            final AnalysisModel analysisModel = graphService.findAnalysisModelByAnalysisId(Long.parseLong(analysisId));
            return Response.ok(AnalysisStatusDTO.withAnalysisModel(analysisModel)).build();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    @GET
    @Path("/analysis/{" + PATH_PARAM_ANALYSIS_ID + "}/execution/")
    public Response retrieveAnalysisExecutions(@PathParam(PATH_PARAM_ANALYSIS_ID) String analysisId) {
        try {
            final JanusGraph centralGraph = graphService.getCentralJanusGraph();
            // https://github.com/JanusGraph/janusgraph/issues/500#issuecomment-327868102
            centralGraph.tx().rollback();
            LOG.info("...running the retrieveAnalysisExecutions \"query\"...");
            final List<? extends WindupExecutionModel> windupExecutionModels = graphService.findWindupExecutionModelByAnalysisId(Long.parseLong(analysisId));
            return Response.ok(
                            windupExecutionModels.stream()
                                    .map(windupExecutionModel -> windupExecutionMapper.toExecutionDTO(windupExecutionModel))
                                    .collect(Collectors.toList()))
                    .build();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    @GET
    @Path("/trigger")
    @Operation(summary = "This method is used to trigger the sample configuration form analysis.", hidden = true)
    public Response trigger() {
        AnalysisMultipartBody analysisRequest = new AnalysisMultipartBody();
        analysisRequest.applicationFile = getClass().getResourceAsStream("/META-INF/resources/samples/jee-example-app-1.0.0.ear");
        analysisRequest.applicationFileName = "jee-example-app-1.0.0.ear";
        analysisRequest.targets = "eap7,cloud-readiness,quarkus,rhr";
        return runAnalysis(analysisRequest);
    }

    private Set<MethodHandler> getMethodHandlers() {
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

    private Response runAnalysis(AnalysisModel analysisModel, AnalysisMultipartBody analysisRequest) {
        try {
            windupBroadcasterResource.broadcastMessage(String.format("{\"id\":%s,\"state\":\"INIT\",\"currentTask\":\"Storing application\",\"totalWork\":2,\"workCompleted\":0}", analysisModel.getAnalysisId()));
            analysisModel.setStatus(Status.INIT);
            graphService.getCentralGraphTraversalSource().tx().commit();
            File application = Paths.get(sharedFolderPath, analysisRequest.applicationFileName).toFile();
            Files.createDirectories(java.nio.file.Path.of(application.getParentFile().getAbsolutePath()));
            Files.copy(
                    analysisRequest.applicationFile,
                    application.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            LOG.debugf("Copied input file to %s\n", application.getAbsolutePath());
            IOUtils.closeQuietly(analysisRequest.applicationFile);
            windupBroadcasterResource.broadcastMessage(String.format("{\"id\":%s,\"state\":\"INIT\",\"currentTask\":\"Triggering the analysis\",\"totalWork\":2,\"workCompleted\":1}", analysisModel.getAnalysisId()));
            final WindupExecution windupExecution = windupExecutionProducer.triggerAnalysis(analysisModel, application.getAbsolutePath(),
                    Paths.get(sharedFolderPath).toAbsolutePath().toString(),
                    analysisRequest.sources,
                    analysisRequest.targets,
                    analysisRequest.packages,
                    analysisRequest.sourceMode);
            final WindupExecutionModel windupExecutionModel = graphService.createFromWindupExecution(windupExecution);
            windupExecutionModel.setApplicationFileName(analysisRequest.applicationFileName);
            analysisModel.addWindupExecution(windupExecutionModel);
            graphService.getCentralGraphTraversalSource().tx().commit();
            windupBroadcasterResource.broadcastMessage(String.format("{\"id\":%s,\"state\":\"INIT\",\"currentTask\":\"Analysis waiting to be executed\",\"totalWork\":2,\"workCompleted\":2}", analysisModel.getAnalysisId()));
            return Response
                    .created(URI.create(String.format("/windup/analysis/%d", analysisModel.getAnalysisId())))
                    .header("Issues-Location", URI.create(String.format("/windup/analysis/%d/issues", analysisModel.getAnalysisId())).toString())
                    .header("Analysis-Id", analysisModel.getAnalysisId())
                    .build();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Response.serverError().build();
    }

    /**
     * Heavily inspired from https://github.com/windup/windup-web/blob/8f81bc56d34756ff3a9261edfccbe9b44af40fc2/addons/web-support/impl/src/main/java/org/jboss/windup/web/addons/websupport/rest/graph/AbstractGraphResource.java#L203
     * @param executionID
     * @param frames
     * @param depth
     * @return
     */
    protected List<Map<String, Object>> frameIterableToResult(long executionID, Iterable<? extends WindupVertexFrame> frames, int depth)
    {
//        GraphMarshallingContext ctx = new GraphMarshallingContext(executionID, null, depth, false, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), true);

        List<Map<String, Object>> result = new ArrayList<>();
        for (WindupVertexFrame frame : frames)
        {
            result.add(convertToMap(/*ctx,*/ frame.getElement(), true, depth));
        }
        return result;
    }

    protected Map<String, Object> convertToMap(/*GraphMarshallingContext ctx,*/ Vertex vertex, boolean addEdges, int depth)
    {
        Map<String, Object> result = new HashMap<>();

        result.put(GraphResource.TYPE, GraphResource.TYPE_VERTEX);
        result.put(GraphResource.KEY_ID, vertex.id());

        // Spare CPU cycles, save the planet. Visited vertices will only contain _id.
/*
        if (ctx.deduplicateVertices && !ctx.addVisited(vertex))
            return result;
*/

        for (String key : vertex.keys()) {
/*
            if (ctx.blacklistProperties.contains(key))
                continue;
*/

            if (WindupFrame.TYPE_PROP.equals(key)) {
                List<String> types = new ArrayList<>();
                Iterator<VertexProperty<String>> typeProperties = vertex.properties(key);
                while (typeProperties.hasNext())
                {
                    types.add(typeProperties.next().value());
                }
                result.put(key, types);
            } else {
                result.put(key, vertex.property(key).orElse(null));
            }
        }


        if (addEdges && --depth > 0) {
            Map<String, Object> outVertices = new HashMap<>();
            result.put(GraphResource.VERTICES_OUT, outVertices);
            addEdges(/*ctx,*/ vertex, Direction.OUT, outVertices, depth);
        }

/*
        if (ctx.includeInVertices) {
            Map<String, Object> inVertices = new HashMap<>();
            result.put(GraphResource.VERTICES_IN, inVertices);
            addEdges(ctx, vertex, Direction.IN, inVertices);
        }
*/

        return result;
    }

    private void addEdges(/*GraphMarshallingContext ctx,*/ Vertex vertex, Direction direction, Map<String, Object> result, int depth)
    {
        final Iterator<Edge> edges = vertex.edges(direction);

        while (edges.hasNext())
        {
            Edge edge = edges.next();
            String label = edge.label();

            Map<String, Object> edgeDetails = (Map<String, Object>) result.get(label);
            // If the details are already there and we aren't recursing any further, then just skip
/*
            if (!whitelistedLabels.contains(label) && edgeDetails != null && ctx.remainingDepth <= 0)
                continue;
*/

            final List<Map<String, Object>> linkedVertices;
            if (edgeDetails == null)
            {
                edgeDetails = new HashMap<>();
                edgeDetails.put(GraphResource.DIRECTION, direction.toString());
                result.put(label, edgeDetails);

                // If we aren't serializing any further, then just provide a link
/*
                if (!whitelistedLabels.contains(label) && ctx.remainingDepth <= 0)
                {
                    edgeDetails.put(GraphResource.TYPE, GraphResource.TYPE_LINK);
                    String linkUri = getLink(ctx.executionID, vertex, direction.toString(), label);
                    edgeDetails.put(GraphResource.LINK, linkUri);
                    continue;
                }
*/

                linkedVertices = new ArrayList<>();
                edgeDetails.put(GraphResource.VERTICES, linkedVertices);
            }
            else
            {
                linkedVertices = (List<Map<String, Object>>) edgeDetails.get(GraphResource.VERTICES);
            }

            Vertex otherVertex = direction == Direction.OUT ? edge.inVertex() : edge.outVertex();

            // Recursion
//            ctx.remainingDepth--;
            Map<String, Object> otherVertexMap = convertToMap(/*ctx,*/ otherVertex, true, depth);
//            ctx.remainingDepth++;

            // Add edge properties if any
            if (!edge.keys().isEmpty())
            {
                Map<String, Object> edgeData = new HashMap<>();
                edge.keys().forEach(key -> edgeData.put(key, edge.property(key).orElse(null)));
                otherVertexMap.put(GraphResource.EDGE_DATA, edgeData);

                /// Add the edge frame's @TypeValue.  Workaround until PR #1063.
                //edgeData.put(WindupFrame.TYPE_PROP, graphTypeManager.resolveTypes(edge, WindupEdgeFrame.class));
            }

            linkedVertices.add(otherVertexMap);
        }
    }
}
