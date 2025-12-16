package factory;

import model.*;

public class UserFactory {
    public static User createUser(String role, int id, String name,
                                  String surname, String username, String tc, Doctor doctor) {
        if ("PATIENT".equalsIgnoreCase(role))
            return new Patient(id, name, surname, username, tc);

        if ("DOCTOR".equalsIgnoreCase(role))
            return doctor != null ? doctor :
                    new Doctor(id, name, surname, username, tc, "", "", "");

        throw new IllegalArgumentException();
    }
}
