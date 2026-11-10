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

    @Test
    void install_refusesAnUnusableFileAndLeavesNothingBehind() throws IOException {
        Path web = file("{\"web\":{\"client_id\":\"id\",\"client_secret\":\"s\"}}");

        assertThrows(IllegalArgumentException.class, () -> GoogleCredentialsFile.install(web));
        assertFalse(Files.exists(installed));
    }
}
