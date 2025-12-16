package model;

public class Patient extends User {
    public Patient(int id, String name, String surname, String username, String tc) {
        super(id, name, surname, username, "PATIENT", tc);
    }
}
