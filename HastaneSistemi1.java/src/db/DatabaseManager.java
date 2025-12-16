package db;

import java.sql.*;

public class DatabaseManager {
    private static DatabaseManager instance;
    private final Connection conn;

    private static final String URL = "jdbc:mysql://localhost:3306/hospital_randevu?useSSL=false&serverTimezone=UTC";
    private static final String USER = "root";
    private static final String PASS = "1234";

    private DatabaseManager() {
        try {
            conn = DriverManager.getConnection(URL, USER, PASS);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    public Connection getConnection() { return conn; }
}
