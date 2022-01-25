package io.tackle.windup.rest;

import io.restassured.http.ContentType;
import io.restassured.http.Headers;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.awaitility.Durations;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.sse.SseEventSource;
import java.io.File;
import java.time.Duration;
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

    private SseEventSource openSseEventSource(List<String> received) {
        final Client client = ClientBuilder.newClient();
        final WebTarget target = client.target(String.format("%s/%s/analysisSse/", URL, PATH));
        final SseEventSource source = SseEventSource.target(target).build();
        source.register(inboundSseEvent -> received.add(inboundSseEvent.readData()));
        source.open();
        return source;
    }

    private ExtractableResponse<Response> postSampleApplicationAnalysis() {
        final File sampleApplication = new File("src/main/resources/META-INF/resources/samples/jee-example-app-1.0.0.ear");
        assertTrue(sampleApplication.exists());
        return given()
                .multiPart("application", sampleApplication)
                .multiPart("applicationFileName","foo.ear")
                .multiPart("targets","eap7,cloud-readiness,quarkus,rhr")
                .when()
                .post(String.format("%s/%s/analysis/", URL, PATH))
                .then()
                .statusCode(201)
                .extract();
    }

    private ExtractableResponse<Response> putTestApplicationAnalysis(String analysisId) {
        final File testApplication = new File("src/test/resources/samples/Windup1x-javaee-example.war");
        assertTrue(testApplication.exists());
        return given()
                .pathParam("analysisId", analysisId)
                .multiPart("application", testApplication)
                .multiPart("applicationFileName","bar.ear")
                .multiPart("targets","eap7")
                .when()
                .put(String.format("%s/%s/analysis/{analysisId}", URL, PATH))
                .then()
                .statusCode(201)
                .extract();
    }

    private void waitForSampleApplicationAnalysisToFinish(String issuesLocation) {
        waitForApplicationAnalysisToFinish(issuesLocation, 92);
    }

    private void waitForTestApplicationAnalysisToFinish(String issuesLocation) {
        waitForApplicationAnalysisToFinish(issuesLocation, 15);
    }

    private void waitForApplicationAnalysisToFinish(String issuesLocation, int numberIssues) {
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
                        .equals(Integer.toString(numberIssues))
                );

    }

    private void checkCreateApplicationAnalysisResponseHeaders(Headers headers) {
        final String location = headers.getValue("Location");
        assertNotNull(location);
        final String issuesLocation = headers.getValue("Issues-Location");
        assertNotNull(issuesLocation);
        final String analysisId = headers.getValue("Analysis-Id");
        assertNotNull(analysisId);
        assertTrue(issuesLocation.contains(analysisId));
        assertTrue(location.endsWith(analysisId));
    }

    private void checkWindupLastEventReceived(List<String> eventsReceived, int windupTotalWorkExpected, String analysisId) {
        // check the "COMPLETED" event from the windup-executor pod has been sent
        assertEquals(1,
                eventsReceived.stream()
                        .filter(s ->
                                s.contains(
                                        String.format("\"totalWork\":%d,\"workCompleted\":%d,\"currentTask\":\"PostFinalizePhase - DeleteWorkDirsAtTheEndRuleProvider - DeleteWorkDirsAtTheEndRuleProvider_2\"",
                                        windupTotalWorkExpected,
                                        windupTotalWorkExpected + 1)
                                )
                        )
                        .filter(s -> s.startsWith(String.format("{\"id\":%s,", analysisId)))
                        .count()
        );
    }

    @Test
    public void testWindupPostAndDeleteAnalysisEndpoints() {
        // start the SSE listener immediately
        final List<String> received = new CopyOnWriteArrayList<>();
        final SseEventSource source = openSseEventSource(received);

        // trigger the analysis
        final Headers headers = postSampleApplicationAnalysis().headers();
        checkCreateApplicationAnalysisResponseHeaders(headers);
        final String location = headers.getValue("Location");
        final String issuesLocation = headers.getValue("Issues-Location");
        final String analysisId = headers.getValue("Analysis-Id");

        // wait for the analysis to finish
        waitForSampleApplicationAnalysisToFinish(issuesLocation);

        // close the SSE endpoint connection 
        source.close();
        // and check some "milestone" events have been sent even if the events have not all fixed value in fields
        // so searching for some patterns will let the assertions do the validation
        checkWindupLastEventReceived(received, 1625, analysisId);
        // check (at least) a merging event has been sent
        assertTrue(received.stream().anyMatch(event -> event.contains(String.format("{\"id\":%s,\"state\":\"MERGING\",\"currentTask\":\"Merging analysis graph into central graph\"", analysisId))));
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

        // check the endpoint for deleting the analysis is working with the 'Location' header value received above
        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .when()
                .delete(location)
                .then()
                .statusCode(204);

        // check there are no more issues for the analysis in the graph
        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .when()
                .get(String.format("%s%s", URL, issuesLocation))
                .then()
                .statusCode(200)
                .body("size()", is(0));
    }

    @Test
    public void testWindupPostAndCancelAndPutAnalysisEndpoints() {
        // start the SSE listener immediately
        final List<String> received = new CopyOnWriteArrayList<>();
        final SseEventSource source = openSseEventSource(received);

        // trigger the analysis
        final Headers headers = postSampleApplicationAnalysis().headers();
        final String location = headers.getValue("Location");
        final String analysisId = headers.getValue("Analysis-Id");
        final String issuesLocation = headers.getValue("Issues-Location");
        checkCreateApplicationAnalysisResponseHeaders(headers);

        // wait some time (receiving at least 10 status updates) before cancelling the execution
        await()
                .pollInterval(Durations.ONE_SECOND)
                .atMost(5, TimeUnit.MINUTES)
                .until(() -> received.size() >= 10);

        // let's cancel the execution now
        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .when()
                .delete(location)
                .then()
                .statusCode(204);

        // wait for the analysis to be cancelled
        await()
                .pollDelay(Duration.ZERO)
                .pollInterval(1, TimeUnit.SECONDS)
                .atMost(2, TimeUnit.MINUTES)
                .until(() -> received.get(received.size() - 1).contains("\"state\":\"CANCELLED\""));

        // check the last delete endpoint's event has been sent
        assertTrue(received.stream().anyMatch(event -> event.endsWith("\"state\":\"DELETE\",\"currentTask\":\"Delete analysis\",\"totalWork\":2,\"workCompleted\":2}")));

        // now we can overwrite the previous analysis
        final Headers putHeaders = putTestApplicationAnalysis(analysisId).headers();
        // check the location is exactly the same as the one provided in the previous POST response
        assertEquals(location, putHeaders.getValue("Location"));
        checkCreateApplicationAnalysisResponseHeaders(putHeaders);

        // wait for the analysis to finish (still using the previous issuesLocation from the POST call)
        waitForTestApplicationAnalysisToFinish(issuesLocation);

        // check the endpoint for retrieving all the issues provides a different response than the one tested above
        given()
                .queryParam("analysisId", analysisId)
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .when()
                .get(String.format("%s/%s/issue", URL, PATH))
                .then()
                .statusCode(200)
                .body("size()", is(15));

        // close the SSE endpoint connection
        source.close();
    }

    @Test
    public void testWindupPostAndPutAnalysisEndpoints() {
        // start the SSE listener immediately
        final List<String> eventsReceived = new CopyOnWriteArrayList<>();
        final SseEventSource source = openSseEventSource(eventsReceived);

        // trigger the analysis
        final Headers postHeaders = postSampleApplicationAnalysis().headers();
        checkCreateApplicationAnalysisResponseHeaders(postHeaders);
        final String analysisId = postHeaders.getValue("Analysis-Id");
        final String issuesLocation = postHeaders.getValue("Issues-Location");
        final String location = postHeaders.getValue("Location");

        // wait for the analysis to finish
        waitForSampleApplicationAnalysisToFinish(issuesLocation);

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

        // and check some "milestone" events have been sent even if the events have not all fixed value in fields
        // so searching for some patterns will let the assertions do the validation
        checkWindupLastEventReceived(eventsReceived, 1625, analysisId);
        // check (at least) a merging event has been sent
        assertTrue(eventsReceived.stream().anyMatch(event -> event.contains(String.format("{\"id\":%s,\"state\":\"MERGING\",\"currentTask\":\"Merging analysis graph into central graph\"", analysisId))));
        // check the merge finished event has been sent
        assertTrue(eventsReceived.contains(String.format("{\"id\":%s,\"state\":\"MERGED\",\"currentTask\":\"Merged into central graph\",\"totalWork\":1,\"workCompleted\":1}", analysisId)));

        // now we can overwrite the previous analysis
        final Headers putHeaders = putTestApplicationAnalysis(analysisId).headers();
        // check the location is exactly the same as the one provided in the previous POST response
        assertEquals(location, putHeaders.getValue("Location"));
        checkCreateApplicationAnalysisResponseHeaders(putHeaders);

        // wait for the analysis to finish (still using the previous issuesLocation from the POST call)
        waitForTestApplicationAnalysisToFinish(issuesLocation);

        // check the endpoint for retrieving all the issues provides a different response than the one tested above
        given()
                .queryParam("analysisId", analysisId)
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .when()
                .get(String.format("%s/%s/issue", URL, PATH))
                .then()
                .statusCode(200)
                .body("size()", is(15));

        // close the SSE endpoint connection
        source.close();

        checkWindupLastEventReceived(eventsReceived, 1413, analysisId);
        // check there are now 2 events of this type due to having invoked the PUT endpoint
        assertEquals(2,
                eventsReceived.stream()
                        .filter(s -> s.contains(String.format("{\"id\":%s,\"state\":\"MERGED\",\"currentTask\":\"Merged into central graph\",\"totalWork\":1,\"workCompleted\":1}", analysisId)))
                        .count()
        );
    }
}
