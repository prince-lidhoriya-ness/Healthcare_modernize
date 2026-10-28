package com.ivirma.clinic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// Sign-in is handled by auth.AuthService, so Spring Boot's default generated user is not needed
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ClinicApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClinicApplication.class, args);
    }
}
