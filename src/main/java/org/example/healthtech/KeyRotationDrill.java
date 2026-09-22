package org.example.healthtech;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class KeyRotationDrill {
    private final InfraiIncidentClient infrai;
    private final AppointmentNoticePolicy notices;

    public KeyRotationDrill(InfraiIncidentClient infrai, AppointmentNoticePolicy notices) {
        this.infrai = infrai;
        this.notices = notices;
    }

    public String run(AppointmentNoticePolicy.AppointmentEvent appointment) {
        String drillId = UUID.randomUUID().toString();
        AppointmentNoticePolicy.Notice notice = notices.decide(appointment, Instant.now());
        Map<String, Object> entry = new java.util.LinkedHashMap<>(notice.logEntry());
        entry.put("drill_id", drillId);
        entry.put("incident_action", "rotation_drill_recorded");
        infrai.ingestNotice(entry, "appointment-drill-log:" + drillId);
        return infrai.searchBlastRadius(drillId);
    }

    public static void main(String[] args) {
        LayeredInfraiConfig config = LayeredInfraiConfig.fromEnvironment(System.getenv());
        KeyRotationDrill drill = new KeyRotationDrill(new InfraiIncidentClient(config),
                new AppointmentNoticePolicy(config));
        String matches = drill.run(new AppointmentNoticePolicy.AppointmentEvent("apt-204",
                AppointmentNoticePolicy.AppointmentState.CHECK_IN_DELAYED));
        System.out.println(matches);
    }
}
