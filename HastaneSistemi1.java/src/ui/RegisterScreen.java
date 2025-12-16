package ui;

import dao.UserDAO;

import javax.swing.*;
import java.awt.*;

public class RegisterScreen extends JFrame {

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
