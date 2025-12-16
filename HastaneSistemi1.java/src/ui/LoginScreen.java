package ui;

import dao.UserDAO;
import model.Doctor;
import model.Patient;
import model.User;

import javax.swing.*;
import java.awt.*;

public class LoginScreen extends JFrame {

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
