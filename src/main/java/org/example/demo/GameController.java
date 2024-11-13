package org.example.demo;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.Objects;
import java.util.Random;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class GameController {
    @FXML
    public Label WinL;
    @FXML
    private GridPane RectPane;
    @FXML
    private Label labelPlayerSymbol;
    @FXML
    private Label labelPlayerMove;
    @FXML
    private Label RScore;
    @FXML
    private Label BScore;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String active_player;
    private String local_player;
    private Integer id;
    private Integer BlueScore = 0;
    private Integer RedScore = 0;
    private Boolean winnerPr = false;

    @FXML
    public void initialize() {
        Random random = new Random();
        id = random.nextInt(100);
        System.out.println("ID: " + id);
        try {
            socket = new Socket("localhost", 8080);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            out.writeObject("ID");
            out.writeObject(Integer.toString(id));

            new Thread(this::listenForUpdates).start();
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    private void listenForUpdates() {
        try {
            while (!socket.isClosed()) {
                String message = (String) in.readObject();
                if (message.startsWith("teamMessage:")) {
                    String teamMessage = message.split(":", 2)[1];
                    Platform.runLater(() -> labelPlayerSymbol.setText(teamMessage));
                    if(Objects.equals(teamMessage, "Your team: RED")) {
                        local_player = "RED";
                        active_player = local_player;
                        System.out.println(local_player);
                        labelPlayerMove.setText("RED turn");
                    } else {
                        local_player = "BLUE";
                        active_player = "RED";
                        System.out.println(local_player);
                        labelPlayerMove.setText("RED turn");
                    }
                } else if(message.equals("RED") || message.equals("BLUE")) {
                    active_player = message;
                    System.out.println("Active player from server: " + active_player);
                    Platform.runLater(() -> labelPlayerMove.setText(active_player + " turn"));
                } else if(message.equals("RED SCORED")) {
                    RedScore++;
                    Platform.runLater(() -> RScore.setText("RED Score: " + RedScore));
                    WinCondition("RED", RedScore);
                } else if(message.equals("BLUE SCORED")) {
                    BlueScore++;
                    Platform.runLater(() -> BScore.setText("BLUE Score: " + BlueScore));
                    WinCondition("BLUE", BlueScore);
                } else if(message.startsWith("WINS")) {
                    winnerPr = true;
                    System.out.println(message);
                    Platform.runLater(() -> WinL.setText(message));
                    closeConnection();
                }
                else {
                    // Обработка других сообщений, например, изменения цвета прямоугольника
                    updateRectangleColor(message);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println(e);
        }
    }

    @FXML
    private void onRectangleClick(MouseEvent event) {
        if(Objects.equals(active_player, local_player) && !winnerPr) {
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
                        else {
                            assert targColor != null;
                            if(otherRect.getFill() == Color.GREY) {
                                allMatch = false;
                                break;
                            }
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
                            if(Objects.equals(local_player, "RED")) {
                                sendMessage("SCORE", "RED SCORED");
                            } else {
                                sendMessage("SCORE", "BLUE SCORED");
                            }
                            break;
                        }
                    }
                }
            }
            sendMessage("CHANGE", active_player);
        }
    }

    private void sendMessage(String tag, String message) {
        try {
            out.writeObject(tag);
            out.writeObject(message);
        }
        catch (IOException e) {
            System.out.println(e);
        }
    }

    private void WinCondition(String side, Integer score) {
        if(score >= 1) {
            String win_message = "WINS " + side;
            try {
                out.writeObject("WIN");
                out.writeObject(win_message);
            } catch (IOException e) {
                System.out.println(e);
            }
        }
    }

    private void sendColorUpdate(int row, int column, Color color, Pos rect_pos) {
        try {
            String message = row + "," + column + "," + color.toString() + "," + rect_pos;
            out.writeObject("MOVE");
            out.writeObject(Integer.toString(id));
            out.writeObject(message);
        } catch (IOException e) {
            System.out.println(e);
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

    private void closeConnection() {
        try {
            in.close();
            out.close();
            socket.close();
        }
        catch (IOException i) {
            System.out.println(i);
        }
    }
}