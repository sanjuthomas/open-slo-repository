package com.openslo.repository.service;

import com.openslo.repository.exception.OpenSloValidationException;
import com.openslo.repository.support.OpenSloTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenSloValidatorTest {

    private OpenSloValidator validator;

    @BeforeEach
    void setUp() {
        validator = new OpenSloValidator();
    }

    @Test
    void acceptsValidDocumentsForAllKinds() {
        assertThatCode(() -> validator.validate(OpenSloTestSupport.serviceDocument("svc"))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(OpenSloTestSupport.sloDocument("slo"))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(OpenSloTestSupport.sliDocument("sli"))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(OpenSloTestSupport.dataSourceDocument("ds"))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(OpenSloTestSupport.alertPolicyDocument("ap"))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(OpenSloTestSupport.alertConditionDocument("ac"))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(OpenSloTestSupport.alertNotificationTargetDocument("ant"))).doesNotThrowAnyException();
    }

    @Test
    void rejectsNullOrEmptyContent() {
        assertThatThrownBy(() -> validator.validate(null))
            .isInstanceOf(OpenSloValidationException.class)
            .hasMessageContaining("required");
        assertThatThrownBy(() -> validator.validate(Map.of()))
            .isInstanceOf(OpenSloValidationException.class);
    }

    @Test
    void rejectsInvalidApiVersion() {
        Map<String, Object> content = OpenSloTestSupport.serviceDocument("svc");
        content.put("apiVersion", "openslo/v2");
        assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(OpenSloValidationException.class)
            .hasMessageContaining("apiVersion");
    }

    @Test
    void rejectsInvalidKindAndMetadata() {
        Map<String, Object> content = OpenSloTestSupport.serviceDocument("svc");
        content.put("kind", "Unknown");
        assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(OpenSloValidationException.class);

        content.put("kind", "Service");
        content.remove("metadata");
        assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(OpenSloValidationException.class);

        content.put("metadata", Map.of("name", "INVALID_NAME"));
        assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(OpenSloValidationException.class);
    }

    @Test
    void rejectsSloWithoutIndicatorOrObjectives() {
        Map<String, Object> withoutIndicator = OpenSloTestSupport.sloDocument("slo");
        @SuppressWarnings("unchecked")
        Map<String, Object> specWithoutIndicator = (Map<String, Object>) withoutIndicator.get("spec");
        specWithoutIndicator.remove("indicator");
        assertThatThrownBy(() -> validator.validate(withoutIndicator))
            .isInstanceOf(OpenSloValidationException.class)
            .hasMessageContaining("indicator");

        Map<String, Object> withoutObjectives = OpenSloTestSupport.sloDocument("slo2");
        @SuppressWarnings("unchecked")
        Map<String, Object> specWithoutObjectives = (Map<String, Object>) withoutObjectives.get("spec");
        specWithoutObjectives.put("objectives", List.of());
        assertThatThrownBy(() -> validator.validate(withoutObjectives))
            .isInstanceOf(OpenSloValidationException.class)
            .hasMessageContaining("objectives");
    }

    @Test
    void rejectsInvalidBudgetingMethodAndTimeWindow() {
        Map<String, Object> invalidBudget = OpenSloTestSupport.sloDocument("slo");
        @SuppressWarnings("unchecked")
        Map<String, Object> budgetSpec = (Map<String, Object>) invalidBudget.get("spec");
        budgetSpec.put("budgetingMethod", "Invalid");
        assertThatThrownBy(() -> validator.validate(invalidBudget))
            .isInstanceOf(OpenSloValidationException.class);

        Map<String, Object> invalidWindow = OpenSloTestSupport.sloDocument("slo2");
        @SuppressWarnings("unchecked")
        Map<String, Object> windowSpec = (Map<String, Object>) invalidWindow.get("spec");
        windowSpec.put("timeWindow", List.of(Map.of("duration", "bad", "isRolling", true)));
        assertThatThrownBy(() -> validator.validate(invalidWindow))
            .isInstanceOf(OpenSloValidationException.class)
            .hasMessageContaining("duration");
    }

    @Test
    void rejectsSliWithoutExactlyOneMetricType() {
        Map<String, Object> content = OpenSloTestSupport.sliDocument("sli");
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) content.get("spec");
        spec.put("thresholdMetric", Map.of("metricSource", Map.of("type", "Prometheus", "spec", Map.of())));
        assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(OpenSloValidationException.class);
    }

    @Test
    void rejectsDataSourceWithoutConnectionDetails() {
        Map<String, Object> content = OpenSloTestSupport.dataSourceDocument("ds");
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) content.get("spec");
        spec.remove("connectionDetails");
        assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(OpenSloValidationException.class);
    }

    @Test
    void rejectsAlertPolicyWithoutTargets() {
        Map<String, Object> content = OpenSloTestSupport.alertPolicyDocument("ap");
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) content.get("spec");
        spec.remove("notificationTargets");
        assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(OpenSloValidationException.class);
    }

    @Test
    void rejectsLongDescription() {
        Map<String, Object> content = OpenSloTestSupport.serviceDocument("svc");
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) content.get("spec");
        spec.put("description", "x".repeat(1051));
        assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(OpenSloValidationException.class)
            .hasMessageContaining("1050");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1d", "4w", "30d", "1h", "1M", "1Q", "1Y"})
    void acceptsValidDurationShorthand(String duration) {
        Map<String, Object> content = OpenSloTestSupport.sloDocument("slo");
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) content.get("spec");
        spec.put("timeWindow", List.of(Map.of("duration", duration, "isRolling", true)));
        assertThatCode(() -> validator.validate(content)).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingSpec() {
        Map<String, Object> content = new HashMap<>(OpenSloTestSupport.serviceDocument("svc"));
        content.remove("spec");
        assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(OpenSloValidationException.class)
            .hasMessageContaining("spec");
    }

    @Test
    void acceptsSloWithIndicatorRef() {
        Map<String, Object> content = OpenSloTestSupport.sloDocument("slo");
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) content.get("spec");
        spec.remove("indicator");
        spec.put("indicatorRef", "external-sli");
        assertThatCode(() -> validator.validate(content)).doesNotThrowAnyException();
    }

    @Test
    void rejectsInvalidTimeWindowShape() {
        Map<String, Object> content = OpenSloTestSupport.sloDocument("slo");
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) content.get("spec");
        spec.put("timeWindow", List.of(Map.of("duration", "1d"), Map.of("duration", "2d")));
        assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(OpenSloValidationException.class)
            .hasMessageContaining("exactly one");

        spec.put("timeWindow", List.of("not-a-map"));
        assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(OpenSloValidationException.class);
    }

    @Test
    void rejectsAlertConditionWithoutConditionBody() {
        Map<String, Object> content = OpenSloTestSupport.alertConditionDocument("ac");
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) content.get("spec");
        spec.remove("condition");
        assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(OpenSloValidationException.class);
    }

    @Test
    void rejectsAlertNotificationTargetWithoutTarget() {
        Map<String, Object> content = OpenSloTestSupport.alertNotificationTargetDocument("ant");
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) content.get("spec");
        spec.remove("target");
        assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(OpenSloValidationException.class);
    }
}
