package model;

public abstract class User {
    protected final int id;
    protected final String name;
    protected final String surname;
    protected final String username;
    protected final String role;
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
