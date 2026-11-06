package com.hunter.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadStorageServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void storesValidPngUnderRandomControlledStorageKey() throws Exception {
        UploadStorageService storage = new UploadStorageService(temporaryDirectory.toString());
        MockMultipartFile upload = new MockMultipartFile(
                "image", "..\\private\\label.png", "image/png", imageBytes("png")
        );

        UploadStorageService.StoredImage stored = storage.store(upload);

        Path saved = storage.resolveStorageKey(stored.storageKey());
        assertThat(saved.getParent()).isEqualTo(temporaryDirectory);
        assertThat(Files.isRegularFile(saved)).isTrue();
        assertThat(stored.mediaType()).isEqualTo("image/png");
        assertThat(stored.originalName()).isEqualTo("label.png");
        storage.delete(stored.storageKey());
        assertThat(Files.exists(saved)).isFalse();
    }

    @Test
    void rejectsSpoofedImageTypeAndPathLikeStorageKeys() throws Exception {
        UploadStorageService storage = new UploadStorageService(temporaryDirectory.toString());
        MockMultipartFile invalidImage = new MockMultipartFile(
                "image", "label.png", "image/png", "not an image".getBytes()
        );

        assertThatThrownBy(() -> storage.store(invalidImage))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("file content");
        assertThatThrownBy(() -> storage.resolveStorageKey("..\\secret.png"))
                .isInstanceOf(ResponseStatusException.class);
        try (var files = Files.list(temporaryDirectory)) {
            assertThat(files.toList()).isEmpty();
        }
    }

    private static byte[] imageBytes(String format) throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, format, bytes);
        return bytes.toByteArray();
    }
}
