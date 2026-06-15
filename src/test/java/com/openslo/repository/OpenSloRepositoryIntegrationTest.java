package com.openslo.repository;

import com.openslo.repository.support.OpenSloTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class OpenSloRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("openslo.security.username", () -> "openslo");
        registry.add("openslo.security.password", () -> "openslo123");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createsListsAndVersionsDocuments() {
        HttpHeaders headers = basicAuthHeaders();
        String name = "checkout-" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> service = OpenSloTestSupport.serviceDocument(name);
        ResponseEntity<Map> createResponse = restTemplate.exchange(
            "/api/documents",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("content", service), headers),
            Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).containsEntry("logicalKey", "openslo/v1/Service/" + name);

        ResponseEntity<List> listResponse = restTemplate.exchange(
            "/api/documents",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            List.class
        );
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotEmpty();

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) service.get("metadata");
        metadata.put("displayName", "Checkout v2");
        ResponseEntity<Map> updateResponse = restTemplate.exchange(
            "/api/documents/openslo~v1~Service~" + name,
            HttpMethod.PUT,
            new HttpEntity<>(Map.of("content", service), headers),
            Map.class
        );
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody()).containsEntry("version", 2);

        ResponseEntity<List> versionsResponse = restTemplate.exchange(
            "/api/documents/openslo~v1~Service~" + name + "/versions",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            List.class
        );
        assertThat(versionsResponse.getBody()).hasSize(2);
    }

    @Test
    void rejectsDuplicateCreate() {
        HttpHeaders headers = basicAuthHeaders();
        Map<String, Object> service = OpenSloTestSupport.serviceDocument("payments-" + UUID.randomUUID().toString().substring(0, 8));

        restTemplate.exchange("/api/documents", HttpMethod.POST, new HttpEntity<>(Map.of("content", service), headers), Map.class);
        ResponseEntity<Map> duplicate = restTemplate.exchange(
            "/api/documents",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("content", service), headers),
            Map.class
        );

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private HttpHeaders basicAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("openslo", "openslo123");
        return headers;
    }
}
