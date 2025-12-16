package strategy;

import util.WorkingHoursUtil;
import java.util.List;

public class HourlyWorkingHourStrategy implements WorkingHourStrategy {
    public List<String> generate(String workingHours) {
        return WorkingHoursUtil.generateHourlySlots(workingHours);
    }
}
