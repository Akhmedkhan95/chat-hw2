package ru.otus.chat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Server {
    private int port;
    private List<ClientHandler> clients;
    private AuthenticatedProvider authenticatedProvider;

    public Server(int port) {
        this.port = port;
        clients = new CopyOnWriteArrayList<>();
        authenticatedProvider = new PostgresAuthenticatedProvider(this);
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Сервер запущен на порту " + port);
            authenticatedProvider.initialize();

            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler(socket, this);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void subscribe(ClientHandler clientHandler) {
        clients.add(clientHandler);
    }

    public void unsubscribe(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        System.out.println("Клиент " + clientHandler.getUsername() + " отлючился");
    }

    public void broadcastMessage(String message) {
        for (ClientHandler client : clients) {
            client.sendMsg(message);
        }
    }

    public boolean isUsernameBusy(String username) {
        for (ClientHandler client : clients) {
            if (client.getUsername().equals(username)) {
                return true;
            }
        }
        return false;
    }

    public void kickUserByAdmin(String username, ClientHandler requester) {
        if (requester.getRole() != UserRole.ADMIN) {
            requester.sendMsg("У вас нет прав для выполнения этой команды");
            return;
        }

        for (ClientHandler client : clients) {
            if (client.getUsername().equals(username)) {
                client.sendMsg("/kicked Вы были отключены администратором");
                client.disconnect();
                requester.sendMsg("Пользователь " + username + " был отключен");
                return;
            }
        }
        requester.sendMsg("Пользователь " + username + " не найден");
    }

    public AuthenticatedProvider getAuthenticatedProvider() {
        return authenticatedProvider;
    }

    public void stop() {
        authenticatedProvider.shutdown();
    }

}
