# Payment platform — OpenSLO examples
#
# Sanction scan latency SLO:
#   99.9% of sanction scans complete in under 1 minute,
#   measured over a rolling 30-day window.
#
# Apply order (references are by metadata.name):
#   1. datasource-prometheus.yaml
#   2. service-payment-platform.yaml
#   3. sli-sanction-scan-latency.yaml
#   4. alert-notification-target-oncall.yaml
#   5. alert-condition-sanction-scan-burn-rate.yaml
#   6. alert-policy-sanction-scan-latency.yaml
#   7. slo-sanction-scan-under-one-minute.yaml
#
# Load into OpenSLO Repository via the UI (one document per save)
# or POST each file's content to POST /api/documents.
