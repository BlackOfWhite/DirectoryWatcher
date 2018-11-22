import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static org.directory.watcher.DirectoryWatcher.ALLOW_ALL;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.directory.watcher.DirectoryWatcher;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DirectoryWatcherTest {

  private static final Path resourcesDirectory = Paths.get(new File("src/test/resources").getAbsolutePath());
  private static Logger logger = LoggerFactory.getLogger(DirectoryWatcherTest.class);
  private List<PathEvent> eventList = Collections.synchronizedList(new ArrayList<>());

  @Before
  public void setUp() {
    eventList.clear();
  }

  @Test
  public void overrideAndInitialize() {
    try {
      DirectoryWatcher watcher = getWatcher();
      Assert.assertNotNull(watcher);
      Assert.assertEquals(watcher.isRecursive(), true);
      Assert.assertEquals(watcher.getFileFilter(), ALLOW_ALL);
      Assert.assertEquals(watcher.getMaxDepth(), Integer.MAX_VALUE);
      Assert.assertEquals(watcher.isNotifyDirectories(), true);
      watcher.start();
      Thread.sleep(3000);
      Assert.assertEquals(eventList.size(), 4);
      Assert.assertThat(eventList, CoreMatchers
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
      Thread.sleep(1000);
      watcher.close();
      Assert.assertFalse(watcher.isRunning());
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }
  }

  private DirectoryWatcher getWatcher() throws IOException {
    return new DirectoryWatcher(resourcesDirectory, true) {
      @Override
      public void pathCreated(Path path) {
        logger.info("Path created: " + path);
        eventList.add(new PathEvent(path, ENTRY_CREATE));
      }

      @Override
      public void pathModified(Path path) {
        logger.info("Path modified: " + path);
        eventList.add(new PathEvent(path, ENTRY_MODIFY));
      }

      @Override
      public void pathRemoved(Path path) {
        logger.info("Path removed: " + path);
        eventList.add(new PathEvent(path, ENTRY_DELETE));
      }
    };
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
  }
}
