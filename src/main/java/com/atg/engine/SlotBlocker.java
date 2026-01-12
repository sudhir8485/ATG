package com.atg.engine;

import com.atg.controller.InstituteConfigLoader;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class SlotBlocker {

    public static Set<Integer> getBlockedSlots(
            InstituteConfigLoader.LoadedInstituteConfig config,
            String startTime) {

        Set<Integer> blocked = new HashSet<>();

        DateTimeFormatter fmt =
                DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

        LocalTime dayStart =
                LocalTime.parse(normalize(startTime), fmt);

        for (InstituteConfigLoader.BreakSlot b : config.breaks) {

            LocalTime breakStart =
                    LocalTime.parse(normalize(b.startTime), fmt);

            long minsFromStart =
                    java.time.Duration.between(dayStart, breakStart).toMinutes();

            int slotIndex =
                    (int) (minsFromStart / config.lectureDuration) + 1;

            blocked.add(slotIndex);
        }

        return blocked;
    }

    // 🔥 SAME LOGIC AS CONTROLLER (CRITICAL)
    private static String normalize(String time) {
        time = time.trim().replace(".", ":").toUpperCase();
        time = time.replaceAll("(?i)(AM|PM)", " $1");
        return time.replaceAll("\\s+", " ").trim();
    }
}
