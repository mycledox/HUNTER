package com.hunter.service;

import com.hunter.api.ApiModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class LocalOcrService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalOcrService.class);
    private static final Pattern LANGUAGES = Pattern.compile("[A-Za-z0-9_+.-]{1,128}");
    private static final long OCR_TIMEOUT_SECONDS = 45;
    private static final long PROBE_TIMEOUT_SECONDS = 5;
    private static final long MAX_OUTPUT_BYTES = 1_000_000;
    private static final long MAX_OCR_PIXELS = 20_000_000L;
    private static final double MAX_UPSCALE_FACTOR = 3.0;
    private final String executable;
    private final String languages;

    public LocalOcrService(
            @Value("${hunter.local-ocr.executable:tesseract}") String executable,
            @Value("${hunter.local-ocr.languages:eng}") String languages
    ) {
        this.executable = executable.strip();
        this.languages = languages.strip();
    }

    public ApiModels.OcrCapability capability() {
        if (isAvailable()) {
            return new ApiModels.OcrCapability("AVAILABLE",
                    "Tesseract OCR is installed and processes package images locally on this computer.");
        }
        return new ApiModels.OcrCapability("NOT_INSTALLED",
                "Install Tesseract OCR and restart the API to enable local text extraction. You can also enter label text manually.");
    }

    /**
     * Exposed for tests so a fake executable can drive the extraction flow
     * directly without passing the availability probe first.
     */
    public String extractTextSkippingProbe(UploadStorageService.ValidatedImage image) {
        return runExtraction(image);
    }

    public String extractText(UploadStorageService.ValidatedImage image) {
        if (!isAvailable()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Tesseract OCR is not installed or cannot be started. Install Tesseract locally, restart the API, or enter label text manually.");
        }
        return runExtraction(image);
    }

    private String runExtraction(UploadStorageService.ValidatedImage image) {
        Path input = null;
        Path output = null;
        Path errorOutput = null;
        Process process = null;
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(image.bytes()));
            if (source == null) {
                throw new IOException("Could not decode image for OCR.");
            }
            BufferedImage prepared = upscaleForOcr(source);
            boolean upscaled = prepared != source;
            input = Files.createTempFile("hunter-ocr-",
                    upscaled ? ".png" : UploadStorageService.extensionFor(image.mediaType()));
            output = Files.createTempFile("hunter-ocr-result-", ".txt");
            errorOutput = Files.createTempFile("hunter-ocr-error-", ".txt");
            if (upscaled) {
                if (!ImageIO.write(prepared, "png", input.toFile())) {
                    throw new IOException("Could not prepare image for OCR.");
                }
            } else {
                Files.write(input, image.bytes());
            }

            process = new ProcessBuilder(executable, input.toString(), "stdout",
                    "-l", languages, "--psm", "6")
                    .redirectOutput(output.toFile())
                    .redirectError(errorOutput.toFile())
                    .start();
            if (!process.waitFor(OCR_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                        "Local OCR took too long. Try a smaller, clearer crop of the label.");
            }
            if (process.exitValue() != 0) {
                String detail = readError(errorOutput);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        detail.isBlank()
                                ? "Tesseract could not read this image. Check that the selected language data is installed."
                                : "Tesseract could not read this image: " + detail);
            }
            if (Files.size(output) > MAX_OUTPUT_BYTES) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "OCR produced too much text. Crop the image to the label section and try again.");
            }

            String text = Files.readString(output, StandardCharsets.UTF_8).strip();
            if (text.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Tesseract did not detect readable text. Try a clearer image or enter the label text manually.");
            }
            if (text.length() > 30_000) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "OCR produced too much text. Crop the image to the label section and try again.");
            }
            return text;
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not run local Tesseract OCR. Check the installation or set HUNTER_TESSERACT_PATH to the executable.", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Local OCR was interrupted. Try again or enter the label text manually.", error);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            deleteTemporaryFile(input);
            deleteTemporaryFile(output);
            deleteTemporaryFile(errorOutput);
        }
    }

    static BufferedImage upscaleForOcr(BufferedImage source) {
        long sourcePixels = (long) source.getWidth() * source.getHeight();
        double scale = Math.min(MAX_UPSCALE_FACTOR,
                Math.sqrt((double) MAX_OCR_PIXELS / sourcePixels));
        if (scale <= 1.0) {
            return source;
        }

        int width = Math.max(source.getWidth(), (int) (source.getWidth() * scale));
        int height = Math.max(source.getHeight(), (int) (source.getHeight() * scale));
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private boolean isAvailable() {
        if (executable.isBlank() || !LANGUAGES.matcher(languages).matches()) {
            return false;
        }
        Path versionOutput = null;
        Process process = null;
        try {
            versionOutput = Files.createTempFile("hunter-ocr-version-", ".txt");
            process = new ProcessBuilder(executable, "--version")
                    .redirectErrorStream(true)
                    .redirectOutput(versionOutput.toFile())
                    .start();
            if (!process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException error) {
            return false;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            deleteTemporaryFile(versionOutput);
        }
    }

    private static String readError(Path path) throws IOException {
        if (path == null || Files.size(path) == 0) {
            return "";
        }
        String detail = Files.readString(path, StandardCharsets.UTF_8).strip();
        return detail.length() > 500 ? detail.substring(0, 500) : detail;
    }

    private static void deleteTemporaryFile(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException error) {
                LOGGER.warn("Could not remove temporary OCR file {}: {}", path, error.getMessage());
                path.toFile().deleteOnExit();
            }
        }
    }
}
