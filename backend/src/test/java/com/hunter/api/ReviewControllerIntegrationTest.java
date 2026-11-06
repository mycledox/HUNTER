package com.hunter.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.UUID;

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

    private TestAccount account;

    @BeforeEach
    void createAccount() throws Exception {
        account = signupAndLogin();
    }

    @Test
    void requiresAnAuthenticatedAccountForPrivateApi() throws Exception {
        mockMvc.perform(get("/api/reviews"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsDuplicateSignupAndInvalidCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .cookie(account.csrfCookie())
                        .header("X-XSRF-TOKEN", account.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthController.SignupRequest(
                                account.email(), account.displayName(), "correct horse battery"))))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/auth/login")
                        .cookie(account.csrfCookie())
                        .header("X-XSRF-TOKEN", account.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthController.LoginRequest(
                                account.email(), "wrong password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsReviewsRecordsDecisionsAndDeletesControlledImage() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "label.png", "image/png", pngImage());

        MvcResult created = mockMvc.perform(withAccount(multipart("/api/reviews")
                        .file(image)
                        .param("productName", "Integration test chips")
                        .param("category", "packaged-snacks")
                        .param("labelText", "Potato chips. Ingredients: potato and salt. Net weight 52 g.")
                        .param("approvedFacts", "Potato chips, ingredients: potato and salt."),
                account))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEEDS_HUMAN_REVIEW"))
                .andExpect(jsonPath("$.checklistVersion").value("demo-v0.1"))
                .andExpect(jsonPath("$.ownerName").value(account.displayName()))
                .andExpect(jsonPath("$.findings.length()").value(6))
                .andReturn();

        JsonNode review = objectMapper.readTree(created.getResponse().getContentAsString());
        String id = review.get("id").asText();

        mockMvc.perform(withAccount(get("/api/reviews/{id}/image", id), account))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));

        mockMvc.perform(withAccount(post("/api/reviews/{id}/decisions", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "NEEDS_CHANGES",
                                  "reviewerName": "Untrusted client name",
                                  "reason": "Verify this label manually."
                                }
                                """), account))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEEDS_CHANGES"))
                .andExpect(jsonPath("$.decisions.length()").value(1))
                .andExpect(jsonPath("$.decisions[0].reviewerName").value(account.displayName()));

        mockMvc.perform(withAccount(get("/api/reviews/{id}", id), account))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisions.length()").value(1))
                .andExpect(jsonPath("$.findings.length()").value(6));

        mockMvc.perform(withAccount(delete("/api/reviews/{id}", id), account))
                .andExpect(status().isNoContent());

        mockMvc.perform(withAccount(get("/api/reviews/{id}", id), account))
                .andExpect(status().isNotFound());
    }

    @Test
    void isolatesReviewsAndImagesBetweenAccounts() throws Exception {
        MvcResult created = mockMvc.perform(withAccount(multipart("/api/reviews")
                        .file(new MockMultipartFile("image", "label.png", "image/png", pngImage()))
                        .param("productName", "Private chips")
                        .param("category", "packaged-snacks")
                        .param("labelText", "Potato chips"),
                account))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        TestAccount otherAccount = signupAndLogin();

        mockMvc.perform(withAccount(get("/api/reviews"), otherAccount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(withAccount(get("/api/reviews/{id}", id), otherAccount))
                .andExpect(status().isNotFound());
        mockMvc.perform(withAccount(get("/api/reviews/{id}/image", id), otherAccount))
                .andExpect(status().isNotFound());
        mockMvc.perform(withAccount(delete("/api/reviews/{id}", id), otherAccount))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresCsrfTokenForStateChangingRequests() throws Exception {
        mockMvc.perform(post("/api/auth/logout").session(account.session()))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutInvalidatesTheServerSession() throws Exception {
        mockMvc.perform(withAccount(post("/api/auth/logout"), account))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reportsLocalOcrUnavailableUntilInstalled() throws Exception {
        mockMvc.perform(get("/api/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ocr.status").value("NOT_INSTALLED"))
                .andExpect(jsonPath("$.ai").value("NEEDS_CONFIGURATION"))
                .andExpect(jsonPath("$.externalTransfer").value(false));

        mockMvc.perform(withAccount(multipart("/api/ocr")
                        .file(new MockMultipartFile("image", "label.png", "image/png", pngImage())),
                account))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(withAccount(post("/api/ai/suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "labelText": "Ingredients: rice and salt.",
                                  "approvedFacts": "",
                                  "findings": []
                                }
                                """), account))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void rejectsInvalidBarcodeBeforeCallingExternalProductLookup() throws Exception {
        mockMvc.perform(withAccount(get("/api/products/barcode/{barcode}", "012345678904"), account))
                .andExpect(status().isBadRequest());
    }

    private TestAccount signupAndLogin() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = suffix + "@example.test";
        String displayName = "Reviewer " + suffix.substring(0, 8);
        String password = "correct horse battery";
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        String csrfToken = objectMapper.readTree(csrfResult.getResponse().getContentAsString())
                .get("token").asText();

        mockMvc.perform(post("/api/auth/signup")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthController.SignupRequest(
                                email.toUpperCase(Locale.ROOT), displayName, password))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthController.LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(email))
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        MvcResult authenticatedCsrf = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();
        return new TestAccount(
                session,
                authenticatedCsrf.getResponse().getCookie("XSRF-TOKEN"),
                objectMapper.readTree(authenticatedCsrf.getResponse().getContentAsString()).get("token").asText(),
                email,
                displayName
        );
    }

    private static MockHttpServletRequestBuilder withAccount(MockHttpServletRequestBuilder request, TestAccount account) {
        return request.session(account.session())
                .cookie(account.csrfCookie())
                .header("X-XSRF-TOKEN", account.csrfToken());
    }

    private static byte[] pngImage() throws Exception {
        BufferedImage image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private record TestAccount(MockHttpSession session, Cookie csrfCookie, String csrfToken, String email,
                               String displayName) {
    }
}
