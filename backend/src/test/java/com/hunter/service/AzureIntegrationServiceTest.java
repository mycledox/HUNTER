package com.hunter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hunter.api.ApiModels;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AzureIntegrationServiceTest {

    @Test
    void draftsOptionalReviewerSuggestionsThroughConfiguredDeployment() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AzureIntegrationService azure = new AzureIntegrationService(
                builder,
                new ObjectMapper(),
                "https://openai.example.test",
                "openai-test-key",
                "reviewer-chat"
        );

        server.expect(requestTo("https://openai.example.test/openai/deployments/reviewer-chat/chat/completions"
                        + "?api-version=2024-10-21"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "openai-test-key"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"- Check the net quantity against the approved facts."}}]}
                        """, MediaType.APPLICATION_JSON));

        assertEquals("- Check the net quantity against the approved facts.",
                azure.suggestReviewNotes(new ApiModels.AiSuggestionRequest(
                        "Net quantity: 50 g", "", List.of("Quantity: verify against source")
                )));
        server.verify();
    }
}
