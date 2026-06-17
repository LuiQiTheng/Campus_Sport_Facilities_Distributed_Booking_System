import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main entry point for the Distributed Booking System.
 * 
 * Each instance runs as either a COORDINATOR or WORKER node.
 * - Coordinator (Node 1): Processes all booking writes, replicates to workers.
 * - Worker (Node 2): Forwards booking writes to coordinator, serves reads locally.
 *
 * Usage:
 *   Coordinator: java BookingServer --port 8081 --role coordinator --workers http://localhost:8082
 *   Worker:      java BookingServer --port 8082 --role worker --coordinator http://localhost:8081
 */
public class BookingServer {

    public static void main(String[] args) throws Exception {
        // Default configuration
        int port = 8081;
        String role = "coordinator";
        String coordinatorUrl = "";
        String workerUrls = "";

        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port":
                    port = Integer.parseInt(args[++i]);
                    break;
                case "--role":
                    role = args[++i].toLowerCase();
                    break;
                case "--coordinator":
                    coordinatorUrl = args[++i];
                    break;
                case "--workers":
                    workerUrls = args[++i];
                    break;
            }
        }

        // Initialize the booking data manager (each node has its own JSON file)
        BookingManager manager = new BookingManager("bookings_node" + port + ".json");

        // Initialize inter-node communicator
        NodeCommunicator communicator = new NodeCommunicator(coordinatorUrl, workerUrls);

        // Create HTTP server with thread pool for handling concurrent requests
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(10));

        // Register API handlers
        BookingHandler handler = new BookingHandler(manager, communicator, role, port);
        server.createContext("/api/bookings", handler::handleGetBookings);
        server.createContext("/api/book", handler::handleBook);
        server.createContext("/api/replicate", handler::handleReplicate);
        server.createContext("/api/status", handler::handleStatus);

        // Register static file handler (serves web UI)
        server.createContext("/", new StaticFileHandler());

        // Start the server
        server.start();

        // Start daily reset scheduler
        startDailyResetScheduler(manager, communicator, handler);

        // Print startup banner
        System.out.println("================================================");
        System.out.println("  DISTRIBUTED BOOKING SYSTEM - NODE STARTED");
        System.out.println("================================================");
        System.out.println("  Port : " + port);
        System.out.println("  Role : " + role.toUpperCase());
        if ("worker".equals(role)) {
            System.out.println("  Coordinator : " + coordinatorUrl);
        } else {
            System.out.println("  Workers     : " + (workerUrls.isEmpty() ? "none" : workerUrls));
        }
        System.out.println("  Data File   : bookings_node" + port + ".json");
        System.out.println("  URL         : http://localhost:" + port);
        System.out.println("================================================");
        System.out.println("  Server is ready. Waiting for requests...");
        System.out.println("================================================");
    }

    private static void startDailyResetScheduler(BookingManager manager, NodeCommunicator communicator, BookingHandler handler) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        ZonedDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.getZone());
        long initialDelayMs = Duration.between(now, nextMidnight).toMillis();
        long periodMs = TimeUnit.DAYS.toMillis(1);

        long diffMinutes = initialDelayMs / 1000 / 60;
        System.out.println("[SCHEDULER] Daily reset scheduled. First run in " + diffMinutes + " minutes (at midnight).");

        scheduler.scheduleAtFixedRate(() -> {
            try {
                if ("coordinator".equals(handler.getRole())) {
                    System.out.println("[SCHEDULER] Midnight reached. Executing daily bookings reset...");
                    manager.resetBookings();
                    communicator.replicateToWorkers(manager.getBookingsJson());
                } else {
                    System.out.println("[SCHEDULER] Midnight reached. Node is a worker, waiting for coordinator to replicate reset.");
                }
            } catch (Exception e) {
                System.err.println("[SCHEDULER ERROR] Failed to execute daily bookings reset: " + e.getMessage());
            }
        }, initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
    }
}
