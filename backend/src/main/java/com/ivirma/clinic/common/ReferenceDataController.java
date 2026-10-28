package com.ivirma.clinic.common;

import com.ivirma.clinic.doctor.DoctorService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lists used by the frontend dropdowns.
 */
@RestController
@CrossOrigin(origins = "*")
public class ReferenceDataController {

    private final DoctorService doctorService;

    public ReferenceDataController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @GetMapping("/api/reference-data")
    public Map<String, Object> getReferenceData() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();

        List<Map<String, Object>> visitTypes = new ArrayList<Map<String, Object>>();
        for (VisitType vt : VisitType.values()) {
            Map<String, Object> item = new HashMap<String, Object>();
            item.put("code", vt.name());
            item.put("label", vt.getLabel());
            item.put("durationMinutes", vt.getDurationMinutes());
            visitTypes.add(item);
        }
        result.put("visitTypes", visitTypes);

        List<Map<String, Object>> interests = new ArrayList<Map<String, Object>>();
        for (TreatmentInterest ti : TreatmentInterest.values()) {
            Map<String, Object> item = new HashMap<String, Object>();
            item.put("code", ti.name());
            item.put("label", ti.getLabel());
            interests.add(item);
        }
        result.put("treatmentInterests", interests);

        List<String> statuses = new ArrayList<String>();
        for (AppointmentStatus s : AppointmentStatus.values()) {
            statuses.add(s.name());
        }
        result.put("appointmentStatuses", statuses);
        result.put("clinicians", doctorService.names());

        List<Map<String, Object>> specialties = new ArrayList<Map<String, Object>>();
        for (Specialty sp : Specialty.values()) {
            Map<String, Object> item = new HashMap<String, Object>();
            item.put("code", sp.name());
            item.put("label", sp.getLabel());
            specialties.add(item);
        }
        result.put("specialties", specialties);
        return result;
    }
}
