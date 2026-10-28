package com.ivirma.clinic.doctor;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin portal: the clinic's doctors. */
@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final DoctorService doctorService;

    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @GetMapping
    public List<Doctor> list() {
        return doctorService.list();
    }

    @PostMapping
    public ResponseEntity<Doctor> register(@Valid @RequestBody DoctorRequest request) {
        return new ResponseEntity<Doctor>(doctorService.register(request), HttpStatus.CREATED);
    }
}
