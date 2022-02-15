package io.tackle.windup.rest.graph;

import io.quarkus.artemis.test.ArtemisTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.tackle.windup.rest.graph.model.AnalysisModel;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

import static io.tackle.windup.rest.TestsExpectations.SAMPLE_APPLICATION_NUMBER_ISSUES_PER_CATEGORY;
import static io.tackle.windup.rest.TestsExpectations.SAMPLE_APPLICATION_TOTAL_STORY_POINTS;
import static io.tackle.windup.rest.TestsExpectations.TEST_APPLICATION_NUMBER_ISSUES_PER_CATEGORY;
import static io.tackle.windup.rest.TestsExpectations.TEST_APPLICATION_TOTAL_STORY_POINTS;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(ArtemisTestResource.class)
public class GraphServiceTest {

    @Inject
    GraphService graphService;

    @Test
    public void getTotalStoryPointsTest() {
        assertEquals(4L, graphService.getTotalStoryPoints("1644943466142"));
    }

    @Test
    public void getNumberIssuesPerCategoryTest() {
        final Map<Object, Long> expectedNumberIssuesPerCategory = new HashMap<>(3);
        expectedNumberIssuesPerCategory.put("Migration Mandatory", 1L);
        expectedNumberIssuesPerCategory.put("Information", 6L);
        expectedNumberIssuesPerCategory.put("Migration Potential", 15L);
        assertEquals(expectedNumberIssuesPerCategory, graphService.getNumberIssuesPerCategory("1644943466142"));
    }

    @Test
    public void getAnalysisModelTest() {
        final AnalysisModel analysisModel = graphService.findAnalysisModelByAnalysisId(1644943527527L);
        assertEquals(AnalysisModel.Status.COMPLETED, analysisModel.getStatus());
        assertEquals(SAMPLE_APPLICATION_TOTAL_STORY_POINTS, analysisModel.getWindupExecutions().get(0).getTotalStoryPoints());
        assertEquals(SAMPLE_APPLICATION_NUMBER_ISSUES_PER_CATEGORY, analysisModel.getWindupExecutions().get(0).getNumberIssuesPerCategory());
        assertEquals(TEST_APPLICATION_TOTAL_STORY_POINTS, analysisModel.getWindupExecutions().get(1).getTotalStoryPoints());
        assertEquals(TEST_APPLICATION_NUMBER_ISSUES_PER_CATEGORY, analysisModel.getWindupExecutions().get(1).getNumberIssuesPerCategory());
    }
}
