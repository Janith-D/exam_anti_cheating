package com.example.anti_cheating_backend.service;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

@Service
public class ExternalServiceManager {

    private static final Logger logger = LoggerFactory.getLogger(ExternalServiceManager.class);

    @Value("${external.ml-service.path:#{null}}")
    private String mlServicePath;

    @Value("${external.desktop-monitor.path:#{null}}")
    private String desktopMonitorPath;

    @Value("${external.ml-service.enabled:true}")
    private boolean mlServiceEnabled;

    @Value("${external.desktop-monitor.enabled:true}")
    private boolean desktopMonitorEnabled;

    private Process mlServiceProcess;
    private Process desktopMonitorProcess;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("========================================");
        logger.info("Starting external services...");
        logger.info("========================================");

        if (mlServiceEnabled) {
            startMlService();
        } else {
            logger.info("ML Service auto-start is disabled");
        }

        if (desktopMonitorEnabled) {
            startDesktopMonitor();
        } else {
            logger.info("Desktop Monitor auto-start is disabled");
        }
    }

    private void startMlService() {
        try {
            String path = resolvePath(mlServicePath, "ml-service");
            if (path == null) {
                logger.warn("ML Service path not found. Set 'external.ml-service.path' in application.properties");
                return;
            }

            File dir = new File(path);
            File apiScript = new File(dir, "src/api.py");
            if (!apiScript.exists()) {
                logger.warn("ML Service script not found: {}", apiScript.getAbsolutePath());
                return;
            }

            logger.info("Starting ML Service from: {}", dir.getAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder("python", "src/api.py");
            pb.directory(dir);
            pb.environment().put("PYTHONPATH", new File(dir, "src").getAbsolutePath());
            pb.redirectErrorStream(true);
            pb.inheritIO();

            mlServiceProcess = pb.start();
            logger.info("ML Service started (PID: {})", mlServiceProcess.pid());

        } catch (IOException e) {
            logger.error("Failed to start ML Service: {}", e.getMessage());
        }
    }

    private void startDesktopMonitor() {
        try {
            String path = resolvePath(desktopMonitorPath, "desktop-monitor");
            if (path == null) {
                logger.warn("Desktop Monitor path not found. Set 'external.desktop-monitor.path' in application.properties");
                return;
            }

            File dir = new File(path);
            File appScript = new File(dir, "app.py");
            if (!appScript.exists()) {
                logger.warn("Desktop Monitor script not found: {}", appScript.getAbsolutePath());
                return;
            }

            logger.info("Starting Desktop Monitor from: {}", dir.getAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder("python", "app.py");
            pb.directory(dir);
            pb.redirectErrorStream(true);
            pb.inheritIO();

            desktopMonitorProcess = pb.start();
            logger.info("Desktop Monitor started (PID: {})", desktopMonitorProcess.pid());

        } catch (IOException e) {
            logger.error("Failed to start Desktop Monitor: {}", e.getMessage());
        }
    }

    private String resolvePath(String configuredPath, String defaultSubdir) {
        // Use configured path if provided
        if (configuredPath != null && !configuredPath.isBlank()) {
            File dir = new File(configuredPath);
            if (dir.isDirectory()) {
                return dir.getAbsolutePath();
            }
        }

        // Auto-detect: look for sibling directory relative to backend
        File backendDir = new File(System.getProperty("user.dir"));
        File parentDir = backendDir.getParentFile();
        if (parentDir != null) {
            File siblingDir = new File(parentDir, defaultSubdir);
            if (siblingDir.isDirectory()) {
                return siblingDir.getAbsolutePath();
            }
        }

        // Also check if we're already in the project root
        File subDir = new File(backendDir, defaultSubdir);
        if (subDir.isDirectory()) {
            return subDir.getAbsolutePath();
        }

        return null;
    }

    @PreDestroy
    public void onShutdown() {
        logger.info("Shutting down external services...");

        stopProcess(mlServiceProcess, "ML Service");
        stopProcess(desktopMonitorProcess, "Desktop Monitor");
    }

    private void stopProcess(Process process, String name) {
        if (process != null && process.isAlive()) {
            logger.info("Stopping {}...", name);
            process.destroy();
            try {
                boolean exited = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                if (!exited) {
                    logger.warn("{} did not stop gracefully, force killing...", name);
                    process.destroyForcibly();
                }
                logger.info("{} stopped", name);
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
    }
}
