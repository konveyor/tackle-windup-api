package io.tackle.windup.rest.routes;

import io.quarkus.vertx.web.Route;
import io.tackle.windup.rest.graph.GraphService;
import io.tackle.windup.rest.resources.WindupResource;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.FileSystemAccess;
import io.vertx.ext.web.handler.StaticHandler;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.io.FileNotFoundException;

@ApplicationScoped
public class WindupDeclarativeRoutes {

    private static final Logger LOG = Logger.getLogger(WindupDeclarativeRoutes.class);

    @Inject
    GraphService graphService;

    @ConfigProperty(defaultValue= "index.html", name = "io.tackle.windup.rest.static-report.index.name")
    String indexFileName;

    @ConfigProperty(defaultValue= "reports", name = "io.tackle.windup.rest.static-report.reports.folder")
    String reportsFolderName;

    @Route(regex = "\\/windup\\/analysis\\/(?<" + WindupResource.PATH_PARAM_ANALYSIS_ID + ">[^\\/]+)\\/static-report\\/(index.html)?", methods = Route.HttpMethod.GET)
    void indexStaticContent(RoutingContext routingContext) {
        final String analysisId = routingContext.pathParam(WindupResource.PATH_PARAM_ANALYSIS_ID);
        LOG.infof("Retrieving 'index.html' report for analysis %s", analysisId);
        String indexPath = String.format("%s/%s", graphService.findLatestWindupExecutionOutputPathByAnalysisId(analysisId), indexFileName);
        LOG.infof("Retrieving 'index.html' report for analysis %s from path %s", analysisId, indexPath);
        // I had to use this approach instead of StaticHandler because to have StaticHandler to work
        // in providing the 'index.html' page, the above get path should have had a '*' at the end,
        // but I wanted to keep control over the file available instead of being forced to about
        // the '*' approach to make every fine "statically" available.
        // To manage the other resources related to reports there's the next route.
        routingContext.response()
                .sendFile(indexPath)
                .onFailure(throwable -> {
                    if (throwable instanceof FileNotFoundException) routingContext.fail(Response.Status.NOT_FOUND.getStatusCode());
                    else routingContext.fail(throwable);
                });
    }

    @Route(path = "/windup/analysis/:" + WindupResource.PATH_PARAM_ANALYSIS_ID + "/static-report/reports/*", methods = Route.HttpMethod.GET)
    void reportsStaticContent(RoutingContext routingContext) {
        final String analysisId = routingContext.pathParam(WindupResource.PATH_PARAM_ANALYSIS_ID);
        LOG.infof("Retrieving from 'report' folder for analysis %s", analysisId);
        StaticHandler
                .create(FileSystemAccess.ROOT, String.format("%s/%s/", graphService.findLatestWindupExecutionOutputPathByAnalysisId(analysisId), reportsFolderName))
                .handle(routingContext);
    }
}
