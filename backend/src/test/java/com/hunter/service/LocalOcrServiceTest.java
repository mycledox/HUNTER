package com.hunter.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises LocalOcrService with a configurable fake executable so the
 * success, timeout and failure branches of extractText run without an
 * installed Tesseract binary.
 */
class LocalOcrServiceTest {

    @TempDir
    Path tempDir;

    private static final UploadStorageService.ValidatedImage IMAGE = new UploadStorageService.ValidatedImage(
            new byte[] { 1, 2, 3 }, "image/png");

    private Path writeScript(String name, String body) throws IOException {
        Path script = tempDir.resolve(name);
        Files.writeString(script, body, StandardCharsets.UTF_8);
        return script;
    }

    private LocalOcrService service(Path executable) {
        return new LocalOcrService(executable.toString(), "eng");
    }

    @Test
    void failingExecutableYieldsBadGateway() throws Exception {
        Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase().contains("win"),
                "The fake OCR executable is a POSIX shell script.");
        // A non-zero exit with a stderr message surfaces as 502 with the detail.
        Path script = writeScript("ocr-fail.sh",
                "#!/bin/sh\n" +
                "echo simulated tesseract failure >&2\n" +
                "exit 3\n");
        script.toFile().setExecutable(true);
        LocalOcrService failing = service(script);
        assertThatThrownBy(() -> failing.extractTextSkippingProbe(IMAGE))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode().value()).isEqualTo(502);
                    assertThat(error.getReason()).contains("simulated tesseract failure");
                });
    }

    @Test
    void slowExecutableYieldsGatewayTimeout() throws Exception {
        Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase().contains("win"),
                "The fake OCR executable is a POSIX shell script.");
        // A fake executable that never finishes within the timeout budget.
        Path script = writeScript("ocr-slow.sh",
                "#!/bin/sh\n" +
                "sleep 60\n");
        script.toFile().setExecutable(true);
        LocalOcrService slow = service(script);
        long start = System.nanoTime();
        assertThatThrownBy(() -> slow.extractTextSkippingProbe(IMAGE))
                .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                        assertThat(error.getStatusCode().value()).isEqualTo(504));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        // Must bail out at the 45 s OCR timeout, well before the script would end.
        assertThat(elapsedMillis).isLessThan(50_000);
    }

    @Test
    void notInstalledExecutableYieldsServiceUnavailable() {
        // A path that can never start a process on any platform.
        LocalOcrService broken = new LocalOcrService(
                tempDir.resolve("missing-binary-xyz").toString(), "eng");
        assertThatThrownBy(() -> broken.extractText(IMAGE))
                .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                        assertThat(error.getStatusCode().value()).isEqualTo(503));
    }

    @Test
    void upscalesSmallImagesBeforeOcr() {
        BufferedImage source = new BufferedImage(200, 400, BufferedImage.TYPE_INT_RGB);

        BufferedImage prepared = LocalOcrService.upscaleForOcr(source);

        assertThat(prepared.getWidth()).isEqualTo(600);
        assertThat(prepared.getHeight()).isEqualTo(1200);
    }
}
