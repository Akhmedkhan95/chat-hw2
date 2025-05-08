package ru.otus.chat;

import java.sql.*;
import java.util.Properties;


public class PostgresAuthenticatedProvider implements AuthenticatedProvider {

    private final Server server;
    private Connection connection;


    public PostgresAuthenticatedProvider(Server server) {
        this.server = server;
    }

    @Override
    public void initialize() {
        try {
            Properties props = new Properties();
            props.setProperty("user", DbProperties.DB_USER);
            props.setProperty("password", DbProperties.DB_PASSWORD);

            connection = DriverManager.getConnection(DbProperties.JDBC_URL, props);
            System.out.println("Подключение к PostgresSQL установлено");
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка подключения к PostgresSQL", e);
        }
    }

    @Override
    public boolean authenticate(ClientHandler clientHandler, String login, String password) {

        try (PreparedStatement stmt = connection.prepareStatement(DbProperties.selectLoginAndPassword)) {
            stmt.setString(1, login);
            stmt.setString(2, password);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String username = rs.getString("username");
                String role = rs.getString("role");

                if (server.isUsernameBusy(username)) {
                    clientHandler.sendMsg("Данная учетная запись уже занята");
                    return false;
                }

                clientHandler.setUsername(username);
                clientHandler.setRole(UserRole.valueOf(role));
                server.subscribe(clientHandler);
                clientHandler.sendMsg("/author " + username);
                return true;
            } else {
                clientHandler.sendMsg("Некорректный логин/пароль");
                return false;
            }
        } catch (SQLException e) {
            clientHandler.sendMsg("Ошибка аутентификации");
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean registration(ClientHandler clientHandler, String login, String password, String username) {
        if (login.trim().length() < 3 || password.trim().length() < 3 || username.trim().length() < 3) {
            clientHandler.sendMsg("Логин 3+ символа, пароль 3+ символа, имя пользователя 3+ символа");
            return false;
        }

        if (isLoginExists(login)) {
            clientHandler.sendMsg("Указанный логин уже занят");
            return false;
        }

        if (isUsernameExists(username)) {
            clientHandler.sendMsg("Указанное имя пользователя уже занято");
            return false;
        }
        return saveUser(login, password, username, clientHandler);
    }

    private boolean saveUser(String login, String password, String username, ClientHandler clientHandler) {
        try (PreparedStatement stmt = connection.prepareStatement(DbProperties.insertUsers)) {
            stmt.setString(1, login);
            stmt.setString(2, password);
            stmt.setString(3, username);
            stmt.setString(4, UserRole.USER.name());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                clientHandler.setUsername(username);
                clientHandler.setRole(UserRole.USER);
                server.subscribe(clientHandler);
                clientHandler.sendMsg("/reload " + username);
                return true;
            } else {
                clientHandler.sendMsg("Ошибка регистрации");
                return false;
            }
        } catch (SQLException e) {
            clientHandler.sendMsg("Ошибка регистрации");
            e.printStackTrace();
            return false;
        }
    }

    private boolean isLoginExists(String login) {
                try (PreparedStatement stmt = connection.prepareStatement(DbProperties.selectUserWhereLogin)) {
            stmt.setString(1, login);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return true;
        }
    }

    private boolean isUsernameExists(String username) {
        try (PreparedStatement stmt = connection.prepareStatement(DbProperties.selectUserWhereUserName)) {
            stmt.setString(1, username);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return true;
        }
    }

    public void shutdown() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("Соединение с PostgresSQL закрыто");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
