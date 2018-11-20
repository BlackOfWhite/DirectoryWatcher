package org.directory.watcher;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DirectoryWatcher implements DirectoryWatcherCallback, Runnable {

  private static final String ALLOW_ALL = "ALLOW_ALL";
  private static Logger logger = LoggerFactory.getLogger(DirectoryWatcher.class);
  private final boolean recursive;
  private Path rootPath;
  private WatchService service;
  private Map<WatchKey, Path> keys;
  private WatchEvent.Kind[] WATCH_EVENTS = new WatchEvent.Kind[]{ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE};
  private int maxDepth;
  private String fileFilter;
  private Thread thread;

  public DirectoryWatcher(Path rootPath, boolean recursive) throws IOException {
    this(rootPath, recursive, Integer.MAX_VALUE, ALLOW_ALL);
  }

  public DirectoryWatcher(Path rootPath, boolean recursive, int maxDepth, String fileFilter) throws IOException {
    this.rootPath = rootPath;
    this.service = FileSystems.getDefault().newWatchService();
    this.recursive = recursive;
    this.keys = new HashMap<>();
    this.maxDepth = maxDepth;
    this.fileFilter = fileFilter;
    this.thread = new Thread(this, "DirectoryWatcherRunnable");
  }

  @SuppressWarnings("unchecked")
  static <T> WatchEvent<T> cast(WatchEvent<?> event) {
    return (WatchEvent<T>) event;
  }

  public void start() {
    try {
      this.thread.start();
    } catch (IllegalStateException e) {
      logger.warn("DirectoryWatcher was already started.", e);
    }
  }

  /**
   * Register the given directory with the WatchService
   */
  private void register(Path dir) throws IOException {
    WatchKey key = dir.register(service, WATCH_EVENTS);
    keys.put(key, dir);
  }

  /**
   * Register the given directory, and all its sub-directories, with the
   * WatchService.
   */
  private void registerAll(final Path start) throws IOException {
    // register directory and sub-directories
    Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
          throws IOException {
        if (Files.isDirectory(dir)) {
          register(dir);
        } else if (isFileAllowed(dir)) {
          callback();
          register(dir);
        }
        return FileVisitResult.CONTINUE;
      }
    });
  }

  /**
   * Process all events for keys queued to the watcher
   */
  void processEvents() {
    for (; ; ) {
      WatchKey key;
      try {
        key = service.take();
      } catch (InterruptedException x) {
        return;
      }

      Path dir = keys.get(key);
      if (dir == null) {
        continue;
      }

      for (WatchEvent<?> event : key.pollEvents()) {
        WatchEvent.Kind kind = event.kind();

        // Context for directory entry event is the file name of entry
        WatchEvent<Path> ev = cast(event);
        Path name = ev.context();
        Path child = dir.resolve(name);

        if (Files.isDirectory(child, NOFOLLOW_LINKS) && recursive && (kind == ENTRY_CREATE) && getPathDepth(child) < maxDepth) {
          try {
            registerAll(child);
          } catch (IOException e) {
            logger.warn("Failed to access directory: {}", child, e);
          }
        } else if (isFileAllowed(child)) {
          callback();
        }
      }

      // reset key and remove from set if directory no longer accessible
      boolean valid = key.reset();
      if (!valid) {
        keys.remove(key);
        // all directories are inaccessible
        if (keys.isEmpty()) {
          break;
        }
      }
    }
  }

  private int getPathDepth(Path path) {
    if (!path.toAbsolutePath().startsWith(rootPath.toAbsolutePath())) {
      return -1;
    }
    return path.getNameCount() - rootPath.getNameCount();
  }

  private boolean isFileAllowed(Path file) {
    return fileFilter.equals(ALLOW_ALL) || file.toString().endsWith(fileFilter);
  }

  public int getMaxDepth() {
    return maxDepth;
  }

  public void setMaxDepth(int maxDepth) {
    this.maxDepth = maxDepth;
  }

  public String getFileFilter() {
    return fileFilter;
  }

  public void setFileFilter(String fileFilter) {
    this.fileFilter = fileFilter;
  }

  public boolean isRecursive() {
    return recursive;
  }

  public Path getRootPath() {
    return rootPath;
  }

  @Override
  public void callback() {
    logger.debug("DirectoryWatcher callback");
  }

  @Override
  public void run() {
    try {
      if (recursive) {
        registerAll(rootPath);
      } else {
        register(rootPath);
      }
    } catch (IOException e) {
      logger.warn("DirectoryWatcher was unable to register path: {}", e.getMessage(), e);
      Thread.currentThread().interrupt();
      return;
    }
    processEvents();
  }
}
