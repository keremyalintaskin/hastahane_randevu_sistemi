package main;

import db.DatabaseManager;
import ui.LoginScreen;

import javax.swing.*;

public class HastaneSistemi {
    public static void main(String[] args) {
        DatabaseManager.getInstance();
        SwingUtilities.invokeLater(() -> new LoginScreen().setVisible(true));
    }
}
