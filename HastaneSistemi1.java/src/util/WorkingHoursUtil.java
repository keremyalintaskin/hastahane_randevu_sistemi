package util;

import java.time.*;
import java.util.*;

public class WorkingHoursUtil {
    public static List<String> generateHourlySlots(String workingHours) {
        List<String> out = new ArrayList<>();
        if (workingHours == null || workingHours.isBlank()) return out;

        for (String p : workingHours.split(",")) {
            String[] lr = p.trim().split("-");
            LocalTime t = LocalTime.parse(lr[0]);
            LocalTime end = LocalTime.parse(lr[1]);
            while (t.isBefore(end)) {
                out.add(t.toString().substring(0,5));
                t = t.plusHours(1);
            }
        }
        return out;
    }

    public static LocalDate startOfWeek(LocalDate d) {
        return d.minusDays(d.getDayOfWeek().getValue() - 1);
    }

    public static LocalDate endOfWeek(LocalDate d) {
        return startOfWeek(d).plusDays(6);
    }
}
