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
package io.tackle.windup.rest.graph;

import com.syncleus.ferma.typeresolvers.TypeResolver;
import io.github.classgraph.AnnotationInfo;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import net.bytebuddy.ByteBuddy;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.janusgraph.core.JanusGraphEdge;
import org.jboss.logging.Logger;
import org.jboss.windup.graph.model.TypeValue;
import org.jboss.windup.graph.model.WindupFrame;
import org.jboss.windup.util.FurnaceCompositeClassLoader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Inspired by {@link org.jboss.windup.graph.GraphTypeManager}
 */
public class WindupTypeResolver implements TypeResolver {

    private static final Logger LOG = Logger.getLogger(WindupTypeResolver.class);

    private final FurnaceCompositeClassLoader compositeClassLoader;
    private Map<String, Class<WindupFrame<?>>> registeredTypes;
    private final Map<String, Class<?>> classCache = new HashMap<>();

    public WindupTypeResolver(FurnaceCompositeClassLoader compositeClassLoader) {
        this.compositeClassLoader = compositeClassLoader;
        getRegisteredTypeMap();
    }

    /**
     * Returns the type discriminator value for given Frames model class, extracted from the @TypeValue annotation.
     */
    public static String getTypeValue(Class<? extends WindupFrame<?>> clazz) {
        TypeValue typeValueAnnotation = clazz.getAnnotation(TypeValue.class);
        if (typeValueAnnotation == null)
            throw new IllegalArgumentException("Class " + clazz.getCanonicalName() + " lacks a @TypeValue annotation");
        return typeValueAnnotation.value();
    }

    private static Set<String> getTypeProperties(Element abstractElement) {
        Set<String> results = new HashSet<>();
        Iterator<? extends Property> properties = null;
        if (abstractElement instanceof Vertex) {
            // LOG.info("Getting from standardvertex as properties method");
            properties = ((Vertex) abstractElement).properties(WindupFrame.TYPE_PROP);
        } else if (abstractElement instanceof JanusGraphEdge) {
            Property<String> typeProperty = abstractElement.property(WindupFrame.TYPE_PROP);
            if (typeProperty.isPresent()) {
                List<String> all = Arrays.asList(((String) typeProperty.value()).split("\\|"));
                results.addAll(all);
                return results;
            }
        } else {
            // LOG.info("Using the old style properties method");
            properties = Collections.singleton(abstractElement.property(WindupFrame.TYPE_PROP)).iterator();
        }

        if (properties == null)
            return results;

        properties.forEachRemaining(property -> {
            if (property.isPresent())
                results.add((String) property.value());
        });
        return results;
    }

    private void initRegistry() {
        registeredTypes = new HashMap<>();
        try (ScanResult scanResult =
                     new ClassGraph()
                             .enableClassInfo()
                             .enableAnnotationInfo()
                             .acceptPackages(
                                     "io.tackle.windup.rest.graph.model",
                                     "org.jboss.windup.graph.model",
                                     "org.jboss.windup.rules.apps.java.model")
                             .rejectClasses("org.jboss.windup.graph.model.WindupExecutionModel")
                             .scan()) {
            for (ClassInfo annotatedClassInfo : scanResult.getClassesWithAnnotation(TypeValue.class)) {
                final Class<?> annotatedClass = annotatedClassInfo.loadClass();
                final AnnotationInfo annotationInfo = annotatedClassInfo.getAnnotationInfo(TypeValue.class);

                LOG.debugf("Adding type to registry: %s", annotatedClass.getName());

                // Do not attempt to add types without @TypeValue. We use
                // *Model types with no @TypeValue to function as essentially
                // "abstract" models that would never exist on their own (only as subclasses).
                if (annotationInfo == null) {
                    LOG.warnf("@%s is missing on type %s", TypeValue.class.getName(), annotatedClass.getName());
                    return;
                }

                if (getRegisteredTypeMap().containsKey(annotationInfo.getParameterValues().get(0).getValue().toString())) {
                    throw new IllegalArgumentException("Type value for model '" + annotatedClass.getCanonicalName()
                            + "' is already registered with model "
                            + getRegisteredTypeMap().get(annotationInfo.getParameterValues().get(0).getValue().toString()).getName());
                }
                getRegisteredTypeMap().put(annotationInfo.getParameterValues().get(0).getValue().toString(), (Class<WindupFrame<?>>) annotatedClass);
            }
        }
    }

    private synchronized Map<String, Class<WindupFrame<?>>> getRegisteredTypeMap() {
        if (registeredTypes == null) initRegistry();
        return registeredTypes;
    }

    private void addProperty(Element abstractElement, String propertyName, String propertyValue) {
        // This uses the direct Titan API which is indexed. See GraphContextImpl.
        if (abstractElement instanceof Vertex)
            ((Vertex) abstractElement).property(propertyName, propertyValue);
            // StandardEdge doesn't have addProperty().
        else if (abstractElement instanceof Edge)
            addTokenProperty(abstractElement, propertyName, propertyValue);
            // For all others, we resort to storing a list
        else {
            Property<List<String>> property = abstractElement.property(propertyName);
            if (property == null) {
                abstractElement.property(propertyName, Collections.singletonList(propertyValue));
            } else {
                List<String> existingList = property.value();
                List<String> newList = new ArrayList<>(existingList);
                newList.add(propertyValue);
                abstractElement.property(propertyName, newList);
            }
        }
    }

