package org.jboss.windup.web.jms;

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
public class AnalysisExecutionProducer {

    private static final Logger LOG = Logger.getLogger(AnalysisExecutionProducer.class);

    @Inject
    ConnectionFactory connectionFactory;

    public long triggerAnalysis(String applicationFilePath, String baseOutputPath, String sources, String targets, String packages, Boolean sourceMode) {
        LOG.debugf("JMS Connection Factory: %s", connectionFactory.toString());
        try (JMSContext context = connectionFactory.createContext(Session.AUTO_ACKNOWLEDGE)) {
            TextMessage executionRequestMessage = context.createTextMessage();

            long id = System.currentTimeMillis();
            executionRequestMessage.setLongProperty("projectId", id);
            executionRequestMessage.setLongProperty("executionId", id);

            AnalysisContext analysisContext = new AnalysisContext();
            analysisContext.setGenerateStaticReports(false);

            analysisContext.setAdvancedOptions(Stream.of(targets.split(",")).map(targetValue -> new AdvancedOption("target", targetValue.trim())).collect(Collectors.toList()));
            if (StringUtils.isNotBlank(sources)) analysisContext.getAdvancedOptions().addAll(Stream.of(sources.split(",")).map(sourceValue -> new AdvancedOption("source", sourceValue.trim())).collect(Collectors.toList()));
            if (StringUtils.isNotBlank(packages)) analysisContext.setIncludePackages(Stream.of(packages.split(",")).map(packageValue -> new Package(packageValue.trim())).collect(Collectors.toSet()));
            if (sourceMode != null) analysisContext.getAdvancedOptions().add(new AdvancedOption("sourceMode", sourceMode.toString()));

            RulesPath rulesPath = new RulesPath();
            rulesPath.setPath("/opt/mta-cli/rules");
            rulesPath.setScanRecursively(true);
            rulesPath.setRulesPathType(PathType.SYSTEM_PROVIDED);
            analysisContext.setRulesPaths(Collections.singleton(rulesPath));

            RegisteredApplication registeredApplication = new RegisteredApplication();
            registeredApplication.setInputPath(applicationFilePath);
            analysisContext.setApplications(Set.of(registeredApplication));

            WindupExecution windupExecution = new WindupExecution();
            windupExecution.setId(id);
            windupExecution.setAnalysisContext(analysisContext);
            windupExecution.setTimeQueued(new GregorianCalendar());
            windupExecution.setState(ExecutionState.QUEUED);
            windupExecution.setOutputPath(Path.of(baseOutputPath, Long.toString(id)).toString());

            String json = WindupExecutionJSONUtil.serializeToString(windupExecution);
            executionRequestMessage.setText(json);
            LOG.infof("Going to send the Windup execution request %s", json);
            context.createProducer().send(context.createQueue("executorQueue"), executionRequestMessage);
            return id;
        }
        catch (JMSException | IOException e)
        {
            throw new RuntimeException("Failed to create WindupExecution stream message!", e);
        }
    }
}
