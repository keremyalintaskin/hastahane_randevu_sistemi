package ui;

import dao.AppointmentDAO;
import dao.UserDAO;
import model.Doctor;
import observer.AppointmentObserver;
import observer.AppointmentSubject;
import state.*;
import strategy.HourlyWorkingHourStrategy;
import strategy.WorkingHourStrategy;
import template.AbstractViewTemplate;
import util.Ui;
import util.WorkingHoursUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;

public class DoctorDashboard extends BaseDashboard implements AppointmentObserver {
    private final Doctor doctor;
    private final UserDAO userDAO = new UserDAO();
    private final AppointmentDAO appointmentDAO = new AppointmentDAO();
    private final WorkingHourStrategy workingHourStrategy = new HourlyWorkingHourStrategy();

    private final DefaultTableModel appModel = new DefaultTableModel(new String[]{"ID","Hasta TC","Hasta","Tarih","Saat","Durum"}, 0);
    private final JTable appTable = new JTable(appModel);

    private JTextArea txtNote;
    private JTextArea txtPrescription;

    public DoctorDashboard(Doctor d) {
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
