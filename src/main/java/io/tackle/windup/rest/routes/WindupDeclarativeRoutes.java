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
package io.tackle.windup.rest.routes;

import io.quarkus.vertx.web.Route;
import io.tackle.windup.rest.graph.GraphService;
import io.tackle.windup.rest.resources.WindupResource;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.FileSystemAccess;
import io.vertx.ext.web.handler.StaticHandler;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.io.FileNotFoundException;

import static javax.ws.rs.core.MediaType.TEXT_HTML;

@ApplicationScoped
public class WindupDeclarativeRoutes {

    private static final Logger LOG = Logger.getLogger(WindupDeclarativeRoutes.class);

    @Inject
    GraphService graphService;

    @ConfigProperty(defaultValue= "index.html", name = "io.tackle.windup.rest.static-report.index.name")
    String indexFileName;

    @ConfigProperty(defaultValue= "reports", name = "io.tackle.windup.rest.static-report.reports.folder")
    String reportsFolderName;

    // here the approach about creating routes has been a compromise with automatic OpenAPI definitions creation
    // so the "entry point" route has been divided into 2 routes and only this one is going to be "exposed" in
    // OpenAPI definitions file and the other two endpoint are going to be hidden.
    @Parameter(
            name = WindupResource.PATH_PARAM_ANALYSIS_ID,
            in = ParameterIn.PATH,
            required = true,
            schema = @Schema(type= SchemaType.STRING)
    )
    @APIResponse(responseCode="200",
            description="Static reports index page",
            content=@Content(mediaType=TEXT_HTML))
    @APIResponse(responseCode = "404", description = "Not Found")
    @Route(path = "/windup/analysis/:" + WindupResource.PATH_PARAM_ANALYSIS_ID + "/static-report/index.html", methods = Route.HttpMethod.GET)
    @Tag(name = "Windup Static Report")
    void indexStaticContent(RoutingContext routingContext) {
        final String analysisId = routingContext.pathParam(WindupResource.PATH_PARAM_ANALYSIS_ID);
        LOG.debugf("Retrieving 'index.html' report for analysis %s", analysisId);
        final String indexPath = String.format("%s/%s", graphService.findLatestWindupExecutionOutputPathByAnalysisId(analysisId), indexFileName);
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

    @Operation(hidden = true)
    @Route(path = "/windup/analysis/:" + WindupResource.PATH_PARAM_ANALYSIS_ID + "/static-report/", methods = Route.HttpMethod.GET)
    void baseStaticContent(RoutingContext routingContext) {
        indexStaticContent(routingContext);
    }

    @Operation(hidden = true)
    @Route(path = "/windup/analysis/:" + WindupResource.PATH_PARAM_ANALYSIS_ID + "/static-report/reports/*", methods = Route.HttpMethod.GET)
    void reportsStaticContent(RoutingContext routingContext) {
        final String analysisId = routingContext.pathParam(WindupResource.PATH_PARAM_ANALYSIS_ID);
        LOG.debugf("Retrieving from 'report' folder for analysis %s", analysisId);
        StaticHandler
                .create(FileSystemAccess.ROOT, String.format("%s/%s/", graphService.findLatestWindupExecutionOutputPathByAnalysisId(analysisId), reportsFolderName))
                .handle(routingContext);
    }
}
