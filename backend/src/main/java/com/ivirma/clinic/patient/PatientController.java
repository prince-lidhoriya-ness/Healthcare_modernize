package com.ivirma.clinic.patient;

import com.ivirma.clinic.common.BadRequestException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

@RestController
@RequestMapping("/api/patients")
@CrossOrigin(origins = "*")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @GetMapping
    public List<Patient> list(@RequestParam(name = "search", required = false) String search) {
        return patientService.list(search);
    }

    @GetMapping("/{id}")
    public Patient get(@PathVariable("id") Long id) {
        return patientService.get(id);
    }

    @PostMapping
    public ResponseEntity<Patient> register(@Valid @RequestBody PatientRequest request) {
        // business checks
        if (isBlank(request.getFirstName()) || isBlank(request.getLastName())) {
            throw new BadRequestException("First name and last name are required");
        }
        if (request.getDateOfBirth() == null) {
            throw new BadRequestException("Date of birth is required");
        }
        if (request.getDateOfBirth().isAfter(LocalDate.now())) {
            throw new BadRequestException("Date of birth cannot be in the future");
        }
        int age = Period.between(request.getDateOfBirth(), LocalDate.now()).getYears();
        if (age < 18) {
            throw new BadRequestException("Patient must be at least 18 years old");
        }
        if (isBlank(request.getEmail()) || !request.getEmail().contains("@")) {
            throw new BadRequestException("A valid email is required");
        }
        if (request.getTreatmentInterest() == null) {
            throw new BadRequestException("Treatment interest is required");
        }
        Patient saved = patientService.register(request);
        return new ResponseEntity<Patient>(saved, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public Patient update(@PathVariable("id") Long id, @RequestBody PatientRequest request) {
        return patientService.update(id, request);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }
}
