package com.ivirma.clinic.appointment;

import com.ivirma.clinic.common.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByClinicianAndStatusNotAndStartTimeBetween(
            String clinician, AppointmentStatus excludedStatus, LocalDateTime from, LocalDateTime to);

    List<Appointment> findByPatientIdAndStatusNotAndStartTimeBetween(
            Long patientId, AppointmentStatus excludedStatus, LocalDateTime from, LocalDateTime to);

    List<Appointment> findByStatusAndStartTimeGreaterThanEqualOrderByStartTimeAsc(
            AppointmentStatus status, LocalDateTime from);

    List<Appointment> findByStatusAndStartTimeBetweenOrderByStartTimeAsc(
            AppointmentStatus status, LocalDateTime from, LocalDateTime to);

    List<Appointment> findAllByOrderByStartTimeDesc();

    List<Appointment> findByPatientIdOrderByStartTimeDesc(Long patientId);

    @Query("select a.status, count(a) from Appointment a group by a.status")
    List<Object[]> countByStatus();

    @Query("select a.visitType, count(a) from Appointment a group by a.visitType")
    List<Object[]> countByVisitType();
}
