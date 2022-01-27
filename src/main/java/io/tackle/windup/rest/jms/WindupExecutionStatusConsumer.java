package io.tackle.windup.rest.jms;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.tackle.windup.rest.graph.GraphService;
import io.tackle.windup.rest.graph.model.AnalysisModel;
import io.tackle.windup.rest.graph.model.AnalysisModel.Status;
import io.tackle.windup.rest.graph.model.WindupExecutionModel;
import io.tackle.windup.rest.resources.WindupBroadcasterResource;
import org.jboss.logging.Logger;
import org.jboss.windup.web.services.json.WindupExecutionJSONUtil;
import org.jboss.windup.web.services.model.WindupExecution;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.jms.ConnectionFactory;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.Message;
import javax.jms.Session;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.tackle.windup.rest.jms.WindupExecutionProducer.MESSAGE_PROPERTY_EXECUTION_ID;
import static io.tackle.windup.rest.jms.WindupExecutionProducer.MESSAGE_PROPERTY_PROJECT_ID;

@ApplicationScoped
public class WindupExecutionStatusConsumer implements Runnable {

    private static final Logger LOG = Logger.getLogger(WindupExecutionStatusConsumer.class);

    @Inject
    ConnectionFactory connectionFactory;

    @Inject
    GraphService graphService;

    @Inject
    WindupBroadcasterResource windupBroadcasterResource;

    private final ExecutorService scheduler = Executors.newSingleThreadExecutor();

    void onStart(@Observes StartupEvent ev) {
        scheduler.submit(this);
    }

    void onStop(@Observes ShutdownEvent ev) {
        scheduler.shutdown();
    }

    @Override
    public void run() {
        LOG.debugf("JMS Connection Factory: %s", connectionFactory.toString());
        try (JMSContext context = connectionFactory.createContext(Session.AUTO_ACKNOWLEDGE)) {
            JMSConsumer consumer = context.createConsumer(context.createQueue("statusUpdateQueue"));
            while (true) {
                Message message = consumer.receive();
                if (message == null) return;
                final String lastUpdate = message.getBody(String.class);
                LOG.debugf("Last status update from executor = %s", lastUpdate);
                WindupExecution windupExecution = WindupExecutionJSONUtil.readJSON(lastUpdate);
                windupBroadcasterResource.broadcastMessage(lastUpdate);
                WindupExecutionModel windupExecutionModel = graphService.findByWindupExecutionId(windupExecution.getId());
                windupExecutionModel.setState(windupExecution.getState());
                windupExecutionModel.setLastModified(windupExecution.getLastModified().getTimeInMillis());
                windupExecutionModel.setCurrentTask(windupExecution.getCurrentTask());
                windupExecutionModel.setWorkCompleted(windupExecution.getWorkCompleted());
                windupExecutionModel.setWorkTotal(windupExecution.getTotalWork());
                AnalysisModel analysisModel = graphService.findByAnalysisId(windupExecution.getId());
                analysisModel.setLastUpdate(lastUpdate);
                switch (windupExecution.getState()) {
                    case COMPLETED:
                        LOG.infof("Executor analysis COMPLETED: %s", lastUpdate);
                        String id = Long.toString(message.getLongProperty(MESSAGE_PROPERTY_PROJECT_ID));
                        analysisModel.setStatus(Status.MERGING);
                        graphService.getCentralGraphTraversalSource().tx().commit();
                        windupBroadcasterResource.broadcastMessage(String.format("{\"id\":%s,\"state\":\"MERGING\",\"currentTask\":\"Merging into central graph\",\"totalWork\":1,\"workCompleted\":0}", id));
                        graphService.updateCentralJanusGraph(windupExecution.getOutputPath(), id, Long.toString(message.getLongProperty(MESSAGE_PROPERTY_EXECUTION_ID)));
                        windupExecutionModel.setTimeFinished(windupExecution.getTimeCompleted().getTimeInMillis());
                        analysisModel.setStatus(Status.COMPLETED);
                        windupBroadcasterResource.broadcastMessage(String.format("{\"id\":%s,\"state\":\"MERGED\",\"currentTask\":\"Merged into central graph\",\"totalWork\":1,\"workCompleted\":1}", id));
                        // TODO delete the application file now
                        LOG.debug("COMPLETED updateCentralJanusGraph");
                        break;
                    case QUEUED:
                        // the Windup executor pod never sends a status update with this state
                        break;
                    case STARTED:
                        windupExecutionModel.setTimeStarted(windupExecution.getTimeStarted().getTimeInMillis());
                        analysisModel.setStatus(Status.STARTED);
                        break;
                    default:
                        break;
                }
                graphService.getCentralGraphTraversalSource().tx().commit();
            }
        } catch (Throwable throwable) {
            LOG.fatal("WindupExecutionStatusConsumer.run broken");
            throwable.printStackTrace();
            throw new RuntimeException(throwable);
        }
    }
}
