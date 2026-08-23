package repository;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Centralised CSV file I/O using try-with-resources. Creates parent dirs as needed. */
public final class FileManager {

    private FileManager() {}

    public static List<String> readLines(String path) {
        List<String> lines = new ArrayList<>();
        Path p = Path.of(path);
        if (!Files.exists(p)) return lines;
        try (BufferedReader reader = Files.newBufferedReader(p)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank() && !line.startsWith("#")) lines.add(line);
            }
        } catch (IOException e) {
            System.out.println("[warn] Could not read " + path + ": " + e.getMessage());
        }
        return lines;
    }

    public static void writeLines(String path, List<String> lines) {
        Path p = Path.of(path);
        try {
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(p)) {
                for (String line : lines) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            System.out.println("[warn] Could not write " + path + ": " + e.getMessage());
        }
    }

    public static void appendLine(String path, String line) {
        List<String> existing = readLines(path);
        existing.add(line);
        writeLines(path, existing);
    }
}
