package io.tackle.windup.rest.graph;

import io.quarkus.artemis.test.ArtemisTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(ArtemisTestResource.class)
public class GraphServiceTest {

    @Inject
    GraphService graphService;

    @Test
    public void getTotalStoryPointsTest() {
        assertEquals(4L, graphService.getTotalStoryPoints("1643125071615"));
    }

    @Test
    public void getNumberIssuesPerCategoryTest() {
        final Map<Object, Long> expected = new HashMap<>(3);
        expected.put("Migration Mandatory", 1L);
        expected.put("Information", 6L);
        expected.put("Migration Potential", 15L);
        assertEquals(expected, graphService.getNumberIssuesPerCategory("1643124959840"));
    }
}
