package ru.otus.chat;

public class DbProperties {
    public static final String JDBC_URL = "jdbc:postgresql://localhost:5432/chat_db";
    public static final String DB_USER = "chat_admin";
    public static final String DB_PASSWORD = "chat_password";
    public static final String selectLoginAndPassword = "SELECT username, role FROM users WHERE login = ? AND password = crypt(?, password)";
    public static final String insertUsers = "INSERT INTO users (login, password, username, role) VALUES (?, crypt(?, gen_salt('bf')), ?, ?)";
    public static final String selectUserWhereLogin = "SELECT 1 FROM users WHERE login = ?";
    public static final String selectUserWhereUserName = "SELECT 1 FROM users WHERE username = ?";
    }
