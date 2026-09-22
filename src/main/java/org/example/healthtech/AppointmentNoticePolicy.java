package org.example.healthtech;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AppointmentNoticePolicy {
    public enum AppointmentState { CHECK_IN_DELAYED, COMPLETED }
    public enum NotificationAction { CALL_CLINIC, LOG_ONLY }

    public record AppointmentEvent(String appointmentReference, AppointmentState state) {
        public AppointmentEvent {
            if (appointmentReference == null || appointmentReference.isBlank()) {
                throw new IllegalArgumentException("appointmentReference is required");
            }
        }
    }

    public record Notice(NotificationAction action, String level, Map<String, Object> logEntry) {}

    private final LayeredInfraiConfig config;

    public AppointmentNoticePolicy(LayeredInfraiConfig config) {
        this.config = config;
    }

    public Notice decide(AppointmentEvent event, Instant occurredAt) {
        NotificationAction action = event.state() == AppointmentState.CHECK_IN_DELAYED
                ? NotificationAction.CALL_CLINIC : NotificationAction.LOG_ONLY;
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("timestamp", occurredAt.toString());
        entry.put("level", action == NotificationAction.CALL_CLINIC ? "warn" : "info");
        entry.put("service", config.serviceName());
        entry.put("environment", config.environment());
        entry.put("message", "appointment.operational.notice");
        entry.put("appointment_reference", event.appointmentReference());
        entry.put("appointment_state", event.state().name().toLowerCase());
        entry.put("notification_action", action.name().toLowerCase());
        return new Notice(action, (String) entry.get("level"), Map.copyOf(entry));
    }
}
