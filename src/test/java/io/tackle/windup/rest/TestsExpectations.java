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
