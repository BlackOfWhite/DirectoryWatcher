import org.directory.watcher.DirectoryWatcher;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.util.*;

import static java.nio.file.StandardWatchEventKinds.*;
import static junit.framework.Assert.assertFalse;
import static junit.framework.TestCase.assertTrue;

public class DirectoryWatcherTest {

    private static final Path resourcesDirectory = Paths.get(new File("src/test/resources").getAbsolutePath());
    private static Logger logger = LoggerFactory.getLogger(DirectoryWatcherTest.class);
    private List<PathEvent> eventListCreate = Collections.synchronizedList(new ArrayList<>());
    private List<PathEvent> eventListModify = Collections.synchronizedList(new ArrayList<>());
    private List<PathEvent> eventListDelete = Collections.synchronizedList(new ArrayList<>());
    Path testLevel23_txt = Paths.get(resourcesDirectory.toString(), "testDir11", "testLevel23.txt");
    Path testLevel23_png = Paths.get(resourcesDirectory.toString(), "testDir11", "testLevel23.png");

    @Before
    public void setUp() {
        testLevel23_txt.toFile().delete();
        testLevel23_png.toFile().delete();
        eventListCreate.clear();
        eventListModify.clear();
        eventListDelete.clear();
    }

    @Test
    public void overrideAndInitialize() {
        try {
            DirectoryWatcher watcher = getWatcher();
            startWatcherWithDelay(watcher);

            Assert.assertNotNull(watcher);
            Assert.assertEquals(watcher.isRecursive(), true);
            Assert.assertTrue(watcher.getFileFilters().isEmpty());
            Assert.assertEquals(watcher.getMaxDepth(), Integer.MAX_VALUE);
            Assert.assertEquals(watcher.isNotifyDirectories(), true);
            Assert.assertEquals(eventListCreate.size(), 4);
            Assert.assertThat(eventListCreate, CoreMatchers
                    .hasItems(new PathEvent(resourcesDirectory, ENTRY_CREATE),
                            new PathEvent(Paths.get(resourcesDirectory.toString(), "testDir11"), ENTRY_CREATE),
                            new PathEvent(Paths.get(resourcesDirectory.toString(), "testDir12"), ENTRY_CREATE),
                            new PathEvent(Paths.get(resourcesDirectory.toString(), "testDir11", "testDir21"), ENTRY_CREATE)));

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void startAndStopTest() {
        try {
            DirectoryWatcher watcher = getWatcher();
            watcher.start();
            Assert.assertTrue(watcher.isRunning());
            Thread.sleep(500);
            watcher.close();
            Assert.assertFalse(watcher.isRunning());
            watcher.start();
            Assert.assertTrue(watcher.isRunning());
            Thread.sleep(500);
            watcher.close();
            Assert.assertFalse(watcher.isRunning());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void manipulateSingleFileTest() throws IOException, InterruptedException {
        DirectoryWatcher watcher = getWatcher();
        startWatcherWithDelay(watcher);

        File file = testLevel23_txt.toFile();
        file.createNewFile();
        assertTrue(file.exists());
        Thread.sleep(500);
        Assert.assertThat(eventListCreate, CoreMatchers
                .hasItems(new PathEvent(file.toPath(), ENTRY_CREATE)));
        file.delete();
        assertFalse(file.exists());
        Thread.sleep(500);
        Assert.assertThat(eventListModify, CoreMatchers
                .hasItems(new PathEvent(file.toPath(), ENTRY_MODIFY)));
        Assert.assertThat(eventListDelete, CoreMatchers
                .hasItems(new PathEvent(file.toPath(), ENTRY_DELETE)));
    }

    @Test
    public void singleFileFilter() throws IOException, InterruptedException {
        DirectoryWatcher watcher = getWatcher(Integer.MAX_VALUE, Arrays.asList(".png"));
        startWatcherWithDelay(watcher);

        File file = testLevel23_txt.toFile();
        File file2 = testLevel23_png.toFile();
        file.createNewFile();
        file2.createNewFile();
        assertTrue(file.exists() && file2.exists());
        Thread.sleep(500);
        Assert.assertFalse(eventListCreate.contains(new PathEvent(file.toPath(), ENTRY_CREATE)));
        Assert.assertTrue(eventListCreate.contains(new PathEvent(file2.toPath(), ENTRY_CREATE)));
        file.delete();
        file2.delete();
        assertTrue(!file.exists() && !file2.exists());
        Thread.sleep(500);
        Assert.assertFalse(eventListModify.contains(new PathEvent(file.toPath(), ENTRY_MODIFY)));
        Assert.assertFalse(eventListDelete.contains(new PathEvent(file.toPath(), ENTRY_DELETE)));
        Assert.assertThat(eventListModify, CoreMatchers
                .hasItems(new PathEvent(file2.toPath(), ENTRY_MODIFY)));
        Assert.assertThat(eventListDelete, CoreMatchers
                .hasItems(new PathEvent(file2.toPath(), ENTRY_DELETE)));
    }

    private DirectoryWatcher getWatcher() throws IOException {
        return new DirectoryWatcher(resourcesDirectory, true) {
            @Override
            public void pathCreated(Path path) {
                logger.info("Path created: " + path);
                eventListCreate.add(new PathEvent(path, ENTRY_CREATE));
            }

            @Override
            public void pathModified(Path path) {
                logger.info("Path modified: " + path);
                eventListModify.add(new PathEvent(path, ENTRY_MODIFY));
            }

            @Override
            public void pathRemoved(Path path) {
                logger.info("Path removed: " + path);
                eventListDelete.add(new PathEvent(path, ENTRY_DELETE));
            }
        };
    }

    private DirectoryWatcher getWatcher(int depth, List<String> fileFilters) throws IOException {
        return new DirectoryWatcher(resourcesDirectory, true, depth, fileFilters) {
            @Override
            public void pathCreated(Path path) {
                logger.info("Path created: " + path);
                eventListCreate.add(new PathEvent(path, ENTRY_CREATE));
            }

            @Override
            public void pathModified(Path path) {
                logger.info("Path modified: " + path);
                eventListModify.add(new PathEvent(path, ENTRY_MODIFY));
            }

            @Override
            public void pathRemoved(Path path) {
                logger.info("Path removed: " + path);
                eventListDelete.add(new PathEvent(path, ENTRY_DELETE));
            }
        };
    }

    private DirectoryWatcher startWatcherWithDelay(DirectoryWatcher watcher) throws IOException, InterruptedException {
        watcher.start();
        Thread.sleep(500); // only for test purposes
        return watcher;
    }

    private class PathEvent {

        private Path path;
        private WatchEvent.Kind kind;

        public PathEvent(Path path, Kind kind) {
            this.path = path;
            this.kind = kind;
        }

        public Path getPath() {
            return path;
        }

        public Kind getKind() {
            return kind;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PathEvent pathEvent = (PathEvent) o;
            return Objects.equals(path, pathEvent.path) &&
                    Objects.equals(kind, pathEvent.kind);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, kind);
        }

        @Override
        public String toString() {
            return "PathEvent{" +
                    "path=" + path +
                    ", kind=" + kind +
                    '}';
        }
    }
}
