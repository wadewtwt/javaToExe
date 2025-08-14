package com.example.javafxtest;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(MainApp.class.getResource("ControlUI.fxml"));
            if (fxmlLoader.getLocation() == null) {
                throw new IOException("Cannot find FXML file. It seems the resource is not correctly bundled.");
            }
            Parent root = fxmlLoader.load();
            Scene scene = new Scene(root, 640, 480);
            stage.setTitle("Jar 启动器");
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            logErrorToFile(e);
        }
    }

    private void logErrorToFile(Exception e) {
        String userHome = System.getProperty("user.home");
        File logFile = new File(userHome, "jar_starter_error.log");
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            writer.println("----------------------------------------");
            writer.println("Timestamp: " + new Date());
            e.printStackTrace(writer);
            writer.println("----------------------------------------");
            writer.println();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }


    public static void main(String[] args) {
        launch(args);
    }
} 