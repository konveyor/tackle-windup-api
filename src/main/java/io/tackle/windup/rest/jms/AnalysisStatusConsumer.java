package io.tackle.windup.rest.jms;

import io.tackle.windup.rest.graph.GraphService;
import io.tackle.windup.rest.rest.WindupBroadcasterResource;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
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

@ApplicationScoped
public class AnalysisStatusConsumer implements Runnable {

    private static final Logger LOG = Logger.getLogger(AnalysisStatusConsumer.class);

    @Inject
    ConnectionFactory connectionFactory;

    @Inject
    GraphService graphService;

    @Inject
    WindupBroadcasterResource windupBroadcasterResource;

    private final ExecutorService scheduler = Executors.newSingleThreadExecutor();

    private volatile String lastUpdate;

    public String getLastUpdate() {
        return lastUpdate;
    }

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
                lastUpdate = message.getBody(String.class);
                LOG.debugf("lastUpdate = %s", lastUpdate);
                WindupExecution windupExecution = WindupExecutionJSONUtil.readJSON(lastUpdate);
                windupBroadcasterResource.broadcastMessage(lastUpdate);
                switch (windupExecution.getState()) {
                    case COMPLETED:
                        LOG.infof("COMPLETED: %s", lastUpdate);
                        String id = Long.toString(message.getLongProperty("projectId"));
                        windupBroadcasterResource.broadcastMessage(String.format("{\"id\":%s,\"state\":\"MERGING\",\"currentTask\":\"Merging into central graph\",\"totalWork\":1,\"workCompleted\":0}", id));
                        graphService.updateCentralJanusGraph(windupExecution.getOutputPath(), id);
                        windupBroadcasterResource.broadcastMessage(String.format("{\"id\":%s,\"state\":\"MERGED\",\"currentTask\":\"Merged into central graph\",\"totalWork\":1,\"workCompleted\":1}", id));
                        // TODO delete the application file now
                        LOG.debug("COMPLETED updateCentralJanusGraph");
                        break;
                    default:
                        break;
                }
            }
        } catch (Throwable throwable) {
            LOG.fatal("AnalysisStatusConsumer.run broken");
            throwable.printStackTrace();
            throw new RuntimeException(throwable);
        }
    }
}
