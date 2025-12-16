package ui;

import dao.AppointmentDAO;
import dao.UserDAO;
import db.DatabaseManager;
import model.Doctor;
import model.Patient;
import observer.AppointmentObserver;
import observer.AppointmentSubject;
import strategy.HourlyWorkingHourStrategy;
import strategy.WorkingHourStrategy;
import template.AbstractViewTemplate;
import util.Ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class PatientDashboard extends BaseDashboard implements AppointmentObserver {
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

    public PatientDashboard(Patient p) {
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
