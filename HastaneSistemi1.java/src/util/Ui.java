package util;

import javax.swing.*;

public class Ui {
    public static void err(JFrame p, String m) {
        JOptionPane.showMessageDialog(p, m, "Hata", JOptionPane.ERROR_MESSAGE);
    }
    public static void info(JFrame p, String m) {
        JOptionPane.showMessageDialog(p, m, "Bilgi", JOptionPane.INFORMATION_MESSAGE);
    }
}
