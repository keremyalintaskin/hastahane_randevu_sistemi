package dao;

import db.DatabaseManager;
import observer.AppointmentSubject;
import state.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class AppointmentDAO {

    private final Connection conn = DatabaseManager.getInstance().getConnection();
    private final AppointmentSubject subject = AppointmentSubject.getInstance();

    public boolean isSlotTaken(int doctorId, LocalDate date, String hhmm) {
        String sql = """
            SELECT COUNT(*) FROM appointments
            WHERE doctor_id=? AND date=? AND time=? AND state='AKTIF'
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            ps.setDate(2, Date.valueOf(date));
            ps.setTime(3, Time.valueOf(LocalTime.parse(hhmm)));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean hasPatientAppointmentSameDay(int patientId, LocalDate date) {
        String sql = """
            SELECT COUNT(*) FROM appointments
            WHERE patient_id=? AND date=? AND state='AKTIF'
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            ps.setDate(2, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void create(int patientId, int doctorId, LocalDate date, String hhmm) {

        if (hasPatientAppointmentSameDay(patientId, date))
            throw new RuntimeException("Hasta aynı gün birden fazla randevu alamaz");

        if (isSlotTaken(doctorId, date, hhmm))
            throw new RuntimeException("Bu saat dolu");

        AppointmentState state = new AktifState();

        String sql = """
            INSERT INTO appointments
            (patient_id,doctor_id,date,time,state,note,prescription)
            VALUES (?,?,?,?,?,?,?)
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            ps.setInt(2, doctorId);
            ps.setDate(3, Date.valueOf(date));
            ps.setTime(4, Time.valueOf(LocalTime.parse(hhmm)));
            ps.setString(5, state.getStateName());
            ps.setString(6, null);
            ps.setString(7, null);
            ps.executeUpdate();
            subject.notifyObservers();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void cancelByPatient(int appointmentId, int patientId) {
        AppointmentState state = new IptalState();
        String sql = """
            UPDATE appointments
            SET state=?
            WHERE id=? AND patient_id=? AND state='AKTIF'
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, state.getStateName());
            ps.setInt(2, appointmentId);
            ps.setInt(3, patientId);
            ps.executeUpdate();
            subject.notifyObservers();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void rescheduleByPatient(
            int appointmentId, int patientId, int doctorId,
            LocalDate newDate, String newHhmm) {

        if (hasPatientAppointmentSameDay(patientId, newDate))
            throw new RuntimeException("Hasta aynı gün başka randevuya sahip");

        if (isSlotTaken(doctorId, newDate, newHhmm))
            throw new RuntimeException("Yeni saat dolu");

        String sql = """
            UPDATE appointments
            SET date=?, time=?
            WHERE id=? AND patient_id=? AND doctor_id=? AND state='AKTIF'
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(newDate));
            ps.setTime(2, Time.valueOf(LocalTime.parse(newHhmm)));
            ps.setInt(3, appointmentId);
            ps.setInt(4, patientId);
            ps.setInt(5, doctorId);
            ps.executeUpdate();
            subject.notifyObservers();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateStateByDoctor(int appointmentId, int doctorId, AppointmentState newState) {
        String sql = """
            UPDATE appointments SET state=?
            WHERE id=? AND doctor_id=?
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newState.getStateName());
            ps.setInt(2, appointmentId);
            ps.setInt(3, doctorId);
            ps.executeUpdate();
            subject.notifyObservers();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveExam(int appointmentId, int doctorId, String note, String prescription) {
        String sql = """
            UPDATE appointments
            SET note=?, prescription=?
            WHERE id=? AND doctor_id=?
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, note);
            ps.setString(2, prescription);
            ps.setInt(3, appointmentId);
            ps.setInt(4, doctorId);
            ps.executeUpdate();
            subject.notifyObservers();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String[] getExam(int appointmentId, int doctorId) {
        String sql = """
            SELECT note,prescription FROM appointments
            WHERE id=? AND doctor_id=?
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, appointmentId);
            ps.setInt(2, doctorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new String[]{"", ""};
                return new String[]{ rs.getString("note"), rs.getString("prescription") };
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String[]> getByPatient(int patientId) {
        List<String[]> list = new ArrayList<>();
        String sql = """
            SELECT a.id,
                   CONCAT(u.name,' ',u.surname),
                   d.branch,
                   a.date,
                   TIME_FORMAT(a.time,'%H:%i'),
                   a.state
            FROM appointments a
            JOIN users u ON a.doctor_id=u.id
            JOIN doctors d ON u.id=d.user_id
            WHERE a.patient_id=?
            ORDER BY a.date DESC, a.time DESC
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new String[]{
                            rs.getString(1),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getString(4),
                            rs.getString(5),
                            rs.getString(6)
                    });
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public List<String[]> getByDoctorBetween(int doctorId, LocalDate from, LocalDate to) {
        List<String[]> list = new ArrayList<>();
        String sql = """
            SELECT a.id,u.tc,CONCAT(u.name,' ',u.surname),
                   a.date,TIME_FORMAT(a.time,'%H:%i'),a.state
            FROM appointments a
            JOIN users u ON a.patient_id=u.id
            WHERE a.doctor_id=? AND a.date BETWEEN ? AND ?
            ORDER BY a.date,a.time
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            ps.setDate(2, Date.valueOf(from));
            ps.setDate(3, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new String[]{
                            rs.getString(1),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getString(4),
                            rs.getString(5),
                            rs.getString(6)
                    });
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public List<String[]> getByPatientBetween(int patientId, LocalDate from, LocalDate to) {
        List<String[]> list = new ArrayList<>();
        String sql = """
            SELECT a.id,
                   CONCAT(u.name,' ',u.surname),
                   d.branch,
                   a.date,
                   TIME_FORMAT(a.time,'%H:%i'),
                   a.state
            FROM appointments a
            JOIN users u ON a.doctor_id=u.id
            JOIN doctors d ON u.id=d.user_id
            WHERE a.patient_id=? AND a.date BETWEEN ? AND ?
            ORDER BY a.date,a.time
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            ps.setDate(2, Date.valueOf(from));
            ps.setDate(3, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new String[]{
                            rs.getString(1),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getString(4),
                            rs.getString(5),
                            rs.getString(6)
                    });
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }
}
