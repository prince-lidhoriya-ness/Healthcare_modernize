package com.ivirma.clinic.doctor;

import com.ivirma.clinic.common.ConflictException;
import com.ivirma.clinic.common.Specialty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {

    @Mock
    private DoctorRepository doctorRepository;

    @InjectMocks
    private DoctorService doctorService;

    private DoctorRequest request() {
        DoctorRequest r = new DoctorRequest();
        r.setFirstName(" Ana ");
        r.setLastName("Lopez");
        r.setSpecialty(Specialty.EMBRYOLOGY);
        r.setEmail("Ana.Lopez@Clinic.example.com");
        return r;
    }

    @Test
    void registerBuildsDisplayNameAndNormalisesEmail() {
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> {
            Doctor d = inv.getArgument(0);
            d.setId(9L);
            return d;
        });

        Doctor saved = doctorService.register(request());

        assertThat(saved.getName()).isEqualTo("Dr. Ana Lopez");
        assertThat(saved.getEmail()).isEqualTo("ana.lopez@clinic.example.com");
        assertThat(saved.getSpecialty()).isEqualTo(Specialty.EMBRYOLOGY);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void registerRejectsDuplicateName() {
        when(doctorRepository.existsByNameIgnoreCase("Dr. Ana Lopez")).thenReturn(true);

        assertThatThrownBy(() -> doctorService.register(request()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already registered");
        verify(doctorRepository, never()).save(any(Doctor.class));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(doctorRepository.existsByEmailIgnoreCase("ana.lopez@clinic.example.com")).thenReturn(true);

        assertThatThrownBy(() -> doctorService.register(request()))
                .isInstanceOf(ConflictException.class);
        verify(doctorRepository, never()).save(any(Doctor.class));
    }

    @Test
    void unknownDoctorIsNotBookable() {
        assertThat(doctorService.isKnown("Dr. Nobody")).isFalse();
        assertThat(doctorService.isKnown(null)).isFalse();
    }
}
