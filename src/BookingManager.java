import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Manages all booking data with synchronized access to prevent race conditions.
 * Implements First Come First Serve (FCFS) scheduling.
 * Each user is limited to a maximum of 2 slots per court per day.
 */
public class BookingManager {

    private final String dataFile;
    private Map<String, Map<String, String>> bookings;

    // All available courts
    private static final String[] COURTS = {
        "badminton_court", "basketball_court", "football_court", "volleyball_court"
    };

    // Time slots: 8:00 AM to 7:00 PM (last slot starts at 19:00, ends at 20:00)
    private static final String[] TIME_SLOTS = {
        "08:00", "09:00", "10:00", "11:00", "12:00", "13:00",
        "14:00", "15:00", "16:00", "17:00", "18:00", "19:00"
    };

    // Maximum bookings per user per court
    private static final int MAX_SLOTS_PER_USER_PER_COURT = 2;

    public BookingManager(String dataFile) {
        this.dataFile = dataFile;
        loadBookings();
    }

    /**
     * Initialize empty booking slots for all courts and times.
     */
    private void initializeEmptyBookings() {
        bookings = new LinkedHashMap<>();
        for (String court : COURTS) {
            Map<String, String> slots = new LinkedHashMap<>();
            for (String time : TIME_SLOTS) {
                slots.put(time, null);
            }
            bookings.put(court, slots);
        }
        saveBookings();
        System.out.println("[INIT] Created fresh booking data in " + dataFile);
    }

    /**
     * Load bookings from JSON file, or initialize if file doesn't exist.
     */
    public void loadBookings() {
        File file = new File(dataFile);
        if (!file.exists()) {
            initializeEmptyBookings();
            return;
        }
        try {
            String content = new String(Files.readAllBytes(file.toPath()), "UTF-8");
            bookings = JsonHelper.fromJson(content);
            System.out.println("[LOAD] Loaded booking data from " + dataFile);
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to load bookings: " + e.getMessage());
            initializeEmptyBookings();
        }
    }

    /**
     * Save current bookings to JSON file.
     */
    private void saveBookings() {
        try (FileWriter writer = new FileWriter(dataFile)) {
            writer.write(JsonHelper.toJson(bookings));
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to save bookings: " + e.getMessage());
        }
    }

    /**
     * CRITICAL: synchronized method to prevent double booking (race condition).
     * Implements First Come First Serve - the first request to acquire the lock wins.
     * Also enforces the maximum 2 slots per user per court rule.
     *
     * @return JSON response string with success status and message
     */
    public synchronized String bookSlot(String court, String time, String userName) {
        // Validate court exists
        if (!bookings.containsKey(court)) {
            return buildResponse(false, "Invalid court.");
        }

        Map<String, String> slots = bookings.get(court);

        // Validate time slot exists
        if (!slots.containsKey(time)) {
            return buildResponse(false, "Invalid time slot.");
        }

        // FIRST COME FIRST SERVE CHECK: If slot is already taken, reject
        if (slots.get(time) != null) {
            return buildResponse(false,
                "Sorry, this slot was already booked by " + slots.get(time) +
                "! Please refresh the page to see the latest bookings.");
        }

        // CHECK MAX 2 SLOTS PER USER PER COURT
        int userSlotCount = 0;
        for (String bookedUser : slots.values()) {
            if (userName.equalsIgnoreCase(bookedUser)) {
                userSlotCount++;
            }
        }
        if (userSlotCount >= MAX_SLOTS_PER_USER_PER_COURT) {
            return buildResponse(false,
                "You have already booked the maximum of " + MAX_SLOTS_PER_USER_PER_COURT +
                " slots for " + formatCourtName(court) + " today.");
        }

        // BOOK THE SLOT - First thread to reach here wins (FCFS)
        slots.put(time, userName);
        saveBookings();

        System.out.println("[BOOKED] " + formatCourtName(court) + " at " + time + " by " + userName);

        return buildResponse(true,
            "Successfully booked " + formatCourtName(court) + " at " + time +
            "! ");
    }

    /**
     * Get current bookings as JSON string.
     */
    public synchronized String getBookingsJson() {
        return JsonHelper.toJson(bookings);
    }

    /**
     * Replace local bookings with replicated data from coordinator.
     * Used by worker nodes to stay in sync.
     */
    public synchronized void replaceBookings(String jsonData) {
        try {
            bookings = JsonHelper.fromJson(jsonData);
            saveBookings();
            System.out.println("[REPLICATED] Updated booking data from coordinator.");
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to process replicated data: " + e.getMessage());
        }
    }

    private String formatCourtName(String court) {
        return court.replace("_", " ").substring(0, 1).toUpperCase() +
               court.replace("_", " ").substring(1);
    }

    private String buildResponse(boolean success, String message) {
        return "{\"success\": " + success + ", \"message\": \"" +
               JsonHelper.escapeJson(message) + "\"}";
    }
}
