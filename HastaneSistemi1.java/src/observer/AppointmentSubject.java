package observer;

import java.util.*;

public class AppointmentSubject {
    private static final AppointmentSubject instance = new AppointmentSubject();
    public static AppointmentSubject getInstance() { return instance; }

    private final List<AppointmentObserver> observers = new ArrayList<>();

    public void addObserver(AppointmentObserver o) {
        observers.add(o);
    }

    public void notifyObservers() {
        observers.forEach(AppointmentObserver::onAppointmentChanged);
    }
}
