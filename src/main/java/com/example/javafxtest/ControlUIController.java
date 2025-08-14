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
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import javafx.animation.ScaleTransition;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ControlUIController {

    // Removed jarPathLabel and file chooser
    @FXML
    private Button startButton;
    @FXML
    private Button stopButton;
    @FXML
    private Label statusLabel;
    @FXML
    private Label networkInfoLabel;

    private File embeddedJar;
    private Process runningProcess;
    private String bundledJavaPath;
    private ParallelTransition startButtonEffect;
    private PauseTransition startButtonEffectStopTimer;
    private final int servicePort = 12123; // 依据你的服务端口，可按需修改或读取配置
    private ScheduledExecutorService statusScheduler;
    private volatile Boolean lastPortOpen = null;

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
        
        try {
            embeddedJar = extractEmbeddedJar("/jar/myJar.jar");
            if (embeddedJar != null && embeddedJar.exists()) {
                startButton.setDisable(false);
                logToFile("Embedded JAR prepared at: " + embeddedJar.getAbsolutePath());
            } else {
                statusLabel.setText("未找到内置JAR");
                logToFile("Embedded JAR not found in resources or file system.");
            }
        } catch (IOException e) {
            statusLabel.setText("内置JAR准备失败: " + e.getMessage());
            logToFile("Failed to prepare embedded JAR: " + e.getMessage());
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

    // Removed handleSelectJar()

    @FXML
    private void handleStart() {
        if (embeddedJar == null || !embeddedJar.exists()) {
            statusLabel.setText("🔴 未找到内置JAR");
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #dc3545; -fx-background-color: #f8d7da; -fx-background-radius: 12; -fx-padding: 4 12 4 12; -fx-font-weight: bold;");
            return;
        }

        if (runningProcess != null && runningProcess.isAlive()) {
            statusLabel.setText("🟡 程序已在运行中");
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #856404; -fx-background-color: #fff3cd; -fx-background-radius: 12; -fx-padding: 4 12 4 12; -fx-font-weight: bold;");
            return;
        }

        try {
            startButton.setDisable(true);
            statusLabel.setText("🟡 启动中");
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #856404; -fx-background-color: #fff3cd; -fx-background-radius: 12; -fx-padding: 4 12 4 12; -fx-font-weight: bold;");
            playStartEffectTenSeconds();
            String javaPath;
            if (bundledJavaPath != null && !bundledJavaPath.isEmpty()) {
                javaPath = bundledJavaPath;
                logToFile("Using bundled JDK to start JAR: " + javaPath);
            } else {
                javaPath = findJavaExecutable();
                logToFile("Bundled JDK not available, falling back to system Java: " + javaPath);
            }
            
            String userHome = System.getProperty("user.home");
            File workingDir = new File(userHome + "/AppData/Local/JarStarter/logs");
            if (!workingDir.exists()) {
                workingDir.mkdirs();
                logToFile("Created writable working directory: " + workingDir.getAbsolutePath());
            }
            logToFile("Setting JAR working directory to: " + workingDir.getAbsolutePath());
            
            ProcessBuilder pb = new ProcessBuilder(javaPath, "-Dfile.encoding=UTF-8", "-Dspring.output.ansi.enabled=never", "-jar", embeddedJar.getAbsolutePath());
            pb.directory(workingDir);
            pb.redirectErrorStream(true);
            // Disable ANSI colors via environment variables as an extra safeguard
            pb.environment().put("SPRING_OUTPUT_ANSI_ENABLED", "never");
            pb.environment().put("NO_COLOR", "1");
            pb.environment().put("CLICOLOR", "0");
            pb.environment().put("ANSICON", "");
            runningProcess = pb.start();
            logToFile("JAR process started successfully with PID: " + runningProcess.pid() + " (with -Dfile.encoding=UTF-8)");
            
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(runningProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String cleaned = stripAnsi(line);
                        logToFile("JAR output: " + cleaned);
                    }
                } catch (IOException e) {
                    logToFile("Error reading JAR output: " + e.getMessage());
                }
            }).start();
            
            new Thread(() -> {
                try {
                    int exitCode = runningProcess.waitFor();
                    logToFile("JAR process exited with code: " + exitCode);
                    // 不直接置为已停止，改由端口检测来最终判定，防止子进程仍在运行
                } catch (InterruptedException e) {
                    logToFile("Process monitor interrupted: " + e.getMessage());
                }
            }).start();
            
            updateStatus("运行中", true);
            startStatusMonitor();
        } catch (IOException e) {
            stopStartEffect();
            startButton.setDisable(false);
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
        stopStatusMonitor();
        updateStatus("已停止", false);
    }

    @FXML
    private void handleDownloadLog() {
        try {
            String userHome = System.getProperty("user.home");
            File logFile = new File(userHome, "jar_starter_log.txt");
            if (!logFile.exists()) {
                showErrorDialog("下载失败", "未找到日志文件：" + logFile.getAbsolutePath());
                return;
            }

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("保存日志文件");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("文本文件", "*.txt"));
            fileChooser.setInitialFileName("jar_starter_log.txt");
            Stage stage = (Stage) statusLabel.getScene().getWindow();
            File dest = fileChooser.showSaveDialog(stage);
            if (dest == null) {
                return;
            }
            dest.getParentFile().mkdirs();
            Files.copy(logFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("下载成功");
            alert.setHeaderText(null);
            alert.setContentText("日志已保存到：" + dest.getAbsolutePath());
            alert.showAndWait();
        } catch (Exception ex) {
            showErrorDialog("下载失败", ex.getMessage());
            logToFile("Download log failed: " + ex.getMessage());
        }
    }

    private String stripAnsi(String input) {
        if (input == null) {
            return null;
        }
        // Remove ANSI escape sequences like \u001B[0;39m etc.
        return input.replaceAll("\u001B\\[[;\\d]*m", "");
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

    private File extractEmbeddedJar(String resourcePath) throws IOException {
        // 1) 如果之前已解压过，直接使用
        String userHome = System.getProperty("user.home");
        File workDir = new File(userHome + "/AppData/Local/JarStarter/app");
        if (!workDir.exists()) {
            workDir.mkdirs();
            logToFile("Created extraction directory: " + workDir.getAbsolutePath());
        } else {
            logToFile("Extraction directory already exists: " + workDir.getAbsolutePath());
        }
        File out = new File(workDir, "myJar.jar");
        if (out.exists()) {
            logToFile("Found existing extracted JAR: " + out.getAbsolutePath());
            return out;
        } else {
            logToFile("No existing extracted JAR found.");
        }

        // 2) 尝试从类路径资源复制
        logToFile("Attempting to load from classpath resource: " + resourcePath);
        InputStream in = getClass().getResourceAsStream(resourcePath);
        if (in != null) {
            logToFile("Classpath resource found, copying to: " + out.getAbsolutePath());
            Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
            in.close();
            if (out.exists()) {
                logToFile("Successfully extracted from classpath.");
                return out;
            } else {
                logToFile("Copy from classpath succeeded but file does not exist after copy.");
            }
        } else {
            logToFile("Classpath resource not found: " + resourcePath);
        }

        // 3) 回退：从文件系统的 target/myJar.jar 复制（便于开发期使用）
        logToFile("Attempting fallback from file system: target/myJar.jar");
        File devJar = new File("target/myJar.jar");
        if (devJar.exists()) {
            logToFile("File system JAR found, copying to: " + out.getAbsolutePath());
            Files.copy(devJar.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (out.exists()) {
                logToFile("Successfully copied from file system.");
                return out;
            } else {
                logToFile("Copy from file system succeeded but file does not exist after copy.");
            }
        } else {
            logToFile("File system JAR not found at target/myJar.jar");
        }

        // 4) 找不到
        logToFile("No JAR found in any location.");
        return null;
    }

    private void showErrorDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void playStartEffectTenSeconds() {
        try {
            if (startButtonEffect != null) {
                startButtonEffect.stop();
            }
            if (startButtonEffectStopTimer != null) {
                startButtonEffectStopTimer.stop();
            }
            DropShadow glow = new DropShadow();
            glow.setColor(Color.web("#28a745"));
            glow.setRadius(20);
            startButton.setEffect(glow);

            ScaleTransition scale = new ScaleTransition(Duration.millis(600), startButton);
            scale.setFromX(1.0);
            scale.setFromY(1.0);
            scale.setToX(1.12);
            scale.setToY(1.12);
            scale.setAutoReverse(true);
            scale.setCycleCount(ParallelTransition.INDEFINITE);

            FadeTransition fade = new FadeTransition(Duration.millis(600), startButton);
            fade.setFromValue(1.0);
            fade.setToValue(0.75);
            fade.setAutoReverse(true);
            fade.setCycleCount(ParallelTransition.INDEFINITE);

            startButtonEffect = new ParallelTransition(scale, fade);
            startButtonEffect.play();

            startButtonEffectStopTimer = new PauseTransition(Duration.seconds(10));
            startButtonEffectStopTimer.setOnFinished(ev -> stopStartEffect());
            startButtonEffectStopTimer.play();
        } catch (Exception ignore) {
        }
    }

    private void stopStartEffect() {
        try {
            if (startButtonEffect != null) {
                startButtonEffect.stop();
                startButtonEffect = null;
            }
            if (startButtonEffectStopTimer != null) {
                startButtonEffectStopTimer.stop();
                startButtonEffectStopTimer = null;
            }
            startButton.setEffect(null);
            startButton.setScaleX(1.0);
            startButton.setScaleY(1.0);
            startButton.setOpacity(1.0);
        } catch (Exception ignore) {
        }
    }

    private void startStatusMonitor() {
        stopStatusMonitor();
        statusScheduler = Executors.newSingleThreadScheduledExecutor();
        statusScheduler.scheduleAtFixedRate(() -> {
            boolean open = isPortOpen("127.0.0.1", servicePort, 800);
            if (lastPortOpen == null || lastPortOpen != open) {
                lastPortOpen = open;
                Platform.runLater(() -> {
                    if (open) {
                        updateStatus("运行中", true);
                    } else {
                        updateStatus("已停止", false);
                    }
                });
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    private void stopStatusMonitor() {
        if (statusScheduler != null) {
            statusScheduler.shutdownNow();
            statusScheduler = null;
            lastPortOpen = null;
        }
    }

    private boolean isPortOpen(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
} 