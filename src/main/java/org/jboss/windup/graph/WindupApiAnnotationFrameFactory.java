package org.jboss.windup.graph;

import com.syncleus.ferma.ReflectionCache;
import com.syncleus.ferma.framefactories.annotation.MethodHandler;

import java.util.Set;

public class WindupApiAnnotationFrameFactory extends AnnotationFrameFactory {

    public WindupApiAnnotationFrameFactory(ClassLoader classLoader, ReflectionCache reflectionCache) {
        super(classLoader, reflectionCache);
    }

    public WindupApiAnnotationFrameFactory(ClassLoader classLoader, ReflectionCache reflectionCache, Set<MethodHandler> handlers) {
        super(classLoader, reflectionCache, handlers);
    }

}
