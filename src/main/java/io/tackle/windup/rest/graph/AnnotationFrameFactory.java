package io.tackle.windup.rest.graph;

import com.syncleus.ferma.ReflectionCache;
import com.syncleus.ferma.framefactories.annotation.AbstractAnnotationFrameFactory;
import com.syncleus.ferma.framefactories.annotation.AdjacencyMethodHandler;
import com.syncleus.ferma.framefactories.annotation.InVertexMethodHandler;
import com.syncleus.ferma.framefactories.annotation.IncidenceMethodHandler;
import com.syncleus.ferma.framefactories.annotation.MethodHandler;
import com.syncleus.ferma.framefactories.annotation.OutVertexMethodHandler;
import com.syncleus.ferma.framefactories.annotation.PropertyMethodHandler;

import java.util.HashSet;
import java.util.Set;

public class AnnotationFrameFactory extends AbstractAnnotationFrameFactory {

    public AnnotationFrameFactory(final ReflectionCache reflectionCache) {
        super(reflectionCache, collectHandlers(null));
    }

    /**
     * Subclasses can use this constructor to add additional custom method handlers.
     *
     * @param reflectionCache The reflection cache used to inspect annotations.
     * @param handlers The handlers used to generate new annotation support.
     */
    public AnnotationFrameFactory(final ReflectionCache reflectionCache, Set<MethodHandler> handlers) {
        super(reflectionCache, collectHandlers(handlers));
    }

    private static final Set<MethodHandler> collectHandlers(Set<MethodHandler> additionalHandlers) {
        final Set<MethodHandler> methodHandlers = new HashSet<>();

        final PropertyMethodHandler propertyHandler = new PropertyMethodHandler();
        methodHandlers.add(propertyHandler);

        final InVertexMethodHandler inVertexHandler = new InVertexMethodHandler();
        methodHandlers.add(inVertexHandler);

        final OutVertexMethodHandler outVertexHandler = new OutVertexMethodHandler();
        methodHandlers.add(outVertexHandler);

        final AdjacencyMethodHandler adjacencyHandler = new AdjacencyMethodHandler();
        methodHandlers.add(adjacencyHandler);

        final IncidenceMethodHandler incidenceHandler = new IncidenceMethodHandler();
        methodHandlers.add(incidenceHandler);

        if(additionalHandlers != null)
            methodHandlers.addAll(additionalHandlers);

        return methodHandlers;
    }

}
