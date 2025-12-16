package model;

public class Doctor extends User {
    private final String branch;
    private final String clinic;
    private final String workingHours;

    public Doctor(int id, String name, String surname, String username, String tc,
                  String branch, String clinic, String workingHours) {
        super(id, name, surname, username, "DOCTOR", tc);
        this.branch = branch;
        this.clinic = clinic;
        this.workingHours = workingHours;
    }

    public String getBranch() { return branch; }
    public String getClinic() { return clinic; }
    public String getWorkingHours() { return workingHours; }

    @Override
    public String toString() {
        return getFullName() + " (" + branch + ")";
    }
}
