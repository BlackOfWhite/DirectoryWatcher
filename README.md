# DirectoryWatcher

Implementation of JDK7's java.nio.file.WatchService that supports file change notifications. Use it to monitor a directory for changes. 

## Getting Started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes. See deployment for notes on how to deploy the project on a live system. There are six callbacks in the DirectoryWatcherCallback interface:
* onFileCreated
* onFileModified
* onFileRemoved
* onDirectoryCreated
* onDirectoryModified
* onDirectoryRemoved

Set *recursive* to enable recursion. Use *maxDepth* to set the maximum depth to traverse when recursively processing a directory.

Set *fileFilter* to monitor only files matching given filter, e.g., *.png*, *.txt*.

## Authors

* **Piotr Niewiński**

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details

## Acknowledgments

* Based on [Oracle Docs](https://docs.oracle.com/javase/tutorial/essential/io/notification.html)
