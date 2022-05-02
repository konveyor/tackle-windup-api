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
package io.tackle.windup.rest.util;

import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class WindupUtil {

    private static final Logger LOG = Logger.getLogger(WindupUtil.class);

    public static boolean deletePath(final String path) {
        return deletePath(Paths.get(path));
    }

    public static boolean deletePath(final Path path) {
        final AtomicBoolean result = new AtomicBoolean(true);
        if (Files.exists(path)) {
            try (Stream<Path> paths = Files.walk(path)) {
                paths.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(file -> {
                            LOG.debugf("Delete %s", file.getAbsolutePath());
                            result.set(result.get() && file.delete());
                        });
            } catch (IOException e) {
                LOG.errorf(e, "Failed to delete folder %s due to %s", path.toString(), e.getMessage());
            }
        }
        return result.get();
    }
}
