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
