package com.example.javafxtest;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.util.Date;

public class ControlUIController {

    @FXML
    private Label jarPathLabel;
    @FXML
    private Button startButton;
    @FXML
    private Button stopButton;
    @FXML
    private Label statusLabel;
    @FXML
    private Label networkInfoLabel;

    private File selectedJar;
    private Process runningProcess;
    private String bundledJavaPath;

    @FXML
    public void initialize() {
        logToFile("Initialization started.");
        updateNetworkInfo();
        statusLabel.setText("状态: 已停止");
        
        try {
            extractEmbeddedJdk();
            logToFile("JDK extraction completed successfully.");
        } catch (Exception e) {
            logToFile("Failed to extract embedded JDK: " + e.getMessage());
            statusLabel.setText("初始化失败: " + e.getMessage());
            showErrorDialog("初始化失败", "无法提取嵌入的 JDK: " + e.getMessage());
        }
    }

    private void updateNetworkInfo() {
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            String ipAddress = localhost.getHostAddress();
            networkInfoLabel.setText(String.format("🌐 %s:10001", ipAddress));
        } catch (UnknownHostException e) {
            networkInfoLabel.setText("🌐 获取失败:10001");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleSelectJar() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择要运行的 JAR 文件");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JAR Files", "*.jar"));
        Stage stage = (Stage) jarPathLabel.getScene().getWindow();
        selectedJar = fileChooser.showOpenDialog(stage);

        if (selectedJar != null) {
            jarPathLabel.setText("📄 " + selectedJar.getName());
            jarPathLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #28a745; -fx-background-color: #d4edda; -fx-background-radius: 5; -fx-padding: 8 12 8 12; -fx-font-weight: bold;");
            startButton.setDisable(false);
        }
    }

    @FXML
    private void handleStart() {
        if (selectedJar == null) {
            statusLabel.setText("🔴 请先选择一个 JAR 文件");
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #dc3545; -fx-background-color: #f8d7da; -fx-background-radius: 12; -fx-padding: 4 12 4 12; -fx-font-weight: bold;");
            return;
        }

        if (runningProcess != null && runningProcess.isAlive()) {
            statusLabel.setText("🟡 程序已在运行中");
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #856404; -fx-background-color: #fff3cd; -fx-background-radius: 12; -fx-padding: 4 12 4 12; -fx-font-weight: bold;");
            return;
        }

        try {
            String javaPath;
            if (bundledJavaPath != null && !bundledJavaPath.isEmpty()) {
                javaPath = bundledJavaPath;
                logToFile("Using bundled JDK to start JAR: " + javaPath);
            } else {
                javaPath = findJavaExecutable();
                logToFile("Bundled JDK not available, falling back to system Java: " + javaPath);
            }
            
            // Set writable working directory
            String userHome = System.getProperty("user.home");
            File workingDir = new File(userHome + "/AppData/Local/JarStarter/logs");
            if (!workingDir.exists()) {
                workingDir.mkdirs();
                logToFile("Created writable working directory: " + workingDir.getAbsolutePath());
            }
            logToFile("Setting JAR working directory to: " + workingDir.getAbsolutePath());
            
            ProcessBuilder pb = new ProcessBuilder(javaPath, "-Dfile.encoding=UTF-8", "-jar", selectedJar.getAbsolutePath());
            pb.directory(workingDir);
            pb.redirectErrorStream(true);
            runningProcess = pb.start();
            logToFile("JAR process started successfully with PID: " + runningProcess.pid() + " (with -Dfile.encoding=UTF-8)");
            
            // Capture output
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(runningProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logToFile("JAR output: " + line);
                    }
                } catch (IOException e) {
                    logToFile("Error reading JAR output: " + e.getMessage());
                }
            }).start();
            
            // Monitor process exit
            new Thread(() -> {
                try {
                    int exitCode = runningProcess.waitFor();
                    logToFile("JAR process exited with code: " + exitCode);
                    Platform.runLater(() -> updateStatus("已停止 (退出码: " + exitCode + ")", false));
                } catch (InterruptedException e) {
                    logToFile("Process monitor interrupted: " + e.getMessage());
                }
            }).start();
            
            updateStatus("运行中", true);
        } catch (IOException e) {
            logToFile("Failed to start JAR: " + e.getMessage());
            statusLabel.setText("🔴 启动失败: " + e.getMessage());
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #dc3545; -fx-background-color: #f8d7da; -fx-background-radius: 12; -fx-padding: 4 12 4 12; -fx-font-weight: bold;");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleStop() {
        if (runningProcess != null && runningProcess.isAlive()) {
            runningProcess.destroy();
            try {
                runningProcess.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        updateStatus("已停止", false);
    }

    private String findJavaExecutable() {
        // 常见的JDK 8路径
        String[] possiblePaths = {
            "java", // 系统PATH中的java
            System.getProperty("java.home") + "\\bin\\java.exe",
            "C:\\Program Files\\Java\\jdk1.8.0_*\\bin\\java.exe",
            "C:\\Program Files (x86)\\Java\\jdk1.8.0_*\\bin\\java.exe",
            System.getProperty("java.home") + "\\bin\\java"
        };
        
        for (String path : possiblePaths) {
            if (path.contains("*")) {
                continue; // 跳过通配符路径，需要更复杂的查找
            }
            File javaFile = new File(path);
            if (path.equals("java") || (javaFile.exists() && javaFile.canExecute())) {
                return path;
            }
        }
        
        return "java"; // 默认返回系统PATH中的java
    }

    private void updateStatus(String status, boolean isRunning) {
        Platform.runLater(() -> {
            if (isRunning) {
                statusLabel.setText("🟢 " + status);
                statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #28a745; -fx-background-color: #d4edda; -fx-background-radius: 12; -fx-padding: 4 12 4 12; -fx-font-weight: bold;");
            } else {
                statusLabel.setText("🔴 " + status);
                statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #dc3545; -fx-background-color: #f8d7da; -fx-background-radius: 12; -fx-padding: 4 12 4 12; -fx-font-weight: bold;");
            }
            startButton.setDisable(isRunning);
            stopButton.setDisable(!isRunning);
        });
    }

    private void extractResourceDirectory(String resourcePath, File targetDir) throws IOException {
        ClassLoader loader = getClass().getClassLoader();
        Enumeration<URL> resources = loader.getResources(resourcePath.substring(1) + "/");
        int extractedCount = 0;
        
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            try (InputStream is = url.openStream()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                String line;
                while ((line = reader.readLine()) != null) {
                    // This is for directories, but since it's a flat list, we need a different approach
                    // Actually, for embedded resources, we need to list all possible paths
                }
            }
        } 
        
        // Revert to predefined list with recursive extraction
        extractResourceRecursive(resourcePath, targetDir, "");
    }
    
    private void extractResourceRecursive(String basePath, File targetDir, String currentPath) throws IOException {
        InputStream dirStream = getClass().getResourceAsStream(basePath + currentPath);
        if (dirStream != null) {
            logToFile("Extracting directory: " + basePath + currentPath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(dirStream));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                String fullPath = currentPath + line;
                if (line.endsWith("/")) {
                    File newDir = new File(targetDir, fullPath);
                    newDir.mkdirs();
                    logToFile("Created directory: " + newDir.getAbsolutePath());
                    extractResourceRecursive(basePath, targetDir, fullPath);
                } else {
                    InputStream fileStream = getClass().getResourceAsStream(basePath + fullPath);
                    if (fileStream != null) {
                        File targetFile = new File(targetDir, fullPath);
                        targetFile.getParentFile().mkdirs();
                        Files.copy(fileStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        logToFile("Extracted file: " + targetFile.getAbsolutePath());
                        if (fullPath.endsWith(".exe")) {
                            targetFile.setExecutable(true);
                        }
                    } else {
                        logToFile("Resource not found: " + basePath + fullPath);
                    }
                }
            }
        } else {
            logToFile("Directory stream not found for: " + basePath + currentPath);
        }
    }
    
    private void logToFile(String message) {
        String userHome = System.getProperty("user.home");
        File logFile = new File(userHome, "jar_starter_log.txt");
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            writer.println("[LOG] " + new Date() + ": " + message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void extractEmbeddedJdk() throws IOException {
        logToFile("Starting JDK extraction process.");
        
        String userHome = System.getProperty("user.home");
        File appDataDir = new File(userHome, "JarStarter");
        if (!appDataDir.exists()) {
            appDataDir.mkdirs();
            logToFile("Created app data directory: " + appDataDir.getAbsolutePath());
        }
        
        File jdkDir = new File(appDataDir, "jdk1.8.0_202");
        if (jdkDir.exists() && new File(jdkDir, "bin/java.exe").exists()) {
            logToFile("JDK already exists at: " + jdkDir.getAbsolutePath() + ". Skipping extraction.");
            bundledJavaPath = new File(jdkDir, "bin/java.exe").getAbsolutePath();
            return;
        }
        
        logToFile("JDK not found. Starting extraction to: " + jdkDir.getAbsolutePath());
        extractResourceDirectory("/embedded-jdk/", jdkDir);
        
        File extractedJava = new File(jdkDir, "bin/java.exe");
        if (extractedJava.exists()) {
            extractedJava.setExecutable(true);
            bundledJavaPath = extractedJava.getAbsolutePath();
            logToFile("JDK extraction successful. Java path: " + bundledJavaPath);
        } else {
            logToFile("JDK extraction failed: java.exe not found after extraction.");
            throw new IOException("JDK extraction failed: java.exe not found.");
        }
    }

    private void showErrorDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
} 