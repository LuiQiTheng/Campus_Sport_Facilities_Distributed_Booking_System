import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.nio.file.*;

/**
 * Serves static files (HTML, CSS, JS) from the web/ directory.
 * This allows each node to serve its own copy of the frontend UI.
 */
public class StaticFileHandler implements HttpHandler {

    private static final String WEB_DIR = "web";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        // Default to index.html
        if (path.equals("/") || path.equals("/index.html")) {
            path = "/index.html";
        }

        File file = new File(WEB_DIR + path);

        if (!file.exists() || file.isDirectory()) {
            file = new File(WEB_DIR + "/index.html");
        }

        if (!file.exists()) {
            String notFound = "404 - File Not Found";
            exchange.sendResponseHeaders(404, notFound.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(notFound.getBytes());
            }
            return;
        }

        // Set appropriate Content-Type header
        String contentType = getContentType(path);
        exchange.getResponseHeaders().set("Content-Type", contentType);

        byte[] bytes = Files.readAllBytes(file.toPath());
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String getContentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=UTF-8";
        if (path.endsWith(".css"))  return "text/css; charset=UTF-8";
        if (path.endsWith(".js"))   return "application/javascript; charset=UTF-8";
        if (path.endsWith(".png"))  return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".svg"))  return "image/svg+xml";
        if (path.endsWith(".ico"))  return "image/x-icon";
        return "text/plain; charset=UTF-8";
    }
}
