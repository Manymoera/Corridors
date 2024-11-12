package org.example.demo;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;

import java.util.Objects;
import java.util.Random;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class GameController {
    @FXML
    private GridPane RectPane;

    @FXML
    private Label labelPlayerSymbol;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String active_player;
    private String local_player;
    private Integer id;

    @FXML
    public void initialize() {
        Random random = new Random();
        id = random.nextInt(100);
        System.out.println("ID: " + id);
        try {
            socket = new Socket("localhost", 12345);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            System.out.println(socket.getRemoteSocketAddress().toString());
            out.writeObject("ID");
            out.writeObject(Integer.toString(id));
            out.flush();

            // Запускаем поток для прослушивания обновлений от сервера
            new Thread(this::listenForUpdates).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void listenForUpdates() {
        try {
            while (true) {
                String message = (String) in.readObject();
                if (message.startsWith("teamMessage:")) {
                    // Обработка команды от сервера (установка текста Label)
                    String teamMessage = message.split(":", 2)[1];
                    Platform.runLater(() -> labelPlayerSymbol.setText(teamMessage));
                    if(Objects.equals(teamMessage, "Your team: RED")) {
                        local_player = "RED";
                        System.out.println(local_player);
                    } else {
                        local_player = "BLUE";
                        System.out.println(local_player);
                    }
                } else {
                    // Обработка других сообщений, например, изменения цвета прямоугольника
                    Platform.runLater(() -> updateRectangleColor(message));
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onRectangleClick(MouseEvent event) {
        if (event.getSource() instanceof Rectangle rectangle) {

            Integer row = GridPane.getRowIndex(rectangle.getParent());
            Integer column = GridPane.getColumnIndex(rectangle.getParent());
            Boolean allMatch = true;
            Color targColor = null;

            if (row != null && column != null
                    && rectangle.getFill() == Color.GREY) {
                rectangle.setFill(getColorFromPlayer(local_player, false));
                targColor = getColorFromPlayer(local_player, false);
                sendColorUpdate(row, column, getColorFromPlayer(local_player, false),
                                StackPane.getAlignment(rectangle));
            }
            StackPane stackPane = (StackPane) rectangle.getParent();
            for(var child : stackPane.getChildren()) {
                if(child instanceof Rectangle otherRect) {
                    if(StackPane.getAlignment(otherRect) == Pos.CENTER) {
                        continue;
                    }
                    else if(!targColor.equals(otherRect.getFill())) {
                        allMatch = false;
                        break;
                    }
                }
            }
            if(allMatch) {
                for (var child : stackPane.getChildren()) {
                    if (child instanceof Rectangle centerRectangle &&
                            StackPane.getAlignment(centerRectangle) == Pos.CENTER) {
                        centerRectangle.setFill(getColorFromPlayer(local_player, true));
                        sendColorUpdate(row, column, getColorFromPlayer(local_player, true),
                                        StackPane.getAlignment(centerRectangle));
                        break;
                    }
                }
            }
        }
    }

    private void sendColorUpdate(int row, int column, Color color, Pos rect_pos) {
        try {
            String message = row + "," + column + "," + color.toString() + "," + rect_pos;
            out.writeObject("MOVE");
            out.writeObject(Integer.toString(id));
            out.writeObject(message);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateRectangleColor(String message) {
        String[] parts = message.split(",");
        int row = Integer.parseInt(parts[0]);
        int column = Integer.parseInt(parts[1]);
        Color color = Color.valueOf(parts[2]);
        Pos rect_pos = Pos.valueOf(parts[3]);

        Rectangle rectangle = findRectangleByPosition(row, column, rect_pos);
        rectangle.setFill(color);
    }

    private Rectangle findRectangleByPosition(int row, int column, Pos rect_pos) {
        for (var node : RectPane.getChildren()) {
            if (node instanceof StackPane stackPane &&
                    GridPane.getRowIndex(node) == row &&
                    GridPane.getColumnIndex(node) == column) {
                System.out.println(row + "," + column);
                for (Node child : stackPane.getChildren()) {
                    if (child instanceof Rectangle rectangle &&
                        StackPane.getAlignment(rectangle) == rect_pos) {
                        System.out.println("Row and Column: " + row + "," + column);
                        return rectangle;
                    }
                }
            }
        }
        return null;
    }

    private Color getColorFromPlayer(String player, Boolean isCenter) {
        if (Objects.equals(player, "RED")) {
            if (isCenter) {
                return Color.DARKRED;
            }
            return Color.RED;
        }
        else {
            if (isCenter) {
                return Color.DARKBLUE;
            }
            return Color.BLUE;
        }
    }
}