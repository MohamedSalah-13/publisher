package com.hamzatech.publisher.service;

import com.hamzatech.publisher.service.GoogleCredentialsFile.Check;
import com.hamzatech.publisher.util.AppPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleCredentialsFileTest {

    private static final String DESKTOP_CLIENT =
            "{\"installed\":{\"client_id\":\"id.apps.example\",\"client_secret\":\"s3cret\"}}";

    @TempDir
    Path dir;

    private final Path installed = AppPaths.dataDir().resolve(GoogleCredentialsFile.FILE_NAME);

    @AfterEach
    void removeInstalledFile() throws IOException {
        Files.deleteIfExists(installed);
    }

    private Path file(String content) throws IOException {
        return Files.writeString(dir.resolve("download.json"), content);
    }

    @Test
    void desktopClientIsAccepted() throws IOException {
        assertEquals(Check.OK, GoogleCredentialsFile.check(file(DESKTOP_CLIENT)));
    }

    @Test
    void webClientIsNamedSoTheUserKnowsWhatToRedo() throws IOException {
        Path web = file("{\"web\":{\"client_id\":\"id\",\"client_secret\":\"s\"}}");

        assertEquals(Check.WEB_CLIENT, GoogleCredentialsFile.check(web));
    }

    @Test
    void otherJsonIsNotCredentials() throws IOException {
        assertEquals(Check.NOT_CREDENTIALS, GoogleCredentialsFile.check(file("{\"type\":\"service_account\"}")));
        assertEquals(Check.NOT_CREDENTIALS, GoogleCredentialsFile.check(file("[1,2]")));
        assertEquals(Check.NOT_CREDENTIALS, GoogleCredentialsFile.check(file("not json {")));
    }

    @Test
    void clientWithoutSecretIsNotCredentials() throws IOException {
        Path incomplete = file("{\"installed\":{\"client_id\":\"id\",\"client_secret\":\" \"}}");

        assertEquals(Check.NOT_CREDENTIALS, GoogleCredentialsFile.check(incomplete));
    }

    @Test
    void missingFileIsUnreadable() {
        assertEquals(Check.UNREADABLE, GoogleCredentialsFile.check(dir.resolve("absent.json")));
    }

    @Test
    void install_copiesIntoTheDataDirectoryUnderTheExpectedName() throws IOException {
        Path source = file(DESKTOP_CLIENT);

        Path target = GoogleCredentialsFile.install(source);

        assertEquals(installed, target);
        assertEquals(DESKTOP_CLIENT, Files.readString(installed));
        // نسخ لا نقل: ملف المستخدم في مجلد التنزيلات يبقى مكانه
        assertEquals(DESKTOP_CLIENT, Files.readString(source));
    }

    @Test
    void install_replacesAnEarlierFile() throws IOException {
        Files.writeString(installed, "old");

        GoogleCredentialsFile.install(file(DESKTOP_CLIENT));

        assertEquals(DESKTOP_CLIENT, Files.readString(installed));
    }

    private Path folderWithFile(String name) throws IOException {
        Path folder = Files.createDirectory(dir.resolve(name));
        Files.writeString(folder.resolve(GoogleCredentialsFile.FILE_NAME), name);
        return folder;
    }

    @Test
    void locate_prefersTheDataDirectoryOverABundledFile() throws IOException {
        // الملف الذي اختاره المستخدم يجب أن يغلب المضمَّن في المثبّت بجانب الـ JAR
        Path data = folderWithFile("data");
        Path jar = folderWithFile("jar");
        Path working = folderWithFile("working");

        assertEquals(data.resolve(GoogleCredentialsFile.FILE_NAME),
                GoogleCredentialsFile.locate(data, jar, working).orElseThrow());
    }

    @Test
    void locate_fallsBackToTheJarDirectoryThenTheWorkingDirectory() throws IOException {
        Path emptyData = Files.createDirectory(dir.resolve("data"));
        Path jar = folderWithFile("jar");
        Path working = folderWithFile("working");

        assertEquals(jar.resolve(GoogleCredentialsFile.FILE_NAME),
                GoogleCredentialsFile.locate(emptyData, jar, working).orElseThrow());
        // jarDir فارغ عند التشغيل من IDE
        assertEquals(working.resolve(GoogleCredentialsFile.FILE_NAME),
                GoogleCredentialsFile.locate(emptyData, null, working).orElseThrow());
    }

    @Test
    void locate_findsNothingWhenNoFolderHasTheFile() throws IOException {
        Path empty = Files.createDirectory(dir.resolve("empty"));

        assertTrue(GoogleCredentialsFile.locate(empty, null, empty).isEmpty());
    }

    @Test
    void install_refusesAnUnusableFileAndLeavesNothingBehind() throws IOException {
        Path web = file("{\"web\":{\"client_id\":\"id\",\"client_secret\":\"s\"}}");

        assertThrows(IllegalArgumentException.class, () -> GoogleCredentialsFile.install(web));
        assertFalse(Files.exists(installed));
    }
}
