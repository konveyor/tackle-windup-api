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
