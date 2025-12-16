package dao;

import db.DatabaseManager;
import factory.UserFactory;
import model.Doctor;
import model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {

    private final Connection conn = DatabaseManager.getInstance().getConnection();

    public User login(String username, String password) {
        String sql = "SELECT id,name,surname,username,role,tc FROM users WHERE username=? AND password=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, password);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                int id = rs.getInt("id");
                String name = rs.getString("name");
                String surname = rs.getString("surname");
                String role = rs.getString("role");
                String tc = rs.getString("tc");

                if ("PATIENT".equalsIgnoreCase(role)) {
                    return UserFactory.createUser(role, id, name, surname, username, tc, null);
                } else {
                    Doctor d = getDoctorByUserId(id);
                    return UserFactory.createUser(role, id, name, surname, username, tc, d);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean registerPatient(
            String name,
            String surname,
            String tc,
            String username,
            String password,
            String contactInfo
    ) {
        try {
            String check = "SELECT COUNT(*) FROM users WHERE tc=? OR username=?";
            try (PreparedStatement cps = conn.prepareStatement(check)) {
                cps.setString(1, tc);
                cps.setString(2, username);
                ResultSet crs = cps.executeQuery();
                crs.next();
                if (crs.getInt(1) > 0) return false;
            }

            String insertUser =
                    "INSERT INTO users(name,surname,tc,username,password,role,contact_info) " +
                            "VALUES (?,?,?,?,?,'PATIENT',?)";
            try (PreparedStatement ps = conn.prepareStatement(insertUser, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setString(2, surname);
                ps.setString(3, tc);
                ps.setString(4, username);
                ps.setString(5, password);
                ps.setString(6, contactInfo);
                ps.executeUpdate();

                ResultSet keys = ps.getGeneratedKeys();
                keys.next();
                int userId = keys.getInt(1);

                try (PreparedStatement pps =
                             conn.prepareStatement("INSERT INTO patients(user_id) VALUES(?)")) {
                    pps.setInt(1, userId);
                    pps.executeUpdate();
                }
            }
            return true;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Doctor getDoctorByUserId(int userId) throws SQLException {
        String sql =
                "SELECT u.id,u.name,u.surname,u.username,u.tc," +
                        "d.branch,d.polyclinic,d.working_hours " +
                        "FROM users u JOIN doctors d ON u.id=d.user_id " +
                        "WHERE u.id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;

            return new Doctor(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("surname"),
                    rs.getString("username"),
                    rs.getString("tc"),
                    rs.getString("branch"),
                    rs.getString("polyclinic"),
                    rs.getString("working_hours")
            );
        }
    }

    public List<String> getAllBranches() {
        List<String> list = new ArrayList<>();
        String sql = "SELECT DISTINCT branch FROM doctors ORDER BY branch";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(rs.getString(1));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public List<Doctor> searchDoctors(String q) {
        List<Doctor> list = new ArrayList<>();
        String like = "%" + q + "%";
        String sql =
                "SELECT u.id,u.name,u.surname,u.username,u.tc," +
                        "d.branch,d.polyclinic,d.working_hours " +
                        "FROM users u JOIN doctors d ON u.id=d.user_id " +
                        "WHERE u.name LIKE ? OR u.surname LIKE ? OR d.branch LIKE ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Doctor(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("surname"),
                        rs.getString("username"),
                        rs.getString("tc"),
                        rs.getString("branch"),
                        rs.getString("polyclinic"),
                        rs.getString("working_hours")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public List<String[]> searchPatientsByTcOrName(String q) {
        List<String[]> list = new ArrayList<>();
        String like = "%" + q + "%";
        String sql =
                "SELECT id,tc,name,surname FROM users " +
                        "WHERE role='PATIENT' AND (tc LIKE ? OR name LIKE ? OR surname LIKE ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                        rs.getString("id"),
                        rs.getString("tc"),
                        rs.getString("name"),
                        rs.getString("surname")
                });
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public String getContactInfo(int userId) {
        try (PreparedStatement ps =
                     conn.prepareStatement("SELECT contact_info FROM users WHERE id=?")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString(1) : "";
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String getPassword(int userId) {
        try (PreparedStatement ps =
                     conn.prepareStatement("SELECT password FROM users WHERE id=?")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString(1) : "";
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateContactInfoAndPassword(int userId, String contactInfo, String newPassword) {
        String sql = "UPDATE users SET contact_info=?, password=? WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, contactInfo);
            ps.setString(2, newPassword);
            ps.setInt(3, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateDoctorWorkingHours(int doctorUserId, String hours) {
        try (PreparedStatement ps =
                     conn.prepareStatement("UPDATE doctors SET working_hours=? WHERE user_id=?")) {
            ps.setString(1, hours);
            ps.setInt(2, doctorUserId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Doctor> getDoctorsByBranch(String branch) {
        List<Doctor> list = new ArrayList<>();

        String sql =
                "SELECT u.id,u.name,u.surname,u.username,u.tc," +
                        "d.branch,d.polyclinic,d.working_hours " +
                        "FROM users u JOIN doctors d ON u.id=d.user_id " +
                        "WHERE d.branch=? ORDER BY u.name,u.surname";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, branch);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                list.add(new Doctor(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("surname"),
                        rs.getString("username"),
                        rs.getString("tc"),
                        rs.getString("branch"),
                        rs.getString("polyclinic"),
                        rs.getString("working_hours")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return list;
    }
}
