import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.sql.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

// =======================================================
//  MODELS
// =======================================================

abstract class User {
    protected final int id;
    protected final String name;
    protected final String surname;
    protected final String username;
    protected final String role; // PATIENT / DOCTOR
    protected final String tc;

    protected User(int id, String name, String surname, String username, String role, String tc) {
        this.id = id;
        this.name = name;
        this.surname = surname;
        this.username = username;
        this.role = role;
        this.tc = tc;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getSurname() { return surname; }
    public String getFullName() { return (name == null ? "" : name) + " " + (surname == null ? "" : surname); }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public String getTc() { return tc; }
}

class Patient extends User {
    public Patient(int id, String name, String surname, String username, String tc) {
        super(id, name, surname, username, "PATIENT", tc);
    }
}

class Doctor extends User {
    private final String branch;
    private final String clinic;
    private final String workingHours; // örn: "09:00-12:00,13:00-17:00"

    public Doctor(int id, String name, String surname, String username, String tc, String branch, String clinic, String workingHours) {
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

// =======================================================
//  DB SINGLETON
// =======================================================

class DatabaseManager {
    private static DatabaseManager instance;
    private final Connection conn;

    private static final String URL = "jdbc:mysql://localhost:3306/hospital_randevu?useSSL=false&serverTimezone=UTC";
    private static final String USER = "root";
    private static final String PASS = "1234";

    private DatabaseManager() {
        try {
            conn = DriverManager.getConnection(URL, USER, PASS);
            System.out.println("MySQL bağlantısı OK");
        } catch (SQLException e) {
            throw new RuntimeException("MySQL bağlantısı kurulamadı: " + e.getMessage(), e);
        }
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    public Connection getConnection() { return conn; }
}

// =======================================================
//  UTILS
// =======================================================

class Ui {
    static void err(JFrame parent, String msg) { JOptionPane.showMessageDialog(parent, msg, "Hata", JOptionPane.ERROR_MESSAGE); }
    static void info(JFrame parent, String msg) { JOptionPane.showMessageDialog(parent, msg, "Bilgi", JOptionPane.INFORMATION_MESSAGE); }
}

class WorkingHoursUtil {
    public static List<String> generateHourlySlots(String workingHours) {
        List<String> out = new ArrayList<>();
        if (workingHours == null || workingHours.trim().isEmpty()) return out;

        String[] parts = workingHours.split(",");
        for (String p : parts) {
            String seg = p.trim();
            if (!seg.contains("-")) continue;
            String[] lr = seg.split("-");
            if (lr.length != 2) continue;

            LocalTime start = LocalTime.parse(lr[0].trim());
            LocalTime end = LocalTime.parse(lr[1].trim());

            LocalTime t = start;
            while (t.isBefore(end)) {
                out.add(t.toString().substring(0,5));
                t = t.plusHours(1);
            }
        }
        return out;
    }

    public static LocalDate startOfWeek(LocalDate d) {
        int diff = d.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue();
        return d.minusDays(diff);
    }

    public static LocalDate endOfWeek(LocalDate d) {
        return startOfWeek(d).plusDays(6);
    }
}

// =======================================================
//  STATE PATTERN
// =======================================================

abstract class AppointmentState {
    public abstract String getStateName();
}

class AktifState extends AppointmentState {
    public String getStateName() { return "AKTIF"; }
}

class IptalState extends AppointmentState {
    public String getStateName() { return "IPTAL"; }
}

class TamamlandiState extends AppointmentState {
    public String getStateName() { return "TAMAMLANDI"; }
}

class GelmediState extends AppointmentState {
    public String getStateName() { return "GELMEDI"; }
}

// =======================================================
//  OBSERVER PATTERN
// =======================================================

interface AppointmentObserver {
    void onAppointmentChanged();
}

class AppointmentSubject {

    private static final AppointmentSubject instance = new AppointmentSubject();
    public static AppointmentSubject getInstance() { return instance; }

    private final List<AppointmentObserver> observers = new ArrayList<>();

    public void addObserver(AppointmentObserver o) {
        observers.add(o);
    }

    public void notifyObservers() {
        for (AppointmentObserver o : observers) {
            o.onAppointmentChanged();
        }
    }
}

// =======================================================
//  STRATEGY PATTERN
// =======================================================

interface WorkingHourStrategy {
    List<String> generate(String workingHours);
}

class HourlyWorkingHourStrategy implements WorkingHourStrategy {
    @Override
    public List<String> generate(String workingHours) {
        return WorkingHoursUtil.generateHourlySlots(workingHours);
    }
}

// =======================================================
//  TEMPLATE METHOD PATTERN
// =======================================================

abstract class AbstractViewTemplate {
    public final void render() {
        loadData();
        buildUI();
    }
    protected abstract void loadData();
    protected abstract void buildUI();
}

// =======================================================
//  BASE DASHBOARD (ABSTRACT CLASS)
// =======================================================

abstract class BaseDashboard extends JFrame {
    protected abstract void loadData();
}

// =======================================================
//  FACTORY PATTERN
// =======================================================

class UserFactory {

    public static User createUser(
            String role,
            int id,
            String name,
            String surname,
            String username,
            String tc,
            Doctor doctor
    ) {
        if ("PATIENT".equalsIgnoreCase(role)) {
            return new Patient(id, name, surname, username, tc);
        }

        if ("DOCTOR".equalsIgnoreCase(role)) {
            if (doctor != null) return doctor;

            return new Doctor(
                    id, name, surname, username, tc,
                    "(Branş yok)", "(Poliklinik yok)", ""
            );
        }

        throw new IllegalArgumentException("Bilinmeyen kullanıcı rolü");
    }
}

// =======================================================
//  DAO - USER
// =======================================================

class UserDAO {

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
                        "FROM users u " +
                        "JOIN doctors d ON u.id=d.user_id " +
                        "WHERE d.branch=? " +
                        "ORDER BY u.name,u.surname";

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

// =======================================================
//  DAO - APPOINTMENT
// =======================================================

class AppointmentDAO {

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

// =======================================================
//  UI - LOGIN
// =======================================================

class LoginScreen extends JFrame {

    JTextField txtUsername = new JTextField();
    JPasswordField txtPassword = new JPasswordField();

    public LoginScreen() {

        setTitle("Hastane Randevu Sistemi - Giriş");
        setSize(350, 180);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel p = new JPanel(new GridLayout(3, 2, 10, 10));

        p.add(new JLabel("Kullanıcı Adı:"));
        p.add(txtUsername);

        p.add(new JLabel("Şifre:"));
        p.add(txtPassword);

        JButton btnLogin = new JButton("Giriş Yap");
        JButton btnRegister = new JButton("Kayıt Ol");

        p.add(btnLogin);
        p.add(btnRegister);

        add(p);

        btnLogin.addActionListener(e -> {
            UserDAO dao = new UserDAO();
            User u = dao.login(
                    txtUsername.getText().trim(),
                    new String(txtPassword.getPassword())
            );

            if (u == null) {
                JOptionPane.showMessageDialog(this, "Hatalı kullanıcı adı veya şifre");
                return;
            }

            dispose();

            if (u instanceof Doctor)
                new DoctorDashboard((Doctor) u);
            else
                new PatientDashboard((Patient) u);
        });

        btnRegister.addActionListener(e -> new RegisterScreen());

        setVisible(true);
    }
}

class RegisterScreen extends JFrame {

    JTextField txtName = new JTextField();
    JTextField txtSurname = new JTextField();
    JTextField txtTc = new JTextField();
    JTextField txtUsername = new JTextField();
    JPasswordField txtPassword = new JPasswordField();
    JTextField txtContact = new JTextField();

    public RegisterScreen() {

        setTitle("Hasta Kayıt");
        setSize(420, 360);
        setLocationRelativeTo(null);

        JPanel p = new JPanel(new GridLayout(7, 2, 10, 10));

        p.add(new JLabel("Ad:")); p.add(txtName);
        p.add(new JLabel("Soyad:")); p.add(txtSurname);
        p.add(new JLabel("TC:")); p.add(txtTc);
        p.add(new JLabel("Kullanıcı Adı:")); p.add(txtUsername);
        p.add(new JLabel("Şifre:")); p.add(txtPassword);
        p.add(new JLabel("İletişim:")); p.add(txtContact);

        JButton btnSave = new JButton("Kayıt Ol");
        p.add(new JLabel(""));
        p.add(btnSave);

        add(p);

        btnSave.addActionListener(e -> {

            if (
                    txtName.getText().isEmpty() ||
                            txtSurname.getText().isEmpty() ||
                            txtTc.getText().isEmpty() ||
                            txtUsername.getText().isEmpty() ||
                            txtPassword.getPassword().length == 0
            ) {
                JOptionPane.showMessageDialog(this, "Tüm alanları doldur");
                return;
            }

            UserDAO dao = new UserDAO();

            boolean ok = dao.registerPatient(
                    txtName.getText().trim(),
                    txtSurname.getText().trim(),
                    txtTc.getText().trim(),
                    txtUsername.getText().trim(),
                    new String(txtPassword.getPassword()),
                    txtContact.getText().trim()
            );

            if (!ok) {
                JOptionPane.showMessageDialog(
                        this,
                        "Bu TC veya kullanıcı adı zaten kayıtlı!",
                        "Hata",
                        JOptionPane.ERROR_MESSAGE
                );
            } else {
                JOptionPane.showMessageDialog(
                        this,
                        "Kayıt başarılı. Giriş yapabilirsiniz.",
                        "Başarılı",
                        JOptionPane.INFORMATION_MESSAGE
                );
                dispose();
            }
        });

        setVisible(true);
    }
}

// =======================================================
//  UI - PATIENT DASHBOARD
// =======================================================

class PatientDashboard extends BaseDashboard implements AppointmentObserver {
    private final Patient patient;
    private final UserDAO userDAO = new UserDAO();
    private final AppointmentDAO appointmentDAO = new AppointmentDAO();
    private final WorkingHourStrategy workingHourStrategy = new HourlyWorkingHourStrategy();

    private final DefaultTableModel myModel = new DefaultTableModel(new String[]{"ID","Doktor","Branş","Tarih","Saat","Durum"}, 0);
    private final JTable myTable = new JTable(myModel);

    private JComboBox<String> cmbBranch;
    private JComboBox<Doctor> cmbDoctor;
    private JTextField txtDate;
    private JComboBox<String> cmbTime;

    PatientDashboard(Patient p) {
        this.patient = p;

        setTitle("Hasta Paneli - " + p.getFullName());
        setSize(980, 560);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();

        tabs.add("Randevu Al", buildBookTab());
        tabs.add("Randevularım", buildMyAppointmentsTab());
        tabs.add("Doktor Ara", buildDoctorSearchTab());
        tabs.add("Profilim", buildProfileTab());

        add(tabs, BorderLayout.CENTER);

        JButton logout = new JButton("Çıkış");
        logout.addActionListener(e -> { new LoginScreen().setVisible(true); dispose(); });
        add(logout, BorderLayout.SOUTH);

        AppointmentSubject.getInstance().addObserver(this);

        new AbstractViewTemplate() {
            @Override
            protected void loadData() {
                loadMyAppointments();
            }

            @Override
            protected void buildUI() {
            }
        }.render();

        setVisible(true);
    }

    private JPanel buildBookTab() {
        JPanel root = new JPanel(new BorderLayout());

        JPanel form = new JPanel(new GridLayout(4, 2, 10, 10));
        cmbBranch = new JComboBox<>();
        cmbDoctor = new JComboBox<>();
        txtDate = new JTextField(LocalDate.now().plusDays(1).toString());
        cmbTime = new JComboBox<>();

        for (String b : userDAO.getAllBranches()) cmbBranch.addItem(b);

        JButton btnRefreshDoctors = new JButton("Doktorları Getir");
        JButton btnBook = new JButton("Randevu Oluştur");

        form.add(new JLabel("Branş:")); form.add(cmbBranch);
        form.add(new JLabel("Doktor:")); form.add(cmbDoctor);
        form.add(new JLabel("Tarih (YYYY-AA-GG):")); form.add(txtDate);
        form.add(btnRefreshDoctors); form.add(btnBook);

        root.add(form, BorderLayout.NORTH);

        JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        timePanel.add(new JLabel("Müsait Saat:"));
        timePanel.add(cmbTime);
        JButton btnLoadTimes = new JButton("Saatleri Göster");
        timePanel.add(btnLoadTimes);
        root.add(timePanel, BorderLayout.CENTER);

        btnRefreshDoctors.addActionListener(e -> reloadDoctorsByBranch());
        btnLoadTimes.addActionListener(e -> reloadTimesForSelectedDoctor());
        cmbDoctor.addActionListener(e -> reloadTimesForSelectedDoctor());

        txtDate.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { reloadTimesForSelectedDoctor(); }
            public void removeUpdate(DocumentEvent e) { reloadTimesForSelectedDoctor(); }
            public void changedUpdate(DocumentEvent e) { reloadTimesForSelectedDoctor(); }
        });

        btnBook.addActionListener(e -> {
            try {
                Doctor d = (Doctor) cmbDoctor.getSelectedItem();
                if (d == null) { Ui.err(this, "Doktor seç!"); return; }
                LocalDate date = LocalDate.parse(txtDate.getText().trim());
                String time = (String) cmbTime.getSelectedItem();
                if (time == null) { Ui.err(this, "Saat seç!"); return; }

                if (appointmentDAO.isSlotTaken(d.getId(), date, time)) {
                    Ui.err(this, "Bu saat dolu!");
                    reloadTimesForSelectedDoctor();
                    return;
                }

                appointmentDAO.create(patient.getId(), d.getId(), date, time);
                Ui.info(this, "Randevu oluşturuldu.");
                reloadTimesForSelectedDoctor();
            } catch (Exception ex) {
                Ui.err(this, "Tarih formatı hatalı. Örn: 2025-12-31");
            }
        });

        reloadDoctorsByBranch();
        return root;
    }

    private void reloadDoctorsByBranch() {
        cmbDoctor.removeAllItems();
        String branch = (String) cmbBranch.getSelectedItem();
        if (branch == null) return;
        for (Doctor d : userDAO.getDoctorsByBranch(branch)) cmbDoctor.addItem(d);
        reloadTimesForSelectedDoctor();
    }

    private void reloadTimesForSelectedDoctor() {
        cmbTime.removeAllItems();
        Doctor d = (Doctor) cmbDoctor.getSelectedItem();
        if (d == null) return;

        LocalDate date;
        try {
            date = LocalDate.parse(txtDate.getText().trim());
        } catch (Exception ex) {
            return;
        }

        List<String> slots = workingHourStrategy.generate(d.getWorkingHours());

        for (String s : slots) {
            if (!appointmentDAO.isSlotTaken(d.getId(), date, s)) {
                cmbTime.addItem(s);
            }
        }
    }

    private JPanel buildMyAppointmentsTab() {
        JPanel root = new JPanel(new BorderLayout());

        root.add(new JScrollPane(myTable), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnCancel = new JButton("Seçili Randevuyu İptal Et");
        JButton btnReschedule = new JButton("Seçili Randevuyu Güncelle (Tarih/Saat)");
        JButton btnReload = new JButton("Yenile");

        actions.add(btnCancel);
        actions.add(btnReschedule);
        actions.add(btnReload);

        root.add(actions, BorderLayout.SOUTH);

        btnReload.addActionListener(e -> loadMyAppointments());

        btnCancel.addActionListener(e -> {
            int row = myTable.getSelectedRow();
            if (row < 0) { Ui.err(this, "Bir randevu seç."); return; }
            int id = Integer.parseInt(myModel.getValueAt(row, 0).toString());
            String state = myModel.getValueAt(row, 5).toString();
            if (!"AKTIF".equalsIgnoreCase(state)) { Ui.err(this, "Sadece AKTIF randevu iptal edilir."); return; }
            appointmentDAO.cancelByPatient(id, patient.getId());
            Ui.info(this, "Randevu iptal edildi.");
        });

        btnReschedule.addActionListener(e -> {
            int row = myTable.getSelectedRow();
            if (row < 0) { Ui.err(this, "Bir randevu seç."); return; }

            int appointmentId = Integer.parseInt(myModel.getValueAt(row, 0).toString());
            String state = myModel.getValueAt(row, 5).toString();
            if (!"AKTIF".equalsIgnoreCase(state)) { Ui.err(this, "Sadece AKTIF randevu güncellenir."); return; }

            String doctorName = myModel.getValueAt(row, 1).toString();

            JTextField newDate = new JTextField(LocalDate.now().plusDays(1).toString());
            JComboBox<String> newTime = new JComboBox<>();

            int doctorId = getDoctorIdByAppointment(appointmentId, patient.getId());
            Doctor d = getDoctorById(doctorId);

            if (d == null) { Ui.err(this, "Doktor bulunamadı."); return; }

            Runnable refreshTimes = () -> {
                newTime.removeAllItems();
                LocalDate dt;
                try { dt = LocalDate.parse(newDate.getText().trim()); } catch (Exception ex) { return; }
                List<String> slots = workingHourStrategy.generate(d.getWorkingHours());
                for (String s : slots) {
                    if (!appointmentDAO.isSlotTaken(d.getId(), dt, s)) newTime.addItem(s);
                }
            };
            refreshTimes.run();
            newDate.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { refreshTimes.run(); }
                public void removeUpdate(DocumentEvent e) { refreshTimes.run(); }
                public void changedUpdate(DocumentEvent e) { refreshTimes.run(); }
            });

            JPanel panel = new JPanel(new GridLayout(4,1,6,6));
            panel.add(new JLabel("Doktor: " + doctorName));
            panel.add(new JLabel("Yeni Tarih:"));
            panel.add(newDate);
            panel.add(newTime);

            int ok = JOptionPane.showConfirmDialog(this, panel, "Randevu Güncelle", JOptionPane.OK_CANCEL_OPTION);
            if (ok == JOptionPane.OK_OPTION) {
                try {
                    LocalDate nd = LocalDate.parse(newDate.getText().trim());
                    String nt = (String) newTime.getSelectedItem();
                    if (nt == null) { Ui.err(this, "Saat seç!"); return; }
                    if (appointmentDAO.isSlotTaken(doctorId, nd, nt)) { Ui.err(this, "Seçilen saat dolu."); return; }

                    appointmentDAO.rescheduleByPatient(appointmentId, patient.getId(), doctorId, nd, nt);
                    Ui.info(this, "Randevu güncellendi.");
                } catch (Exception ex) {
                    Ui.err(this, "Geçersiz tarih/saat.");
                }
            }
        });

        return root;
    }

    private int getDoctorIdByAppointment(int appointmentId, int patientId) {
        String sql = "SELECT doctor_id FROM appointments WHERE id=? AND patient_id=?";
        try (PreparedStatement ps = DatabaseManager.getInstance().getConnection().prepareStatement(sql)) {
            ps.setInt(1, appointmentId);
            ps.setInt(2, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
                return -1;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Doctor getDoctorById(int doctorUserId) {
        try {
            return userDAO.getDoctorByUserId(doctorUserId);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private JPanel buildDoctorSearchTab() {
        JPanel root = new JPanel(new BorderLayout());

        JTextField q = new JTextField();
        JButton btn = new JButton("Ara (Ad/Soyad/Branş)");

        DefaultTableModel m = new DefaultTableModel(new String[]{"ID","TC","Doktor","Branş","Poliklinik","Çalışma Saatleri"}, 0);
        JTable t = new JTable(m);

        JPanel top = new JPanel(new BorderLayout(8,8));
        top.add(q, BorderLayout.CENTER);
        top.add(btn, BorderLayout.EAST);

        root.add(top, BorderLayout.NORTH);
        root.add(new JScrollPane(t), BorderLayout.CENTER);

        btn.addActionListener(e -> {
            m.setRowCount(0);
            String query = q.getText().trim();
            if (query.isEmpty()) return;
            for (Doctor d : userDAO.searchDoctors(query)) {
                m.addRow(new Object[]{
                        d.getId(),
                        d.getTc(),
                        d.getFullName(),
                        d.getBranch(),
                        d.getClinic(),
                        d.getWorkingHours()
                });
            }
        });

        return root;
    }

    private JPanel buildProfileTab() {
        JPanel root = new JPanel(new GridLayout(6,2,10,10));

        String currentContact = userDAO.getContactInfo(patient.getId());
        String currentPass = userDAO.getPassword(patient.getId());

        JTextField txtContact = new JTextField(currentContact == null ? "" : currentContact);
        JPasswordField txtPass = new JPasswordField(currentPass == null ? "" : currentPass);

        root.add(new JLabel("TC:")); root.add(new JLabel(patient.getTc()));
        root.add(new JLabel("Ad Soyad:")); root.add(new JLabel(patient.getFullName()));
        root.add(new JLabel("İletişim Bilgisi:")); root.add(txtContact);
        root.add(new JLabel("Şifre:")); root.add(txtPass);

        JButton save = new JButton("Kaydet");
        root.add(new JLabel(""));
        root.add(save);

        save.addActionListener(e -> {
            userDAO.updateContactInfoAndPassword(patient.getId(), txtContact.getText().trim(), new String(txtPass.getPassword()));
            Ui.info(this, "Profil güncellendi.");
        });

        return root;
    }

    private void loadMyAppointments() {
        myModel.setRowCount(0);
        for (String[] r : appointmentDAO.getByPatient(patient.getId())) myModel.addRow(r);
    }

    @Override
    protected void loadData() {
        loadMyAppointments();
    }

    @Override
    public void onAppointmentChanged() {
        loadData();
    }
}

// =======================================================
//  UI - DOCTOR DASHBOARD
// =======================================================

class DoctorDashboard extends BaseDashboard implements AppointmentObserver {
    private final Doctor doctor;
    private final UserDAO userDAO = new UserDAO();
    private final AppointmentDAO appointmentDAO = new AppointmentDAO();
    private final WorkingHourStrategy workingHourStrategy = new HourlyWorkingHourStrategy();

    private final DefaultTableModel appModel = new DefaultTableModel(new String[]{"ID","Hasta TC","Hasta","Tarih","Saat","Durum"}, 0);
    private final JTable appTable = new JTable(appModel);

    private JTextArea txtNote;
    private JTextArea txtPrescription;

    DoctorDashboard(Doctor d) {
        this.doctor = d;

        setTitle("Doktor Paneli - " + d.getFullName());
        setSize(1100, 620);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Randevular", buildAppointmentsTab());
        tabs.add("Muayene / Reçete", buildExamTab());
        tabs.add("Hasta Ara", buildPatientSearchTab());
        tabs.add("Ayarlar", buildSettingsTab());

        add(tabs, BorderLayout.CENTER);

        JButton logout = new JButton("Çıkış");
        logout.addActionListener(e -> { new LoginScreen().setVisible(true); dispose(); });
        add(logout, BorderLayout.SOUTH);

        AppointmentSubject.getInstance().addObserver(this);

        new AbstractViewTemplate() {
            @Override
            protected void loadData() {
                loadDoctorAppointments(LocalDate.now(), LocalDate.now());
            }

            @Override
            protected void buildUI() {
            }
        }.render();

        setVisible(true);
    }

    private JPanel buildAppointmentsTab() {
        JPanel root = new JPanel(new BorderLayout());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton btnDaily = new JButton("Günlük");
        JButton btnWeekly = new JButton("Haftalık");
        JTextField from = new JTextField(LocalDate.now().toString(), 10);
        JTextField to = new JTextField(LocalDate.now().toString(), 10);
        JButton btnRange = new JButton("Tarih Aralığı Listele");

        top.add(new JLabel("Filtre: "));
        top.add(btnDaily);
        top.add(btnWeekly);
        top.add(new JLabel("Başlangıç:")); top.add(from);
        top.add(new JLabel("Bitiş:")); top.add(to);
        top.add(btnRange);

        root.add(top, BorderLayout.NORTH);
        root.add(new JScrollPane(appTable), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnDone = new JButton("Tamamlandı");
        JButton btnNoShow = new JButton("Hasta Gelmedi");
        JButton btnCancel = new JButton("İptal (Doktor)");
        JButton btnReload = new JButton("Yenile");

        bottom.add(btnDone);
        bottom.add(btnNoShow);
        bottom.add(btnCancel);
        bottom.add(btnReload);

        root.add(bottom, BorderLayout.SOUTH);

        btnDaily.addActionListener(e -> loadDoctorAppointments(LocalDate.now(), LocalDate.now()));

        btnWeekly.addActionListener(e -> {
            LocalDate s = WorkingHoursUtil.startOfWeek(LocalDate.now());
            LocalDate ee = WorkingHoursUtil.endOfWeek(LocalDate.now());
            loadDoctorAppointments(s, ee);
        });

        btnRange.addActionListener(e -> {
            try {
                LocalDate f = LocalDate.parse(from.getText().trim());
                LocalDate t = LocalDate.parse(to.getText().trim());
                loadDoctorAppointments(f, t);
            } catch (Exception ex) {
                Ui.err(this, "Tarih formatı yanlış. Örn: 2025-12-31");
            }
        });

        btnReload.addActionListener(e -> loadDoctorAppointments(LocalDate.now(), LocalDate.now()));

        btnDone.addActionListener(e -> updateSelectedState(new TamamlandiState()));
        btnNoShow.addActionListener(e -> updateSelectedState(new GelmediState()));
        btnCancel.addActionListener(e -> updateSelectedState(new IptalState()));

        appTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) fillExamFieldsFromSelected();
        });

        return root;
    }

    private void loadDoctorAppointments(LocalDate from, LocalDate to) {
        appModel.setRowCount(0);
        for (String[] r : appointmentDAO.getByDoctorBetween(doctor.getId(), from, to)) appModel.addRow(r);
    }

    private Integer getSelectedAppointmentId() {
        int row = appTable.getSelectedRow();
        if (row < 0) return null;
        return Integer.parseInt(appModel.getValueAt(row, 0).toString());
    }

    private void updateSelectedState(AppointmentState newState) {
        Integer id = getSelectedAppointmentId();
        if (id == null) { Ui.err(this, "Bir randevu seç."); return; }
        appointmentDAO.updateStateByDoctor(id, doctor.getId(), newState);
        Ui.info(this, "Durum güncellendi: " + newState.getStateName());
    }

    private JPanel buildExamTab() {
        JPanel root = new JPanel(new BorderLayout());

        JPanel info = new JPanel(new FlowLayout(FlowLayout.LEFT));
        info.add(new JLabel("Not: Randevu seç → Muayene/Recepte doldur → Kaydet"));
        root.add(info, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1,2,10,10));
        txtNote = new JTextArea();
        txtPrescription = new JTextArea();
        center.add(new JScrollPane(txtNote));
        center.add(new JScrollPane(txtPrescription));

        JPanel labels = new JPanel(new GridLayout(1,2,10,10));
        labels.add(new JLabel("Muayene Notu"));
        labels.add(new JLabel("Reçete"));

        JPanel mid = new JPanel(new BorderLayout());
        mid.add(labels, BorderLayout.NORTH);
        mid.add(center, BorderLayout.CENTER);

        root.add(mid, BorderLayout.CENTER);

        JButton btnSave = new JButton("Seçili Randevuya Kaydet");
        btnSave.addActionListener(e -> saveExamForSelected());
        root.add(btnSave, BorderLayout.SOUTH);

        return root;
    }

    private void fillExamFieldsFromSelected() {
        Integer id = getSelectedAppointmentId();
        if (id == null) return;
        String[] np = appointmentDAO.getExam(id, doctor.getId());
        txtNote.setText(np[0] == null ? "" : np[0]);
        txtPrescription.setText(np[1] == null ? "" : np[1]);
    }

    private void saveExamForSelected() {
        Integer id = getSelectedAppointmentId();
        if (id == null) { Ui.err(this, "Önce randevu seç."); return; }
        appointmentDAO.saveExam(id, doctor.getId(), txtNote.getText().trim(), txtPrescription.getText().trim());
        Ui.info(this, "Muayene notu & reçete kaydedildi.");
    }

    private JPanel buildPatientSearchTab() {
        JPanel root = new JPanel(new BorderLayout());

        JTextField q = new JTextField();
        JButton btn = new JButton("Ara (TC / Ad Soyad)");

        DefaultTableModel m = new DefaultTableModel(new String[]{"HastaID","TC","Ad","Soyad"}, 0);
        JTable t = new JTable(m);

        JPanel top = new JPanel(new BorderLayout(8,8));
        top.add(q, BorderLayout.CENTER);
        top.add(btn, BorderLayout.EAST);

        JPanel filter = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField from = new JTextField(LocalDate.now().minusDays(7).toString(), 10);
        JTextField to = new JTextField(LocalDate.now().plusDays(7).toString(), 10);
        JButton btnList = new JButton("Seçili Hastanın Randevuları (Tarih Aralığı)");

        filter.add(new JLabel("Başlangıç:")); filter.add(from);
        filter.add(new JLabel("Bitiş:")); filter.add(to);
        filter.add(btnList);

        DefaultTableModel rm = new DefaultTableModel(new String[]{"ID","Doktor","Branş","Tarih","Saat","Durum"}, 0);
        JTable rt = new JTable(rm);

        JPanel center = new JPanel(new GridLayout(2,1,10,10));
        center.add(new JScrollPane(t));
        center.add(new JScrollPane(rt));

        root.add(top, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(filter, BorderLayout.SOUTH);

        btn.addActionListener(e -> {
            m.setRowCount(0);
            String query = q.getText().trim();
            if (query.isEmpty()) return;
            for (String[] r : userDAO.searchPatientsByTcOrName(query)) m.addRow(r);
        });

        btnList.addActionListener(e -> {
            int row = t.getSelectedRow();
            if (row < 0) { Ui.err(this, "Hasta seç."); return; }

            int patientId = Integer.parseInt(m.getValueAt(row,0).toString());
            LocalDate f, tt;
            try {
                f = LocalDate.parse(from.getText().trim());
                tt = LocalDate.parse(to.getText().trim());
            } catch (Exception ex) {
                Ui.err(this, "Tarih formatı yanlış.");
                return;
            }

            rm.setRowCount(0);
            for (String[] r : appointmentDAO.getByPatientBetween(patientId, f, tt)) rm.addRow(r);
        });

        return root;
    }

    private JPanel buildSettingsTab() {
        JPanel root = new JPanel(new GridLayout(8,2,10,10));

        JLabel lblBranch = new JLabel(doctor.getBranch());
        JLabel lblClinic = new JLabel(doctor.getClinic());
        JTextField txtWh = new JTextField(doctor.getWorkingHours() == null ? "" : doctor.getWorkingHours());

        root.add(new JLabel("Branş:")); root.add(lblBranch);
        root.add(new JLabel("Poliklinik:")); root.add(lblClinic);
        root.add(new JLabel("Çalışma Saatleri (örn 09:00-12:00,13:00-17:00):")); root.add(txtWh);

        JButton save = new JButton("Çalışma Saatlerini Kaydet");
        root.add(new JLabel("")); root.add(save);

        String currentContact = userDAO.getContactInfo(doctor.getId());
        String currentPass = userDAO.getPassword(doctor.getId());
        JTextField txtContact = new JTextField(currentContact == null ? "" : currentContact);
        JPasswordField txtPass = new JPasswordField(currentPass == null ? "" : currentPass);

        root.add(new JLabel("İletişim Bilgisi:")); root.add(txtContact);
        root.add(new JLabel("Şifre:")); root.add(txtPass);

        JButton saveProfile = new JButton("Profil Kaydet");
        root.add(new JLabel("")); root.add(saveProfile);

        save.addActionListener(e -> {
            String wh = txtWh.getText().trim();
            if (!wh.isEmpty()) {
                List<String> slots = workingHourStrategy.generate(wh);
                if (slots.isEmpty()) {
                    Ui.err(this, "Çalışma saat formatı yanlış. Örn: 09:00-12:00,13:00-17:00");
                    return;
                }
            }
            userDAO.updateDoctorWorkingHours(doctor.getId(), wh);
            Ui.info(this, "Çalışma saatleri güncellendi. (Randevu ekranında otomatik etkiler)");
        });

        saveProfile.addActionListener(e -> {
            userDAO.updateContactInfoAndPassword(doctor.getId(), txtContact.getText().trim(), new String(txtPass.getPassword()));
            Ui.info(this, "Profil güncellendi.");
        });

        return root;
    }

    @Override
    protected void loadData() {
        loadDoctorAppointments(LocalDate.now(), LocalDate.now());
    }

    @Override
    public void onAppointmentChanged() {
        loadData();
    }
}

// =======================================================
//  MAIN
// =======================================================

public class HastaneSistemi {
    public static void main(String[] args) {
        DatabaseManager.getInstance();
        SwingUtilities.invokeLater(() -> new LoginScreen().setVisible(true));
}
}