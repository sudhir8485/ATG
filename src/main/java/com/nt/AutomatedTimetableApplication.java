package com.nt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;

@SpringBootApplication
public class AutomatedTimetableApplication {

    static int serverPort = 8765;

    public static void main(String[] args) {
        try {
            Files.createDirectories(Paths.get(System.getProperty("user.home"), ".atg"));
        } catch (Exception ignored) {}

        serverPort = findFreePort(8765);
        System.setProperty("server.port", String.valueOf(serverPort));

        // Required on Linux for AWT/SystemTray to work outside a display server
        System.setProperty("java.awt.headless", "false");

        SpringApplication.run(AutomatedTimetableApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        setupSystemTray();
        openBrowser("http://localhost:" + serverPort);
    }

    private void setupSystemTray() {
        if (!SystemTray.isSupported()) return;
        try {
            SystemTray tray = SystemTray.getSystemTray();
            Image icon = buildTrayIcon();
            TrayIcon trayIcon = new TrayIcon(icon, "ATG  (port " + serverPort + ")");
            trayIcon.setImageAutoSize(true);

            PopupMenu menu = new PopupMenu();

            MenuItem openItem = new MenuItem("Open Browser");
            openItem.addActionListener(e -> openBrowser("http://localhost:" + serverPort));

            MenuItem exitItem = new MenuItem("Exit ATG");
            exitItem.addActionListener(e -> System.exit(0));

            menu.add(openItem);
            menu.addSeparator();
            menu.add(exitItem);
            trayIcon.setPopupMenu(menu);
            trayIcon.addActionListener(e -> openBrowser("http://localhost:" + serverPort)); // double-click

            tray.add(trayIcon);
        } catch (Exception ignored) {}
    }

    static void openBrowser(String url) {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception e) {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(url));
                }
            } catch (Exception ignored) {}
        }
    }

    // Programmatic 16×16 tray icon: indigo rounded square with white "A" letter
    private Image buildTrayIcon() {
        int size = 64;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(79, 70, 229));      // indigo
        g.fillRoundRect(0, 0, size, size, 16, 16);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 36));
        FontMetrics fm = g.getFontMetrics();
        String text = "A";
        int tx = (size - fm.stringWidth(text)) / 2;
        int ty = (size - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(text, tx, ty);
        g.dispose();
        return img;
    }

    private static int findFreePort(int preferred) {
        try (ServerSocket s = new ServerSocket(preferred)) {
            s.setReuseAddress(true);
            return preferred;
        } catch (IOException e) {
            try (ServerSocket s = new ServerSocket(0)) {
                s.setReuseAddress(true);
                return s.getLocalPort();
            } catch (IOException ex) {
                return preferred;
            }
        }
    }
}
