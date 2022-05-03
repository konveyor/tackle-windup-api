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
package io.tackle.windup.rest;

import io.restassured.http.ContentType;
import io.restassured.http.Headers;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.tackle.windup.rest.graph.model.AnalysisModel;
import org.awaitility.Durations;
import org.jboss.windup.web.services.model.ExecutionState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.sse.SseEventSource;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static io.tackle.windup.rest.TestsExpectations.SAMPLE_APPLICATION_TOTAL_STORY_POINTS;
import static io.tackle.windup.rest.TestsExpectations.TEST_APPLICATION_TOTAL_STORY_POINTS;
import static io.tackle.windup.rest.TestsExpectations.getSampleApplicationNumberIssuesPerCategory;
import static io.tackle.windup.rest.TestsExpectations.getTestApplicationNumberIssuesPerCategory;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WindupE2EIT {

    private static final String PATH = "windup";
    private static final String HEADER_ANALYSIS_ID = "Analysis-Id";
    private static final String HEADER_LOCATION = "Location";
    private static final String HEADER_ISSUES_LOCATION = "Issues-Location";
    private static final int SAMPLE_APPLICATION_WINDUP_TOTAL_WORK_EXPECTED = 1625; // jee-example-app-1.0.0.ear
    private static final int TEST_APPLICATION_WINDUP_TOTAL_WORK_EXPECTED = 1413; // Windup1x-javaee-example.war
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
        source.register(inboundSseEvent -> {
            // helpful in case of need during local tests but please do not commit uncommented
//            System.out.println("Received " + inboundSseEvent.readData());
            received.add(inboundSseEvent.readData());
        });
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
                .multiPart("packages","org.jboss.devconf")
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
        final String location = headers.getValue(HEADER_LOCATION);
        assertNotNull(location);
        final String issuesLocation = headers.getValue(HEADER_ISSUES_LOCATION);
        assertNotNull(issuesLocation);
        final String analysisId = headers.getValue(HEADER_ANALYSIS_ID);
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

    private void checkAnalysisStatus(String analysisId, AnalysisModel.Status status) {
        getAnalysisStatus(analysisId).body("status", is(status.toString()));
    }

    private void waitForAnalysisStatusToBe(String analysisId, AnalysisModel.Status statusExpected) {
        await()
            .pollInterval(1, TimeUnit.SECONDS)
            .atMost(5, TimeUnit.MINUTES)
            .until(
                () -> statusExpected.toString()
                        .equals(getAnalysisStatus(analysisId)
                                .extract()
                                .path("status")
                                .toString()
                        )
            );
    }

    private ValidatableResponse getAnalysisStatus(String analysisId) {
        return given()
                .pathParam("analysisId", analysisId)
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .when()
                .get(String.format("%s/%s/analysis/{analysisId}/status/", URL, PATH))
                .then()
                .statusCode(200);
    }

    private ValidatableResponse getAnalysisExecutions(String analysisId) {
        return given()
                .pathParam("analysisId", analysisId)
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .when()
                .get(String.format("%s/%s/analysis/{analysisId}/execution/", URL, PATH))
                .then()
                .statusCode(200);
    }

    private Object[] additionalKeyMatcherPairsForSampleApplicationExecution(int indexInResponseArray) {
        Object[] additionalKeyMatcherPairs = new Object[26];
        additionalKeyMatcherPairs[0] = String.format("[%d].totalStoryPoints", indexInResponseArray);
        additionalKeyMatcherPairs[1] = is(SAMPLE_APPLICATION_TOTAL_STORY_POINTS.intValue());
        additionalKeyMatcherPairs[2] = String.format("[%d].numberIssuesPerCategory.'Migration Optional'", indexInResponseArray);
        additionalKeyMatcherPairs[3] = is(getSampleApplicationNumberIssuesPerCategory("Migration Optional"));
        additionalKeyMatcherPairs[4] = String.format("[%d].numberIssuesPerCategory.'Migration Mandatory'", indexInResponseArray);
        additionalKeyMatcherPairs[5] = is(getSampleApplicationNumberIssuesPerCategory("Migration Mandatory"));
        additionalKeyMatcherPairs[6] = String.format("[%d].numberIssuesPerCategory.'Cloud Mandatory'", indexInResponseArray);
        additionalKeyMatcherPairs[7] = is(getSampleApplicationNumberIssuesPerCategory("Cloud Mandatory"));
        additionalKeyMatcherPairs[8] = String.format("[%d].numberIssuesPerCategory.'Information'", indexInResponseArray);
        additionalKeyMatcherPairs[9] = is(getSampleApplicationNumberIssuesPerCategory("Information"));
        additionalKeyMatcherPairs[10] = String.format("[%d].numberIssuesPerCategory.'Migration Potential'", indexInResponseArray);
        additionalKeyMatcherPairs[11] = is(getSampleApplicationNumberIssuesPerCategory("Migration Potential"));
        additionalKeyMatcherPairs[12] = String.format("[%d].state", indexInResponseArray);
        additionalKeyMatcherPairs[13] = is(ExecutionState.COMPLETED.toString());
        additionalKeyMatcherPairs[14] = String.format("[%d].workTotal", indexInResponseArray);
        additionalKeyMatcherPairs[15] = is(SAMPLE_APPLICATION_WINDUP_TOTAL_WORK_EXPECTED);
        additionalKeyMatcherPairs[16] = String.format("[%d].applicationFileName", indexInResponseArray);
        additionalKeyMatcherPairs[17] = is("foo.ear");
        additionalKeyMatcherPairs[18] = String.format("[%d].targets", indexInResponseArray);
        additionalKeyMatcherPairs[19] = hasItems("rhr", "quarkus", "eap:[7]", "cloud-readiness");
        additionalKeyMatcherPairs[20] = String.format("[%d].sources", indexInResponseArray);
        additionalKeyMatcherPairs[21] = empty();
        additionalKeyMatcherPairs[22] = String.format("[%d].packages", indexInResponseArray);
        additionalKeyMatcherPairs[23] = empty();
        additionalKeyMatcherPairs[24] = String.format("[%d].sourceMode", indexInResponseArray);
        additionalKeyMatcherPairs[25] = is(false);
        return additionalKeyMatcherPairs;
    }

    private Object[] additionalKeyMatcherPairsForTestApplicationExecution(int indexInResponseArray) {
        Object[] additionalKeyMatcherPairs = new Object[22];
        additionalKeyMatcherPairs[0] = String.format("[%d].totalStoryPoints", indexInResponseArray);
        additionalKeyMatcherPairs[1] = is(TEST_APPLICATION_TOTAL_STORY_POINTS.intValue());
        additionalKeyMatcherPairs[2] = String.format("[%d].numberIssuesPerCategory.'Migration Mandatory'", indexInResponseArray);
        additionalKeyMatcherPairs[3] = is(getTestApplicationNumberIssuesPerCategory("Migration Mandatory"));
        additionalKeyMatcherPairs[4] = String.format("[%d].numberIssuesPerCategory.'Information'", indexInResponseArray);
        additionalKeyMatcherPairs[5] = is(getTestApplicationNumberIssuesPerCategory("Information"));
        additionalKeyMatcherPairs[6] = String.format("[%d].numberIssuesPerCategory.'Migration Potential'", indexInResponseArray);
        additionalKeyMatcherPairs[7] = is(getTestApplicationNumberIssuesPerCategory("Migration Potential"));
        additionalKeyMatcherPairs[8] = String.format("[%d].state", indexInResponseArray);
        additionalKeyMatcherPairs[9] = is(ExecutionState.COMPLETED.toString());
        additionalKeyMatcherPairs[10] = String.format("[%d].workTotal", indexInResponseArray);
        additionalKeyMatcherPairs[11] = is(TEST_APPLICATION_WINDUP_TOTAL_WORK_EXPECTED);
        additionalKeyMatcherPairs[12] = String.format("[%d].applicationFileName", indexInResponseArray);
        additionalKeyMatcherPairs[13] = is("bar.ear");
        additionalKeyMatcherPairs[14] = String.format("[%d].targets", indexInResponseArray);
        additionalKeyMatcherPairs[15] = hasItems("eap:[7]");
        additionalKeyMatcherPairs[16] = String.format("[%d].sources", indexInResponseArray);
        additionalKeyMatcherPairs[17] = empty();
        additionalKeyMatcherPairs[18] = String.format("[%d].packages", indexInResponseArray);
        additionalKeyMatcherPairs[19] = hasItems("org.jboss.devconf");
        additionalKeyMatcherPairs[20] = String.format("[%d].sourceMode", indexInResponseArray);
        additionalKeyMatcherPairs[21] = is(false);
        return additionalKeyMatcherPairs;
    }

    public void checkStaticReportsExists(String analysisId, String expectedApplicationName) {
        // check 'index.html' page is provided as default entry page
        checkHtmlStaticReportExistsWithTitle(analysisId, "", "Application List", expectedApplicationName);
        // check 'index.html' page is provided if dire requested
        checkHtmlStaticReportExistsWithTitle(analysisId, "index.html", "Application List", expectedApplicationName);
        // check another page is available testing the "Rule Providers" page
        checkHtmlStaticReportExistsWithTitle(analysisId, "reports/windup_ruleproviders.html", "Migration Toolkit for Applications by Red Hat Rule Providers");

        // check also Javascript files work fine
        given()
                .pathParam("analysisId", analysisId)
                .when()
                .get(String.format("%s/%s/analysis/{analysisId}/static-report/reports/resources/js/windup-migration-issues.js", URL, PATH))
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON);

        // check PNG images work fine
        given()
                .pathParam("analysisId", analysisId)
                .when()
                .get(String.format("%s/%s/analysis/{analysisId}/static-report/reports/resources/img/mta-icon.png", URL, PATH))
                .then()
                .statusCode(200)
                .contentType("image/png");

        checkNotFoundExternalFiles(analysisId);
    }
    public void checkStaticReportsDoesNotExist(String analysisId) {
        given()
                .pathParam("analysisId", analysisId)
                .when()
                .get(String.format("%s/%s/analysis/{analysisId}/static-report/", URL, PATH))
                .then()
                .statusCode(404);

        given()
                .pathParam("analysisId", analysisId)
                .when()
                .get(String.format("%s/%s/analysis/{analysisId}/static-report/reports/resources/js/windup-migration-issues.js", URL, PATH))
                .then()
                .statusCode(404);

        checkNotFoundExternalFiles(analysisId);
    }

    private void checkNotFoundExternalFiles(String analysisId) {
        // check folder browsing is not allowed
        given()
                .pathParam("analysisId", analysisId)
                .when()
                .get(String.format("%s/%s/analysis/{analysisId}/static-report/reports/", URL, PATH))
                .then()
                .statusCode(404);

        // check a file in a different subfolder is not reachable
        given()
                .pathParam("analysisId", analysisId)
                .when()
                .get(String.format("%s/%s/analysis/{analysisId}/static-report/logs/analysis.log", URL, PATH))
                .then()
                .statusCode(404);

        // check a different subfolder is not reachable
        given()
                .pathParam("analysisId", analysisId)
                .when()
                .get(String.format("%s/%s/analysis/{analysisId}/static-report/stats/", URL, PATH))
                .then()
                .statusCode(404);
    }

    private void checkHtmlStaticReportExistsWithTitle(String analysisId, String reportPath, String expectedTitle, String... containsValues) {
        Response response = given()
                .pathParam("analysisId", analysisId)
                .when()
                .get(String.format("%s/%s/analysis/{analysisId}/static-report/%s", URL, PATH, reportPath))
                .then()
                .statusCode(200)
                .contentType(ContentType.HTML)
                .body("html.head.title", is(expectedTitle))
                .extract()
                .response();
        // Alternative approach to page's title validation
/*
        XmlPath htmlPath = new XmlPath(HTML, response.getBody().asString());
        assertEquals(expectedTitle, htmlPath.getString("html.head.title"));
*/
        Arrays.stream(containsValues).forEach(containsValue -> {
                    assertTrue(response.getBody().asString().contains(containsValue));
                }
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
        final String location = headers.getValue(HEADER_LOCATION);
        final String issuesLocation = headers.getValue(HEADER_ISSUES_LOCATION);
        final String analysisId = headers.getValue(HEADER_ANALYSIS_ID);

        // wait for the analysis to finish
        waitForSampleApplicationAnalysisToFinish(issuesLocation);

        // close the SSE endpoint connection 
        source.close();
        // and check some "milestone" events have been sent even if the events have not all fixed value in fields
        // so searching for some patterns will let the assertions do the validation
        checkWindupLastEventReceived(received, SAMPLE_APPLICATION_WINDUP_TOTAL_WORK_EXPECTED, analysisId);
        // check (at least) a merging event has been sent
        assertTrue(received.stream().anyMatch(event -> event.contains(String.format("{\"id\":%s,\"state\":\"MERGING\",\"currentTask\":\"Merging analysis graph into central graph\"", analysisId))));
        // check the merge finished event has been sent
        assertTrue(received.contains(String.format("{\"id\":%s,\"state\":\"MERGED\",\"currentTask\":\"Merged into central graph\",\"totalWork\":1,\"workCompleted\":1}", analysisId)));
        // check analysis status is completed
        checkAnalysisStatus(analysisId, AnalysisModel.Status.COMPLETED);
        // check there's one execution with historic statistics
        getAnalysisExecutions(analysisId)
                .body("size()", is(1),
                        additionalKeyMatcherPairsForSampleApplicationExecution(0));

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

        // check the static reports have been created and are available
        checkStaticReportsExists(analysisId, "foo.ear");

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

        // check analysis status is deleted
        checkAnalysisStatus(analysisId, AnalysisModel.Status.DELETED);
        // check there's still one execution with historic statistics
        getAnalysisExecutions(analysisId)
                .body("size()", is(1),
                        additionalKeyMatcherPairsForSampleApplicationExecution(0));

        // check the static reports have been deleted and aren't available anymore
        checkStaticReportsDoesNotExist(analysisId);
    }

    @Test
    public void testWindupPostAndCancelAndPutAnalysisEndpoints() {
        // start the SSE listener immediately
        final List<String> received = new CopyOnWriteArrayList<>();
        final SseEventSource source = openSseEventSource(received);

        // trigger the analysis
        final Headers headers = postSampleApplicationAnalysis().headers();
        final String location = headers.getValue(HEADER_LOCATION);
        final String analysisId = headers.getValue(HEADER_ANALYSIS_ID);
        final String issuesLocation = headers.getValue(HEADER_ISSUES_LOCATION);
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
        assertTrue(received.stream().anyMatch(event -> event.endsWith("\"state\":\"DELETE\",\"currentTask\":\"Delete analysis graph\",\"totalWork\":1,\"workCompleted\":1}")));
        // check analysis status is cancelled
        waitForAnalysisStatusToBe(analysisId, AnalysisModel.Status.CANCELLED);
        // check there's still one execution with historic statistics
        getAnalysisExecutions(analysisId)
                .body("size()", is(1),
                        "[0].totalStoryPoints", nullValue(),
                        "[0].numberIssuesPerCategory.'Migration Optional'", nullValue(),
                        "[0].state", is(ExecutionState.CANCELLED.toString()),
                        "[0].workTotal", is(SAMPLE_APPLICATION_WINDUP_TOTAL_WORK_EXPECTED));

        // check the static reports have been deleted and aren't available anymore
        checkStaticReportsDoesNotExist(analysisId);

        // now we can overwrite the previous analysis
        final Headers putHeaders = putTestApplicationAnalysis(analysisId).headers();
        // check the location is exactly the same as the one provided in the previous POST response
        assertEquals(location, putHeaders.getValue(HEADER_LOCATION));
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

        // check analysis status is completed
        checkAnalysisStatus(analysisId, AnalysisModel.Status.COMPLETED);
        // check there two executions with historic statistics and
        // the former execution became the 2nd element (index [1]) in the response
        final List<Object> checks =  new ArrayList<>(List.of(
                "[1].totalStoryPoints", nullValue(),
                "[1].numberIssuesPerCategory.'Migration Optional'", nullValue(),
                "[1].state", is(ExecutionState.CANCELLED.toString()),
                "[1].workTotal", is(SAMPLE_APPLICATION_WINDUP_TOTAL_WORK_EXPECTED)));
        checks.addAll(List.of(additionalKeyMatcherPairsForTestApplicationExecution(0)));
        getAnalysisExecutions(analysisId).body("size()", is(2), checks.toArray());

        // check the static reports have been created and are available
        checkStaticReportsExists(analysisId, "bar.ear");
    }

    @Test
    public void testWindupPostAndPutAnalysisEndpoints() {
        // start the SSE listener immediately
        final List<String> eventsReceived = new CopyOnWriteArrayList<>();
        final SseEventSource source = openSseEventSource(eventsReceived);

        // trigger the analysis
        final Headers postHeaders = postSampleApplicationAnalysis().headers();
        checkCreateApplicationAnalysisResponseHeaders(postHeaders);
        final String analysisId = postHeaders.getValue(HEADER_ANALYSIS_ID);
        final String issuesLocation = postHeaders.getValue(HEADER_ISSUES_LOCATION);
        final String location = postHeaders.getValue(HEADER_LOCATION);

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
        checkWindupLastEventReceived(eventsReceived, SAMPLE_APPLICATION_WINDUP_TOTAL_WORK_EXPECTED, analysisId);
        // check (at least) a merging event has been sent
        assertTrue(eventsReceived.stream().anyMatch(event -> event.contains(String.format("{\"id\":%s,\"state\":\"MERGING\",\"currentTask\":\"Merging analysis graph into central graph\"", analysisId))));
        // check the merge finished event has been sent
        assertTrue(eventsReceived.contains(String.format("{\"id\":%s,\"state\":\"MERGED\",\"currentTask\":\"Merged into central graph\",\"totalWork\":1,\"workCompleted\":1}", analysisId)));
        // check analysis status is completed
        checkAnalysisStatus(analysisId, AnalysisModel.Status.COMPLETED);

        // check the static reports have been created and are available
        checkStaticReportsExists(analysisId, "foo.ear");

        // now we can overwrite the previous analysis
        final Headers putHeaders = putTestApplicationAnalysis(analysisId).headers();
        // check the location is exactly the same as the one provided in the previous POST response
        assertEquals(location, putHeaders.getValue(HEADER_LOCATION));
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

        checkWindupLastEventReceived(eventsReceived, TEST_APPLICATION_WINDUP_TOTAL_WORK_EXPECTED, analysisId);
        // check there are now 2 events of this type due to having invoked the PUT endpoint
        assertEquals(2,
                eventsReceived.stream()
                        .filter(s -> s.contains(String.format("{\"id\":%s,\"state\":\"MERGED\",\"currentTask\":\"Merged into central graph\",\"totalWork\":1,\"workCompleted\":1}", analysisId)))
                        .count()
        );

        // check analysis status is completed
        checkAnalysisStatus(analysisId, AnalysisModel.Status.COMPLETED);
        // check there's just one execution with statics
        final List<Object> checks =  new ArrayList<>(List.of(additionalKeyMatcherPairsForTestApplicationExecution(0)));
        checks.addAll(List.of(additionalKeyMatcherPairsForSampleApplicationExecution(1)));
        getAnalysisExecutions(analysisId).body("size()", is(2), checks.toArray());

        // check the static reports have been created and are available
        checkStaticReportsExists(analysisId, "bar.ear");
    }

    @Test
    public void testAnalysisStatusEndpointNotFound() {
        given()
                .pathParam("analysisId", "0")
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .when()
                .get(String.format("%s/%s/analysis/{analysisId}/status/", URL, PATH))
                .then()
                .statusCode(404);
    }

    @Test
    public void testAnalysisStatusEndpoint() {
        // trigger an analysis and then wait for each status to happen
        final Headers postHeaders = postSampleApplicationAnalysis().headers();
        final String analysisId = postHeaders.getValue(HEADER_ANALYSIS_ID);
        waitForAnalysisStatusToBe(analysisId, AnalysisModel.Status.INIT);
        waitForAnalysisStatusToBe(analysisId, AnalysisModel.Status.STARTED);
        waitForAnalysisStatusToBe(analysisId, AnalysisModel.Status.MERGING);
        waitForAnalysisStatusToBe(analysisId, AnalysisModel.Status.COMPLETED);
    }

}
