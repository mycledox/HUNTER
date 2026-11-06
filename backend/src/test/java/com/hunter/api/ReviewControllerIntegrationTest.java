package com.hunter.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:hunter-integration;DB_CLOSE_DELAY=-1",
        "hunter.storage.directory=target/test-uploads",
        "hunter.local-ocr.executable=tesseract-not-installed-for-test",
        "hunter.azure.openai.endpoint=",
        "hunter.azure.openai.key=",
        "hunter.azure.openai.deployment="
})
@AutoConfigureMockMvc
class ReviewControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsReviewsRecordsDecisionsAndDeletesControlledImage() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "label.png", "image/png", pngImage());

        MvcResult created = mockMvc.perform(multipart("/api/reviews")
                        .file(image)
                        .param("productName", "Integration test chips")
                        .param("category", "packaged-snacks")
                        .param("labelText", "Potato chips. Ingredients: potato and salt. Net weight 52 g.")
                        .param("approvedFacts", "Potato chips, ingredients: potato and salt.")
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEEDS_HUMAN_REVIEW"))
                .andExpect(jsonPath("$.checklistVersion").value("demo-v0.1"))
                .andExpect(jsonPath("$.findings.length()").value(6))
                .andReturn();

        JsonNode review = objectMapper.readTree(created.getResponse().getContentAsString());
        String id = review.get("id").asText();

        mockMvc.perform(get("/api/reviews/{id}/image", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));

        mockMvc.perform(post("/api/reviews/{id}/decisions", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "NEEDS_CHANGES",
                                  "reviewerName": "Integration reviewer",
                                  "reason": "Verify this label manually."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEEDS_CHANGES"))
                .andExpect(jsonPath("$.decisions.length()").value(1))
                .andExpect(jsonPath("$.decisions[0].reviewerName").value("Integration reviewer"));

        mockMvc.perform(get("/api/reviews/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisions.length()").value(1))
                .andExpect(jsonPath("$.findings.length()").value(6));

        mockMvc.perform(delete("/api/reviews/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/reviews/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void reportsLocalOcrUnavailableUntilInstalled() throws Exception {
        mockMvc.perform(get("/api/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ocr.status").value("NOT_INSTALLED"))
                .andExpect(jsonPath("$.ai").value("NEEDS_CONFIGURATION"))
                .andExpect(jsonPath("$.externalTransfer").value(false));

        mockMvc.perform(multipart("/api/ocr")
                        .file(new MockMultipartFile("image", "label.png", "image/png", pngImage())))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(post("/api/ai/suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "labelText": "Ingredients: rice and salt.",
                                  "approvedFacts": "",
                                  "findings": []
                                }
                                """))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void rejectsInvalidBarcodeBeforeCallingExternalProductLookup() throws Exception {
        mockMvc.perform(get("/api/products/barcode/{barcode}", "012345678904"))
                .andExpect(status().isBadRequest());
    }

    private static byte[] pngImage() throws Exception {
        BufferedImage image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
