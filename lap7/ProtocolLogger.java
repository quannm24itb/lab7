import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Singleton quản lý nhật ký giao thức và phát sự kiện cho UI.
 */
public class ProtocolLogger {
    public interface LogListener {
        void onNewLog(ProtocolLogEntry entry);
        void onLogsCleared();
    }

    private static final ProtocolLogger instance = new ProtocolLogger();
    private final List<ProtocolLogEntry> logs = Collections.synchronizedList(new ArrayList<>());
    private final List<LogListener> listeners = Collections.synchronizedList(new ArrayList<>());

    private ProtocolLogger() {}

    public static ProtocolLogger getInstance() {
        return instance;
    }

    public void addListener(LogListener listener) {
        listeners.add(listener);
    }

    public void removeListener(LogListener listener) {
        listeners.remove(listener);
    }

    public void log(String type, String direction, String message) {
        ProtocolLogEntry entry = new ProtocolLogEntry(type, direction, message);
        logs.add(entry);
        try {
            DatabaseManager.saveLog(type, direction, message);
        } catch (Exception ignored) {}
        for (LogListener l : listeners) {
            try {
                l.onNewLog(entry);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public List<ProtocolLogEntry> getLogs() {
        return new ArrayList<>(logs);
    }

    public List<ProtocolLogEntry> getLogsByType(String type) {
        List<ProtocolLogEntry> result = new ArrayList<>();
        synchronized (logs) {
            for (ProtocolLogEntry entry : logs) {
                if ("ALL".equalsIgnoreCase(type) || entry.getType().equalsIgnoreCase(type)) {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    public void clear() {
        logs.clear();
        for (LogListener l : listeners) {
            try {
                l.onLogsCleared();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