    private void addTokenProperty(Element el, String propertyName, String propertyValue) {
        Property<String> val = el.property(propertyName);
        if (!val.isPresent())
            el.property(propertyName, propertyValue);
        else
            el.property(propertyName, val.value() + "|" + propertyValue);
    }

    /**
     * Adds the type value to the field denoting which type the element represents.
     */
    public void addTypeToElement(Class<? extends WindupFrame<?>> kind, Element element) {
        TypeValue typeValueAnnotation = kind.getAnnotation(TypeValue.class);
        if (typeValueAnnotation == null)
            return;

        String typeValue = typeValueAnnotation.value();

        Set<String> types = getTypeProperties(element);

        // LOG.info("Adding type to element: " + element + " type: " + kind + " property is already present? " + types);
        for (String typePropertyValue : types) {
            if (typePropertyValue.equals(typeValue)) {
                // this is already in the list, so just exit now
                return;
            }
        }

        addProperty(element, WindupFrame.TYPE_PROP, typeValue);
        addSuperclassType(kind, element);
    }

    @SuppressWarnings("unchecked")
    private void addSuperclassType(Class<? extends WindupFrame<?>> kind, Element element) {
        for (Class<?> superInterface : kind.getInterfaces()) {
            if (WindupFrame.class.isAssignableFrom(superInterface)) {
                addTypeToElement((Class<? extends WindupFrame<?>>) superInterface, element);
            }
        }
    }

    /**
     * Returns the classes which this vertex/edge represents, typically subclasses. This will only return the lowest level subclasses (no superclasses
     * of types in the type list will be returned). This prevents Annotation resolution issues between superclasses and subclasses (see also:
     * WINDUP-168).
     */
    @Override
    public <T> Class<T> resolve(Element e, Class<T> defaultType) {
        final Set<String> valuesAll = getTypeProperties(e);
        if (valuesAll == null || valuesAll.isEmpty())
            return defaultType;

        List<Class<?>> resultClasses = new ArrayList<>();

        for (String value : valuesAll) {
            Class<?> type = registeredTypes.get(value);
            if (type != null) {
                // first check that no subclasses have already been added
                ListIterator<Class<?>> previouslyAddedIterator = resultClasses.listIterator();
                boolean shouldAdd = true;
                while (previouslyAddedIterator.hasNext()) {
                    Class<?> previouslyAdded = previouslyAddedIterator.next();
                    if (previouslyAdded.isAssignableFrom(type)) {
                        // Remove the previously added superclass
                        previouslyAddedIterator.remove();
                    } else if (type.isAssignableFrom(previouslyAdded)) {
                        // The current type is a superclass of a previously added type, don't add it
                        shouldAdd = false;
                    }
                }

                if (shouldAdd) {
                    resultClasses.add(type);
                }
            }
        }
        if (!resultClasses.isEmpty()) {
            // Ferma needs a single class, so create a composite one
            return (Class<T>) getClass(resultClasses);
        }
        return defaultType;
    }

    private Class<?> getClass(List<Class<?>> interfaces) {
        List<String> interfaceNames = interfaces.stream()
                .map(Class::getCanonicalName)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
        String key = interfaceNames.toString();

        Class<?> result = classCache.get(key);
        if (result == null) {
            result = new ByteBuddy()
                    .makeInterface()
                    .implement(interfaces).make()
                    .load(compositeClassLoader)
                    .getLoaded();
            classCache.put(key, result);
        }
        return result;
    }

    @Override
    public Class<?> resolve(Element element) {
        return resolve(element, WindupFrame.class);
    }

    @Override
    public void init(Element element, Class<?> kind) {
        if (WindupFrame.class.isAssignableFrom(kind)) {
            addTypeToElement((Class<? extends WindupFrame<?>>) kind, element);
        }
    }

    @Override
    public void deinit(Element element) {
        element.properties(WindupFrame.TYPE_PROP).forEachRemaining(Property::remove);
    }

    @Override
    public <P extends Element, T extends Element> GraphTraversal<P, T> hasType(GraphTraversal<P, T> traverser, Class<?> type) {
        String typeValue = getTypeValue((Class<? extends WindupFrame<?>>) type);
        return traverser.has(WindupFrame.TYPE_PROP, org.apache.tinkerpop.gremlin.process.traversal.P.eq(typeValue));
    }

    @Override
    public <P extends Element, T extends Element> GraphTraversal<P, T> hasNotType(GraphTraversal<P, T> traverser, Class<?> type) {
        String typeValue = getTypeValue((Class<? extends WindupFrame<?>>) type);
        return traverser.filter(new Predicate<Traverser<T>>() {
            @Override
            public boolean test(final Traverser<T> toCheck) {
                final Property<String> property = toCheck.get().property(WindupFrame.TYPE_PROP);
                if (!property.isPresent())
                    return true;

                final String resolvedType = property.value();
                if (typeValue.contains(resolvedType))
                    return false;
                else
                    return true;
            }
        });
    }
}
