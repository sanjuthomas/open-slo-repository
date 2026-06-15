package com.openslo.repository.service;

import com.openslo.repository.exception.OpenSloValidationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class OpenSloValidator {

    private static final Set<String> ALLOWED_KINDS = Set.of(
        "DataSource", "SLO", "SLI", "AlertPolicy", "AlertCondition",
        "AlertNotificationTarget", "Service"
    );

    private static final Set<String> BUDGETING_METHODS = Set.of(
        "Occurrences", "Timeslices", "RatioTimeslices"
    );

    private static final Pattern RFC1123_NAME = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$");
    private static final Pattern DURATION_SHORTHAND = Pattern.compile("^\\d+[mhdwMQY]$");

    @SuppressWarnings("unchecked")
    public void validate(Map<String, Object> content) {
        if (content == null || content.isEmpty()) {
            throw new OpenSloValidationException("OpenSLO document content is required");
        }

        String apiVersion = requireString(content, "apiVersion");
        if (!"openslo/v1".equals(apiVersion)) {
            throw new OpenSloValidationException("apiVersion must be 'openslo/v1'");
        }

        String kind = requireString(content, "kind");
        if (!ALLOWED_KINDS.contains(kind)) {
            throw new OpenSloValidationException(
                "kind must be one of: " + String.join(", ", ALLOWED_KINDS));
        }

        Object metadataObj = content.get("metadata");
        if (!(metadataObj instanceof Map<?, ?> metadata)) {
            throw new OpenSloValidationException("metadata is required");
        }

        String name = requireString(metadata, "name");
        if (!RFC1123_NAME.matcher(name).matches()) {
            throw new OpenSloValidationException(
                "metadata.name must be RFC1123 compliant (lowercase alphanumeric or '-', max 63 chars)");
        }

        Object specObj = content.get("spec");
        if (!(specObj instanceof Map<?, ?>)) {
            throw new OpenSloValidationException("spec is required");
        }

        Map<String, Object> spec = (Map<String, Object>) specObj;
        validateKindSpecific(kind, spec);
    }

    private void validateKindSpecific(String kind, Map<String, Object> spec) {
        switch (kind) {
            case "SLO" -> validateSlo(spec);
            case "SLI" -> validateSli(spec);
            case "DataSource" -> validateDataSource(spec);
            case "AlertPolicy" -> validateAlertPolicy(spec);
            case "AlertCondition" -> validateAlertCondition(spec);
            case "AlertNotificationTarget" -> validateAlertNotificationTarget(spec);
            case "Service" -> validateDescription(spec);
            default -> throw new OpenSloValidationException("Unsupported kind: " + kind);
        }
    }

    private void validateSlo(Map<String, Object> spec) {
        validateDescription(spec);

        if (!spec.containsKey("service") || spec.get("service") == null) {
            throw new OpenSloValidationException("SLO spec.service is required");
        }

        boolean hasIndicator = spec.get("indicator") != null;
        boolean hasIndicatorRef = spec.get("indicatorRef") != null;
        if (!hasIndicator && !hasIndicatorRef) {
            throw new OpenSloValidationException("SLO requires either spec.indicator or spec.indicatorRef");
        }

        String budgetingMethod = requireString(spec, "budgetingMethod");
        if (!BUDGETING_METHODS.contains(budgetingMethod)) {
            throw new OpenSloValidationException(
                "budgetingMethod must be one of: Occurrences, Timeslices, RatioTimeslices");
        }

        Object objectives = spec.get("objectives");
        if (!(objectives instanceof List<?> list) || list.isEmpty()) {
            throw new OpenSloValidationException("SLO spec.objectives must contain at least one objective");
        }

        if (spec.containsKey("timeWindow")) {
            validateTimeWindow(spec.get("timeWindow"));
        }
    }

    private void validateSli(Map<String, Object> spec) {
        validateDescription(spec);
        boolean hasThreshold = spec.get("thresholdMetric") != null;
        boolean hasRatio = spec.get("ratioMetric") != null;
        if (hasThreshold == hasRatio) {
            throw new OpenSloValidationException("SLI requires exactly one of thresholdMetric or ratioMetric");
        }
    }

    private void validateDataSource(Map<String, Object> spec) {
        validateDescription(spec);
        requireString(spec, "type");
        if (spec.get("connectionDetails") == null) {
            throw new OpenSloValidationException("DataSource spec.connectionDetails is required");
        }
    }

    private void validateAlertPolicy(Map<String, Object> spec) {
        validateDescription(spec);
        Object conditions = spec.get("conditions");
        if (!(conditions instanceof List<?> list) || list.isEmpty()) {
            throw new OpenSloValidationException("AlertPolicy spec.conditions is required");
        }
        Object targets = spec.get("notificationTargets");
        if (!(targets instanceof List<?> targetList) || targetList.isEmpty()) {
            throw new OpenSloValidationException("AlertPolicy spec.notificationTargets is required");
        }
    }

    private void validateAlertCondition(Map<String, Object> spec) {
        validateDescription(spec);
        requireString(spec, "severity");
        if (!(spec.get("condition") instanceof Map<?, ?>)) {
            throw new OpenSloValidationException("AlertCondition spec.condition is required");
        }
    }

    private void validateAlertNotificationTarget(Map<String, Object> spec) {
        validateDescription(spec);
        requireString(spec, "target");
    }

    private void validateTimeWindow(Object timeWindowObj) {
        if (!(timeWindowObj instanceof List<?> windows) || windows.size() != 1) {
            throw new OpenSloValidationException("timeWindow must contain exactly one entry");
        }
        if (!(windows.get(0) instanceof Map<?, ?> window)) {
            throw new OpenSloValidationException("timeWindow entry must be an object");
        }
        String duration = requireString(window, "duration");
        if (!DURATION_SHORTHAND.matcher(duration).matches()) {
            throw new OpenSloValidationException("timeWindow duration must use OpenSLO duration shorthand (e.g. 28d, 4w)");
        }
    }

    private void validateDescription(Map<String, Object> spec) {
        Object description = spec.get("description");
        if (description instanceof String desc && desc.length() > 1050) {
            throw new OpenSloValidationException("description must be at most 1050 characters");
        }
    }

    private String requireString(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String str) || str.isBlank()) {
            throw new OpenSloValidationException(key + " is required");
        }
        return str;
    }
}
