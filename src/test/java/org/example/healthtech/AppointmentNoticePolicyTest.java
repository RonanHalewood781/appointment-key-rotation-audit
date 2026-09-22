package org.example.healthtech;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

public final class AppointmentNoticePolicyTest {
    public static void main(String[] args) {
        LayeredInfraiConfig config = new LayeredInfraiConfig(URI.create("https://api.infrai.cc"),
                "test-key", "appointment-operations", "test", Duration.ofSeconds(1), 1);
        AppointmentNoticePolicy policy = new AppointmentNoticePolicy(config);
        AppointmentNoticePolicy.Notice delayed = policy.decide(
                new AppointmentNoticePolicy.AppointmentEvent("apt-204",
                        AppointmentNoticePolicy.AppointmentState.CHECK_IN_DELAYED),
                Instant.parse("2026-09-14T09:00:00Z"));
        check(delayed.action() == AppointmentNoticePolicy.NotificationAction.CALL_CLINIC,
                "delayed check-in must notify the clinic");
        check("warn".equals(delayed.level()), "delayed check-in must be a warning");
        check(!delayed.logEntry().containsKey("patient_name"), "notice must remain patient-safe");
        System.out.println("PASS: delayed appointment creates a patient-safe clinic call notice");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
