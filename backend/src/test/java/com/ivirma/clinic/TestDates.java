package com.ivirma.clinic;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public final class TestDates {

    private TestDates() {
    }

    /** A future clinic day (never a Sunday) at the given time. */
    public static LocalDateTime futureClinicDay(int hour, int minute) {
        LocalDate day = LocalDate.now().plusDays(2);
        if (day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            day = day.plusDays(1);
        }
        return LocalDateTime.of(day, LocalTime.of(hour, minute));
    }

    public static LocalDateTime nextSunday(int hour) {
        LocalDate day = LocalDate.now().plusDays(1);
        while (day.getDayOfWeek() != DayOfWeek.SUNDAY) {
            day = day.plusDays(1);
        }
        return LocalDateTime.of(day, LocalTime.of(hour, 0));
    }
}
