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
package io.tackle.windup.rest.jms;

import io.tackle.windup.rest.graph.model.AnalysisModel;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;
import org.jboss.windup.web.services.json.WindupExecutionJSONUtil;
import org.jboss.windup.web.services.model.AdvancedOption;
import org.jboss.windup.web.services.model.AnalysisContext;
import org.jboss.windup.web.services.model.ExecutionState;
import org.jboss.windup.web.services.model.Package;
import org.jboss.windup.web.services.model.PathType;
import org.jboss.windup.web.services.model.RegisteredApplication;
import org.jboss.windup.web.services.model.RulesPath;
import org.jboss.windup.web.services.model.WindupExecution;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class WindupExecutionProducer {

    private static final Logger LOG = Logger.getLogger(WindupExecutionProducer.class);
    static final String MESSAGE_PROPERTY_PROJECT_ID = "projectId";
    static final String MESSAGE_PROPERTY_EXECUTION_ID = "executionId";

    @Inject
    ConnectionFactory connectionFactory;

    public WindupExecution triggerAnalysis(AnalysisModel analysisModel, String applicationFilePath, String baseOutputPath, String sources, String targets, String packages, Boolean sourceMode) {
        LOG.debugf("JMS Connection Factory: %s", connectionFactory.toString());
        try (JMSContext context = connectionFactory.createContext(Session.AUTO_ACKNOWLEDGE)) {
            final TextMessage executionRequestMessage = context.createTextMessage();

            executionRequestMessage.setLongProperty(MESSAGE_PROPERTY_PROJECT_ID, analysisModel.getAnalysisId());
            executionRequestMessage.setLongProperty(MESSAGE_PROPERTY_EXECUTION_ID, System.currentTimeMillis());

            final AnalysisContext analysisContext = new AnalysisContext();
            analysisContext.setGenerateStaticReports(true);

            analysisContext.setAdvancedOptions(Stream.of(targets.split(",")).map(targetValue -> new AdvancedOption("target", targetValue.trim())).collect(Collectors.toList()));
            if (StringUtils.isNotBlank(sources)) analysisContext.getAdvancedOptions().addAll(Stream.of(sources.split(",")).map(sourceValue -> new AdvancedOption("source", sourceValue.trim())).collect(Collectors.toList()));
            if (StringUtils.isNotBlank(packages)) analysisContext.setIncludePackages(Stream.of(packages.split(",")).map(packageValue -> new Package(packageValue.trim(), packageValue.trim(), false)).collect(Collectors.toSet()));
            if (sourceMode != null) analysisContext.getAdvancedOptions().add(new AdvancedOption("sourceMode", sourceMode.toString()));

            final RulesPath rulesPath = new RulesPath();
            rulesPath.setPath("/opt/mta-cli/rules");
            rulesPath.setScanRecursively(true);
            rulesPath.setRulesPathType(PathType.SYSTEM_PROVIDED);
            analysisContext.setRulesPaths(Collections.singleton(rulesPath));

            final RegisteredApplication registeredApplication = new RegisteredApplication();
            registeredApplication.setInputPath(applicationFilePath);
            analysisContext.setApplications(Set.of(registeredApplication));

            final WindupExecution windupExecution = new WindupExecution();
            windupExecution.setId(executionRequestMessage.getLongProperty(MESSAGE_PROPERTY_PROJECT_ID));
            windupExecution.setAnalysisContext(analysisContext);
            windupExecution.setTimeQueued(new GregorianCalendar());
            windupExecution.setState(ExecutionState.QUEUED);
            windupExecution.setOutputPath(Path.of(baseOutputPath, Long.toString(analysisModel.getAnalysisId())).toString());

            final String json = WindupExecutionJSONUtil.serializeToString(windupExecution);
            executionRequestMessage.setText(json);
            LOG.infof("Going to send the Windup execution request %s", json);
            context.createProducer().send(context.createQueue("executorQueue"), executionRequestMessage);
            return windupExecution;
        }
        catch (JMSException | IOException e)
        {
            throw new RuntimeException("Failed to create WindupExecution stream message!", e);
        }
    }

    public void cancelAnalysis(long analysisId) {
        LOG.debugf("JMS Connection Factory: %s", connectionFactory.toString());
        try (JMSContext context = connectionFactory.createContext(Session.AUTO_ACKNOWLEDGE)) {
            final WindupExecution windupExecution = new WindupExecution();
            windupExecution.setId(analysisId);
            final String json = WindupExecutionJSONUtil.serializeToString(windupExecution);
            final TextMessage cancelRequestMessage = context.createTextMessage();
            cancelRequestMessage.setLongProperty(MESSAGE_PROPERTY_PROJECT_ID, analysisId);
            cancelRequestMessage.setText(json);
            LOG.infof("Going to send the Windup cancel request %s", json);
            context.createProducer().send(context.createTopic("executorCancellation"), cancelRequestMessage);
        } catch (JMSException | IOException e) {
            throw new RuntimeException("Failed to cancel WindupExecution stream message!", e);
        }
    }
}
