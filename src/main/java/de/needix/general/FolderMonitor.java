package de.needix.general;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.logging.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FolderMonitor {
    private static final Logger LOGGER = Logger.getLogger(FolderMonitor.class.getName());
    private static final Path WATCH_FOLDER = Paths.get("/home/need/Downloads/JDownloader");
    private static final Path MUSIC_DESTINATION = Paths.get("/mnt/music/Music/New/");
    private static final Path MP3_TOCOPY_DESTINATION = Paths.get("/mnt/music/MP3ToCopy/");
    private static final Path MP3_UNSORTED_DESTINATION = Paths.get("/mnt/music/MP3/Unsorted/");
    private static final Set<Path> processedFolders = new HashSet<>();

    static {
        setupLogger();
    }

    private static void setupLogger() {
        try {
            FileHandler fileHandler = new FileHandler("foldermonitor_%g.log", 5242880, 5, true);
            fileHandler.setFormatter(new SimpleFormatter() {
                @Override
                public String format(LogRecord record) {
                    return String.format("[%s] %s: %s%n",
                            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                            record.getLevel(),
                            record.getMessage());
                }
            });
            LOGGER.addHandler(fileHandler);
            LOGGER.setLevel(Level.ALL);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        LOGGER.info("Starting folder monitor application");
        
        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            WATCH_FOLDER.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
            
            while (true) {
                WatchKey key = watchService.take();
                
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        Path newPath = WATCH_FOLDER.resolve((Path) event.context());
                        
                        if (Files.isDirectory(newPath) && !processedFolders.contains(newPath)) {
                            LOGGER.info("New folder detected: " + newPath);
                            processNewFolder(newPath);
                            processedFolders.add(newPath);
                        }
                    }
                }
                
                key.reset();
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.severe("Error in main monitoring loop: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void processNewFolder(Path folder) {
        long timeToWait = 5;

        LOGGER.info("Waiting "+timeToWait+" minutes before processing folder: " + folder);
        try {
            Thread.sleep(timeToWait * 60 * 1000);
            
            // Process MP4 files
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*.mp4")) {
                for (Path file : stream) {
                    copyFile(file, MUSIC_DESTINATION.resolve(file.getFileName()));
                }
            }

            // Process M4A files
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*.m4a")) {
                for (Path file : stream) {
                    // Copy to MP3ToCopy
                    copyFile(file, MP3_TOCOPY_DESTINATION.resolve(file.getFileName()));
                    // Copy to MP3/Unsorted
                    copyFile(file, MP3_UNSORTED_DESTINATION.resolve(file.getFileName()));
                }
            }

            // Delete the folder and its contents
            deleteFolder(folder);

        } catch (InterruptedException e) {
            LOGGER.severe("Sleep interrupted: " + e.getMessage());
        } catch (IOException e) {
            LOGGER.severe("Error in main monitoring loop: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void deleteFolder(Path folder) {
        try {
            Files.walkFileTree(folder, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    LOGGER.info("Deleted file: " + file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    LOGGER.info("Deleted directory: " + dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LOGGER.severe("Error deleting folder " + folder + ": " + e.getMessage());
        }
    }


    private static void copyFile(Path source, Path destination) {
        try {
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Successfully copied " + source + " to " + destination);
        } catch (IOException e) {
            LOGGER.severe("Error copying " + source + " to " + destination + ": " + e.getMessage());
        }
    }
}
