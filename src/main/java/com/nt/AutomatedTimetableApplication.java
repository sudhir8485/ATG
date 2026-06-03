package com.nt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;

@SpringBootApplication
public class AutomatedTimetableApplication {

    public static void main(String[] args) {
        // Ensure H2 data directory exists before Spring connects to the database
        try {
            Files.createDirectories(Paths.get(System.getProperty("user.home"), ".atg"));
        } catch (Exception ignored) {}

        SpringApplication.run(AutomatedTimetableApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void openBrowser() {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(new URI("http://localhost:8080"));
            } catch (Exception ignored) {}
        }
    }
}
