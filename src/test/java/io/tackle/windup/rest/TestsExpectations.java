package io.tackle.windup.rest;

import java.util.HashMap;
import java.util.Map;

public class TestsExpectations {
    public static final Long SAMPLE_APPLICATION_TOTAL_STORY_POINTS = 96L;
    public static final Map<Object, Long> SAMPLE_APPLICATION_NUMBER_ISSUES_PER_CATEGORY;
    public static final Long TEST_APPLICATION_TOTAL_STORY_POINTS = 4L;
    public static final Map<Object, Long> TEST_APPLICATION_NUMBER_ISSUES_PER_CATEGORY;
    
    static {
        SAMPLE_APPLICATION_NUMBER_ISSUES_PER_CATEGORY = new HashMap<>(5);
        SAMPLE_APPLICATION_NUMBER_ISSUES_PER_CATEGORY.put("Migration Optional", 1L);
        SAMPLE_APPLICATION_NUMBER_ISSUES_PER_CATEGORY.put("Migration Mandatory",53L);
        SAMPLE_APPLICATION_NUMBER_ISSUES_PER_CATEGORY.put("Cloud Mandatory",5L);
        SAMPLE_APPLICATION_NUMBER_ISSUES_PER_CATEGORY.put("Information", 6L);
        SAMPLE_APPLICATION_NUMBER_ISSUES_PER_CATEGORY.put("Migration Potential", 38L);

        TEST_APPLICATION_NUMBER_ISSUES_PER_CATEGORY = new HashMap<>(3);
        TEST_APPLICATION_NUMBER_ISSUES_PER_CATEGORY.put("Migration Mandatory", 1L);
        TEST_APPLICATION_NUMBER_ISSUES_PER_CATEGORY.put("Information", 6L);
        TEST_APPLICATION_NUMBER_ISSUES_PER_CATEGORY.put("Migration Potential", 15L);
    }
    
    public static Integer getSampleApplicationNumberIssuesPerCategory(String category) {
        return SAMPLE_APPLICATION_NUMBER_ISSUES_PER_CATEGORY.get(category).intValue();
    }

    public static Integer getTestApplicationNumberIssuesPerCategory(String category) {
        return TEST_APPLICATION_NUMBER_ISSUES_PER_CATEGORY.get(category).intValue();
    }
}
