package ru.otus.chat;

public class PostgresAuthenticatedProvider implements AuthenticatedProvider {

    private final Server server;
    private Connection connection;

    public PostgresAuthenticatedProvider(Server server) {
        this.server = server;
    }

    @Override
    public void initialize() {
        try {
            // Настройки подключения (лучше вынести в конфиг)
            String url = "jdbc:postgresql://localhost:5432/chat_db";
            Properties props = new Properties();
            props.setProperty("user", "chat_admin");
            props.setProperty("password", "chat_password");

            connection = DriverManager.getConnection(url, props);
            System.out.println("Подключение к PostgreSQL установлено");
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка подключения к PostgreSQL", e);
        }
    }

    @Override
    public boolean authenticate(ClientHandler clientHandler, String login, String password) {
        String sql = "SELECT username, role FROM users WHERE login = ? AND password = crypt(?, password)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
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
                clientHandler.sendMsg("/authok " + username);
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
        // Проверка минимальной длины
        if (login.trim().length() < 3 || password.trim().length() < 3 || username.trim().length() < 3) {
            clientHandler.sendMsg("Логин 3+ символа, пароль 3+ символа, имя пользователя 3+ символа");
            return false;
        }

        // Проверка существования логина
        if (isLoginExists(login)) {
            clientHandler.sendMsg("Указанный логин уже занят");
            return false;
        }

        // Проверка существования имени пользователя
        if (isUsernameExists(username)) {
            clientHandler.sendMsg("Указанное имя пользователя уже занято");
            return false;
        }

        // Регистрация нового пользователя
        String sql = "INSERT INTO users (login, password, username, role) VALUES (?, crypt(?, gen_salt('bf')), ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, login);
            stmt.setString(2, password);
            stmt.setString(3, username);
            stmt.setString(4, UserRole.USER.name());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                clientHandler.setUsername(username);
                clientHandler.setRole(UserRole.USER);
                server.subscribe(clientHandler);
                clientHandler.sendMsg("/regok " + username);
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
        String sql = "SELECT 1 FROM users WHERE login = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, login);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return true; // В случае ошибки считаем что логин занят
        }
    }

    private boolean isUsernameExists(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return true; // В случае ошибки считаем что имя занято
        }
    }
    public void shutdown() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("Соединение с PostgreSQL закрыто");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
