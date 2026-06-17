import java.util.*;

/**
 * Simple JSON helper for parsing and serializing booking data.
 * Handles our specific structure: Map<String, Map<String, String>>
 * No external libraries needed.
 */
public class JsonHelper {

    /**
     * Serialize booking data to JSON string.
     */
    public static String toJson(Map<String, Map<String, String>> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        Iterator<Map.Entry<String, Map<String, String>>> courtIter = data.entrySet().iterator();
        while (courtIter.hasNext()) {
            Map.Entry<String, Map<String, String>> courtEntry = courtIter.next();
            sb.append("  \"").append(escapeJson(courtEntry.getKey())).append("\": {\n");
            Iterator<Map.Entry<String, String>> slotIter = courtEntry.getValue().entrySet().iterator();
            while (slotIter.hasNext()) {
                Map.Entry<String, String> slotEntry = slotIter.next();
                sb.append("    \"").append(escapeJson(slotEntry.getKey())).append("\": ");
                if (slotEntry.getValue() == null) {
                    sb.append("null");
                } else {
                    sb.append("\"").append(escapeJson(slotEntry.getValue())).append("\"");
                }
                if (slotIter.hasNext()) sb.append(",");
                sb.append("\n");
            }
            sb.append("  }");
            if (courtIter.hasNext()) sb.append(",");
            sb.append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Parse JSON string to booking data map.
     * Supports our specific 2-level nested structure.
     */
    public static Map<String, Map<String, String>> fromJson(String json) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        int[] pos = {0};
        skipWhitespace(json, pos);
        expect(json, pos, '{');

        while (true) {
            skipWhitespace(json, pos);
            if (pos[0] < json.length() && json.charAt(pos[0]) == '}') { pos[0]++; break; }

            String courtKey = parseString(json, pos);
            skipWhitespace(json, pos);
            expect(json, pos, ':');
            skipWhitespace(json, pos);

            Map<String, String> slots = new LinkedHashMap<>();
            expect(json, pos, '{');

            while (true) {
                skipWhitespace(json, pos);
                if (pos[0] < json.length() && json.charAt(pos[0]) == '}') { pos[0]++; break; }

                String timeKey = parseString(json, pos);
                skipWhitespace(json, pos);
                expect(json, pos, ':');
                skipWhitespace(json, pos);

                String value = parseValue(json, pos);
                slots.put(timeKey, value);

                skipWhitespace(json, pos);
                if (pos[0] < json.length() && json.charAt(pos[0]) == ',') { pos[0]++; }
            }

            result.put(courtKey, slots);
            skipWhitespace(json, pos);
            if (pos[0] < json.length() && json.charAt(pos[0]) == ',') { pos[0]++; }
        }

        return result;
    }

    /**
     * Extract a simple string value from a flat JSON object.
     * Used for parsing booking request bodies.
     */
    public static String extractValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) return null;
        int colonIndex = json.indexOf(":", keyIndex + searchKey.length());
        if (colonIndex == -1) return null;

        int i = colonIndex + 1;
        while (i < json.length() && json.charAt(i) == ' ') i++;
        if (i >= json.length()) return null;

        if (json.charAt(i) == '"') {
            int start = i + 1;
            int end = json.indexOf('"', start);
            if (end == -1) return null;
            return json.substring(start, end);
        }
        return null;
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    // --- Internal parsing helpers ---

    private static void skipWhitespace(String s, int[] pos) {
        while (pos[0] < s.length() && Character.isWhitespace(s.charAt(pos[0]))) {
            pos[0]++;
        }
    }

    private static void expect(String s, int[] pos, char c) {
        skipWhitespace(s, pos);
        if (pos[0] < s.length() && s.charAt(pos[0]) == c) {
            pos[0]++;
        }
    }

    private static String parseString(String s, int[] pos) {
        skipWhitespace(s, pos);
        if (pos[0] >= s.length() || s.charAt(pos[0]) != '"') return "";
        pos[0]++; // skip opening quote
        StringBuilder sb = new StringBuilder();
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);
            if (c == '\\' && pos[0] + 1 < s.length()) {
                pos[0]++;
                sb.append(s.charAt(pos[0]));
            } else if (c == '"') {
                pos[0]++; // skip closing quote
                return sb.toString();
            } else {
                sb.append(c);
            }
            pos[0]++;
        }
        return sb.toString();
    }

    private static String parseValue(String s, int[] pos) {
        skipWhitespace(s, pos);
        if (pos[0] >= s.length()) return null;
        if (s.charAt(pos[0]) == '"') {
            return parseString(s, pos);
        }
        // Check for null
        if (s.startsWith("null", pos[0])) {
            pos[0] += 4;
            return null;
        }
        return null;
    }
}
