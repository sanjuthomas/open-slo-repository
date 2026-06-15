package com.openslo.repository.service;

import com.openslo.repository.config.JacksonConfig;
import com.openslo.repository.exception.OpenSloValidationException;
import com.openslo.repository.support.OpenSloTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = YamlConversionService.class)
@Import(JacksonConfig.class)
class YamlConversionServiceTest {

    @Autowired
    private YamlConversionService yamlConversionService;

    @Autowired
    @Qualifier("yamlObjectMapper")
    private com.fasterxml.jackson.databind.ObjectMapper yamlObjectMapper;

    @Test
    void parsesAndSerializesYaml() {
        String yaml = """
            apiVersion: openslo/v1
            kind: Service
            metadata:
              name: checkout
            spec:
              description: Checkout service
            """;

        Map<String, Object> parsed = yamlConversionService.parseYaml(yaml);
        assertThat(parsed.get("kind")).isEqualTo("Service");

        String roundTrip = yamlConversionService.toYaml(parsed);
        assertThat(roundTrip).contains("Service");
    }

    @Test
    void rejectsBlankYaml() {
        assertThatThrownBy(() -> yamlConversionService.parseYaml("  "))
            .isInstanceOf(OpenSloValidationException.class);
    }

    @Test
    void rejectsInvalidYaml() {
        assertThatThrownBy(() -> yamlConversionService.parseYaml("kind: [unclosed"))
            .isInstanceOf(OpenSloValidationException.class)
            .hasMessageContaining("Invalid YAML");
    }

    @Test
    void serializesDocumentFromSupport() {
        String yaml = yamlConversionService.toYaml(OpenSloTestSupport.sloDocument("latency"));
        assertThat(yaml).contains("SLO");
    }
}
