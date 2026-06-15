package com.openslo.repository.controller;

import com.openslo.repository.dto.OpenSloDetailDto;
import com.openslo.repository.dto.OpenSloSummaryDto;
import com.openslo.repository.exception.DuplicateOpenSloException;
import com.openslo.repository.config.SecurityConfig;
import com.openslo.repository.exception.GlobalExceptionHandler;
import com.openslo.repository.exception.OpenSloNotFoundException;
import com.openslo.repository.exception.OpenSloValidationException;
import com.openslo.repository.service.OpenSloService;
import com.openslo.repository.service.YamlConversionService;
import com.openslo.repository.support.OpenSloTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OpenSloController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = {
    "openslo.security.username=test",
    "openslo.security.password=test"
})
class OpenSloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OpenSloService openSloService;

    @MockitoBean
    private YamlConversionService yamlConversionService;

    @Test
    void listsActiveDocuments() throws Exception {
        when(openSloService.listActive()).thenReturn(List.of(
            new OpenSloSummaryDto("id", "openslo/v1/Service/svc", "openslo/v1", "Service", "svc", "Svc", 1, false,
                Instant.parse("2026-01-01T00:00:00Z"), "openslo")
        ));

        mockMvc.perform(get("/api/documents").with(httpBasic("test", "test")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("svc"));
    }

    @Test
    void createsDocument() throws Exception {
        Map<String, Object> content = OpenSloTestSupport.serviceDocument("checkout");
        when(openSloService.create(any())).thenReturn(detail("openslo/v1/Service/checkout", content));

        mockMvc.perform(post("/api/documents").with(httpBasic("test", "test"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":{"apiVersion":"openslo/v1","kind":"Service","metadata":{"name":"checkout"},"spec":{}}}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.logicalKey").value("openslo/v1/Service/checkout"));
    }

    @Test
    void updatesDocumentWithEncodedLogicalKey() throws Exception {
        Map<String, Object> content = OpenSloTestSupport.serviceDocument("checkout");
        when(openSloService.update(eq("openslo/v1/Service/checkout"), any())).thenReturn(detail("openslo/v1/Service/checkout", content));

        mockMvc.perform(put("/api/documents/openslo~v1~Service~checkout").with(httpBasic("test", "test"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":{"apiVersion":"openslo/v1","kind":"Service","metadata":{"name":"checkout"},"spec":{}}}
                    """))
            .andExpect(status().isOk());
    }

    @Test
    void validatesDocument() throws Exception {
        Map<String, Object> content = OpenSloTestSupport.serviceDocument("checkout");
        when(openSloService.buildLogicalKey(any())).thenReturn("openslo/v1/Service/checkout");

        mockMvc.perform(post("/api/documents/validate").with(httpBasic("test", "test"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":{"apiVersion":"openslo/v1","kind":"Service","metadata":{"name":"checkout"},"spec":{}}}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void parsesYaml() throws Exception {
        when(yamlConversionService.parseYaml(any())).thenReturn(OpenSloTestSupport.serviceDocument("checkout"));

        mockMvc.perform(post("/api/documents/parse-yaml").with(httpBasic("test", "test"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"yaml\":\"kind: Service\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kind").value("Service"));
    }

    @Test
    void returnsConflictForDuplicate() throws Exception {
        when(openSloService.create(any())).thenThrow(new DuplicateOpenSloException("openslo/v1/Service/checkout"));

        mockMvc.perform(post("/api/documents").with(httpBasic("test", "test"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":{"apiVersion":"openslo/v1","kind":"Service","metadata":{"name":"checkout"},"spec":{}}}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void returnsNotFound() throws Exception {
        when(openSloService.getActiveByLogicalKey("missing")).thenThrow(new OpenSloNotFoundException("missing"));

        mockMvc.perform(get("/api/documents/missing").with(httpBasic("test", "test")))
            .andExpect(status().isNotFound());
    }

    @Test
    void returnsBadRequestForValidationFailure() throws Exception {
        when(openSloService.create(any())).thenThrow(new OpenSloValidationException("invalid"));

        mockMvc.perform(post("/api/documents").with(httpBasic("test", "test"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":{"apiVersion":"openslo/v1","kind":"Service","metadata":{"name":"checkout"},"spec":{}}}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/documents"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getsDocumentById() throws Exception {
        Map<String, Object> content = OpenSloTestSupport.serviceDocument("checkout");
        when(openSloService.getById("mongo-id")).thenReturn(detail("openslo/v1/Service/checkout", content));

        mockMvc.perform(get("/api/documents/id/mongo-id").with(httpBasic("test", "test")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("id"));
    }

    @Test
    void checksExistence() throws Exception {
        when(openSloService.existsActive("openslo/v1/Service/checkout")).thenReturn(true);

        mockMvc.perform(get("/api/documents/exists/openslo~v1~Service~checkout").with(httpBasic("test", "test")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.exists").value(true));
    }

    @Test
    void serializesToYaml() throws Exception {
        when(yamlConversionService.toYaml(any())).thenReturn("kind: Service");

        mockMvc.perform(post("/api/documents/to-yaml").with(httpBasic("test", "test"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":{"apiVersion":"openslo/v1","kind":"Service","metadata":{"name":"checkout"},"spec":{}}}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.yaml").value("kind: Service"));
    }

    @Test
    void listsVersions() throws Exception {
        when(openSloService.listVersions("openslo/v1/Service/checkout")).thenReturn(List.of(
            new OpenSloSummaryDto("id", "openslo/v1/Service/checkout", "openslo/v1", "Service", "checkout", null, 2, false,
                Instant.parse("2026-01-01T00:00:00Z"), "openslo")
        ));

        mockMvc.perform(get("/api/documents/openslo~v1~Service~checkout/versions").with(httpBasic("test", "test")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].version").value(2));
    }

    private OpenSloDetailDto detail(String logicalKey, Map<String, Object> content) {
        return new OpenSloDetailDto("id", logicalKey, 1, false, content,
            Instant.parse("2026-01-01T00:00:00Z"), "openslo");
    }
}
