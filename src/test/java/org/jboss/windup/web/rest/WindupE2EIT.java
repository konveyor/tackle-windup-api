package org.jboss.windup.web.rest;

import io.restassured.http.ContentType;
import io.restassured.http.Headers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.sse.SseEventSource;
import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WindupE2EIT {

    private static final String PATH = "windup";
    private static String URL;

    @BeforeAll
    public static void init() {
        URL = System.getProperty("windup.url", "");
        assertFalse(URL.isEmpty(), "\"windup.url\" property not provided.\nAppend \"-Dwindup.url=$(minikube service -n windup api --url)\" to the command to set it.\n");
    }

    @Test
    public void testWindupPostAnalysisEndpoint() {
        // start the SSE listener immediately
        final Client client = ClientBuilder.newClient();
        final WebTarget target = client.target(String.format("%s/%s/analysisSse/", URL, PATH));
        final List<String> received = new CopyOnWriteArrayList<>();
        final SseEventSource source = SseEventSource.target(target).build();
        source.register(inboundSseEvent -> received.add(inboundSseEvent.readData()));
        source.open();

        // trigger the analysis
        final File sampleApplication = new File("src/main/resources/META-INF/resources/samples/jee-example-app-1.0.0.ear");
        assertTrue(sampleApplication.exists());
        final Headers headers = given()
                .multiPart("application", sampleApplication)
                .multiPart("applicationFileName","foo.ear")
                .multiPart("targets","eap7,cloud-readiness,quarkus,rhr")
                .when()
                .post(String.format("%s/%s/analysis/", URL, PATH))
                .then()
                .statusCode(201)
                .extract()
                .headers();
        final String location = headers.getValue("Location");
        assertNotNull(location);
        final String issuesLocation = headers.getValue("Issues-Location");
        assertNotNull(issuesLocation);
        final String analysisId = headers.getValue("Analysis-Id");
        assertNotNull(analysisId);
        assertTrue(issuesLocation.contains(analysisId));
        assertTrue(location.endsWith(analysisId));

        // wait for the analysis to finish
        await()
                .pollInterval(1, TimeUnit.SECONDS)
                .atMost(5, TimeUnit.MINUTES)
                .until(() -> given()
                        .accept(ContentType.JSON)
                        .contentType(ContentType.JSON)
                        .when()
                        .get(String.format("%s%s", URL, issuesLocation))
                        .then()
                        .statusCode(200)
                        .extract()
                        .path("size()")
                        .toString()
                        .equals("92")
                );

        // close the SSE endpoint connection 
        source.close();
        // and check some "milestone" events have been sent even if the events have not all fixed value in fields
        // so searching for some patterns will let the assertions do the validation
        // check the "COMPLETED" event from the windup-executor pod has been sent
        assertEquals(1, received.stream().filter(s -> s.contains("\"totalWork\":1581,\"workCompleted\":1582,\"currentTask\":\"PostFinalizePhase - DeleteWorkDirsAtTheEndRuleProvider - DeleteWorkDirsAtTheEndRuleProvider_2\"")).count());
        // check the merge finished event has been sent
        assertTrue(received.contains(String.format("{\"id\":%s,\"state\":\"MERGED\",\"currentTask\":\"Merged into central graph\",\"totalWork\":1,\"workCompleted\":1}", analysisId)));

        // check the endpoint for retrieving all the issues is working with the analysis ID as query param
        given()
                .queryParam("analysisId", analysisId)
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .when()
                .get(String.format("%s/%s/issue", URL, PATH))
                .then()
                .statusCode(200)
                .body("size()", is(92));
    }
}
