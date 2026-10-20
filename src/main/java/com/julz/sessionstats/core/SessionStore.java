package com.julz.sessionstats.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Saves each session as its own JSON file under {@code <root>/<world folder>/<start time>.json},
 * for example {@code config/session-stats/server_play.example.com/2026-10-19_20-15-03.json}.
 */
public final class SessionStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter FILE_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);

    private final Path root;
    private final ZoneId zone;

    public SessionStore(Path root, ZoneId zone) {
        this.root = root;
        this.zone = zone;
    }

    public Path root() {
        return root;
    }

    /** A folder name for a world or server: safe on every OS, readable, and stable between visits. */
    public static String folderName(String kind, String name) {
        String safe = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_").replaceAll("^[._]+|_+$", "");
        if (safe.isEmpty()) {
            safe = "unnamed";
        }
        if (safe.length() > 64) {
            safe = safe.substring(0, 64);
        }
        return kind + "_" + safe;
    }

    /** Writes the session, replacing the file of an earlier save of the same session. */
    public Path save(String folder, Session session) throws IOException {
        Path dir = root.resolve(folder);
        Files.createDirectories(dir);
        Path file = dir.resolve(FILE_NAME.format(Instant.ofEpochMilli(session.startedAt).atZone(zone)) + ".json");
        // Written next to the target first, so a crash mid-write never leaves a broken file.
        Path temp = dir.resolve(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp)) {
            GSON.toJson(session, writer);
        }
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return file;
    }

    /** The world folders that have sessions, sorted by name. */
    public List<String> folders() throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> dirs = Files.list(root)) {
            return dirs.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    /** All sessions in one world folder, or in every folder when {@code folder} is null. Broken files are skipped. */
    public List<Session> load(String folder) throws IOException {
        List<Session> sessions = new ArrayList<>();
        for (String name : folder != null ? List.of(folder) : folders()) {
            Path dir = root.resolve(name);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                    Session session = read(file);
                    if (session != null) {
                        sessions.add(session);
                    }
                }
            }
        }
        return sessions;
    }

    private static Session read(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            Session session = GSON.fromJson(reader, Session.class);
            if (session == null) {
                return null;
            }
            if (session.stats == null) {
                session.stats = new java.util.LinkedHashMap<>();
            }
            if (session.world == null) {
                session.world = "";
            }
            return session;
        } catch (IOException | JsonParseException e) {
            return null;
        }
    }
}
