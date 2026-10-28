package com.ivirma.clinic.dashboard;

import com.ivirma.clinic.appointment.Appointment;
import com.ivirma.clinic.appointment.AppointmentRepository;
import com.ivirma.clinic.appointment.AppointmentResponse;
import com.ivirma.clinic.common.AppointmentStatus;
import com.ivirma.clinic.common.VisitType;
import com.ivirma.clinic.patient.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private static final int NEXT_APPOINTMENTS_LIMIT = 10;

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;

    public DashboardService(PatientRepository patientRepository, AppointmentRepository appointmentRepository) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummary getSummary() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime endOfToday = startOfToday.plusDays(1);
        LocalDateTime in7Days = now.plusDays(7);

        DashboardSummary summary = new DashboardSummary();
        summary.setTotalPatients(patientRepository.count());
        summary.setNewPatientsThisWeek(patientRepository.countByCreatedAtAfter(now.minusDays(7)));

        List<Appointment> today = appointmentRepository.findByStatusAndStartTimeBetweenOrderByStartTimeAsc(
                AppointmentStatus.SCHEDULED, startOfToday, endOfToday);
        summary.setAppointmentsToday(today.size());

        List<Appointment> next7Days = appointmentRepository.findByStatusAndStartTimeBetweenOrderByStartTimeAsc(
                AppointmentStatus.SCHEDULED, now, in7Days);
        summary.setUpcomingNext7Days(next7Days.size());

        List<AppointmentResponse> next = new ArrayList<AppointmentResponse>();
        for (int i = 0; i < next7Days.size() && i < NEXT_APPOINTMENTS_LIMIT; i++) {
            next.add(AppointmentResponse.from(next7Days.get(i)));
        }
        summary.setNextAppointments(next);

        Map<String, Long> byStatus = new LinkedHashMap<String, Long>();
        for (AppointmentStatus s : AppointmentStatus.values()) {
            byStatus.put(s.name(), 0L);
        }
        for (Object[] row : appointmentRepository.countByStatus()) {
            byStatus.put(((AppointmentStatus) row[0]).name(), (Long) row[1]);
        }
        summary.setAppointmentsByStatus(byStatus);

        Map<String, Long> byVisitType = new LinkedHashMap<String, Long>();
        for (VisitType vt : VisitType.values()) {
            byVisitType.put(vt.getLabel(), 0L);
        }
        for (Object[] row : appointmentRepository.countByVisitType()) {
            byVisitType.put(((VisitType) row[0]).getLabel(), (Long) row[1]);
        }
        summary.setAppointmentsByVisitType(byVisitType);

        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        summary.setGeneratedAt(format.format(new Date()));
        return summary;
    }
}
