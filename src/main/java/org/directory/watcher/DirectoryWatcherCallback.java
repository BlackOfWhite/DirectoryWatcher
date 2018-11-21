package org.directory.watcher;

import java.nio.file.Path;

public interface DirectoryWatcherCallback {

  void pathCreated(Path path);

  void pathModified(Path path);

  void pathRemoved(Path path);
}
