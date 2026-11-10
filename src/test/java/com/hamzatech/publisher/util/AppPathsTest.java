package com.hamzatech.publisher.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppPathsTest {

    @Test
    void dataDir_honoursTheOverrideProperty_soTestsStayIsolated() {
        String override = System.getProperty(AppPaths.DATA_DIR_PROPERTY);
        assertTrue(override != null && !override.isBlank(),
                "surefire must set " + AppPaths.DATA_DIR_PROPERTY + ", otherwise tests write to real user data");
        assertEquals(Path.of(override).toAbsolutePath(), AppPaths.dataDir());
    }

    @Test
    void dataDir_isCreatedIfMissing() {
        assertTrue(Files.isDirectory(AppPaths.dataDir()));
    }

    @Test
    void dataFile_resolvesInsideTheDataDirectory() {
        Path file = AppPaths.dataFile("some_file.json");
        assertEquals(AppPaths.dataDir(), file.getParent());
        assertEquals("some_file.json", file.getFileName().toString());
    }

    @Test
    void dataFile_movesALegacyFileOutOfTheWorkingDirectory() throws IOException {
        String name = "legacy_migration_probe.json";
        Path legacy = Path.of(name).toAbsolutePath();
        Path target = AppPaths.dataDir().resolve(name);
        Files.deleteIfExists(target);
        Files.writeString(legacy, "{\"from\":\"working dir\"}");

        try {
            Path resolved = AppPaths.dataFile(name);

            assertEquals(target, resolved);
            assertTrue(Files.exists(target), "the file should now live in the data directory");
            assertEquals("{\"from\":\"working dir\"}", Files.readString(target));
        } finally {
            Files.deleteIfExists(legacy);
            Files.deleteIfExists(target);
        }
    }

    @Test
    void dataFile_neverOverwritesAnExistingFileInTheDataDirectory() throws IOException {
        String name = "legacy_conflict_probe.json";
        Path legacy = Path.of(name).toAbsolutePath();
        Path target = AppPaths.dataDir().resolve(name);
        Files.writeString(target, "current");
        Files.writeString(legacy, "legacy");

        try {
            AppPaths.dataFile(name);
            assertEquals("current", Files.readString(target), "existing data must win");
        } finally {
            Files.deleteIfExists(legacy);
            Files.deleteIfExists(target);
        }
    }

    @Test
    void dataFile_removesTheLegacyFile_soNoPlainTextIsLeftBehind() throws IOException {
        // كان النقل يستخدم ATOMIC_MOVE وحده، وهو يفشل حين يكون مجلد المشروع على قرص
        // غير قرص مجلد البيانات، فيسقط إلى النسخ ويترك الأصل الصريح مكانه إلى الأبد
        String name = "legacy_removal_probe.json";
        Path legacy = Path.of(name).toAbsolutePath();
        Path target = AppPaths.dataDir().resolve(name);
        Files.deleteIfExists(target);
        Files.writeString(legacy, "secret");

        try {
            AppPaths.dataFile(name);

            assertTrue(Files.exists(target), "the file must arrive in the data directory");
            assertFalse(Files.exists(legacy), "the working-directory copy must be gone");
        } finally {
            Files.deleteIfExists(legacy);
            Files.deleteIfExists(target);
        }
    }
}
