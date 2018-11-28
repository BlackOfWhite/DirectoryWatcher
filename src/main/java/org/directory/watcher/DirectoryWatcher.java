package org.directory.watcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.*;

public class DirectoryWatcher implements DirectoryWatcherCallback, Runnable {

    private static final String THREAD_ID = "DirectoryWatcherRunnable";
    private static Logger logger = LoggerFactory.getLogger(DirectoryWatcher.class);
    private final boolean recursive;
    private Path rootPath;
    private WatchService service;
    private Map<WatchKey, Path> keys;
    private WatchEvent.Kind[] WATCH_EVENTS = new WatchEvent.Kind[]{ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE};
    private int maxDepth;
    private List<String> fileFilters;
    private Thread thread;
    private boolean notifyDirectories;
    private volatile boolean running;

    public DirectoryWatcher(Path rootPath, boolean recursive) throws IOException {
        this(rootPath, recursive, Integer.MAX_VALUE, Collections.emptyList());
    }

    public DirectoryWatcher(Path rootPath, boolean recursive, int maxDepth, List<String> fileFilters) throws IOException {
        this.rootPath = rootPath;
        this.service = FileSystems.getDefault().newWatchService();
        this.recursive = recursive;
        this.keys = new HashMap<>();
        this.maxDepth = maxDepth;
        this.fileFilters = fileFilters;
        this.thread = new Thread(this, THREAD_ID);
        this.notifyDirectories = true;
    }

    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>) event;
    }

    public void start() throws IOException {
        try {
            this.running = true;
            this.service = FileSystems.getDefault().newWatchService();
            this.thread = new Thread(this, THREAD_ID);
            this.thread.start();
        } catch (IllegalStateException e) {
            logger.warn("DirectoryWatcher was already started.", e);
        }
    }

    public void close() {
        try {
            this.service.close();
        } catch (IOException e) {
            logger.warn("Unexpected exception while closing WatchService.", e);
        }
        this.running = false;
        this.service.close();
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
                    callback(dir, ENTRY_CREATE);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Process all events for keys queued to the watcher
     */
    void processEvents() {
        while (running) {
            WatchKey key;
            try {
                key = service.take();
            } catch (ClosedWatchServiceException e) {
                continue;
            } catch (InterruptedException e) {
                logger.warn("Unexpected exception while running {} thread.", THREAD_ID, e);
                close();
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
                } else if (isFileAllowed(child)) { // callbacks for files from the root level
                    callback(child, kind);
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
        logger.info("{} was successfully stopped.", THREAD_ID);
    }

    private int getPathDepth(Path path) {
        if (!path.toAbsolutePath().startsWith(rootPath.toAbsolutePath())) {
            return -1;
        }
        return path.getNameCount() - rootPath.getNameCount();
    }

    private boolean isFileAllowed(Path file) {
        return fileFilters.isEmpty() || fileFilters.stream().anyMatch(s -> file.toString().endsWith(s));
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public List<String> getFileFilters() {
        return fileFilters;
    }

    public void setFileFilters(List<String> fileFilters) {
        this.fileFilters = fileFilters;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public Path getRootPath() {
        return rootPath;
    }

    public boolean isNotifyDirectories() {
        return notifyDirectories;
    }

    public void setNotifyDirectories(boolean notifyDirectories) {
        this.notifyDirectories = notifyDirectories;
    }

    public boolean isRunning() {
        return running;
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

    @Override
    public void pathCreated(Path path) {
        logger.debug("Path created.");
    }

    @Override
    public void pathModified(Path path) {
        logger.debug("Path modified.");
    }

    @Override
    public void pathRemoved(Path path) {
        logger.debug("Path removed.");
    }

    private void callback(Path path, WatchEvent.Kind kind) {
        if (Files.isDirectory(path) && !notifyDirectories) {
            logger.debug("Notifications for directories are off.");
        }
        if (kind == ENTRY_CREATE) {
            pathCreated(path);
        } else if (kind == ENTRY_MODIFY) {
            pathModified(path);
        } else if (kind == ENTRY_DELETE) {
            pathRemoved(path);
        } else {
        }
    }
}
