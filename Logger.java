import java.io.PrintStream;
import java.time.LocalDateTime;

public class Logger {
    private final PrintStream _target;

    public Logger(PrintStream target) {
        _target = target;
    }

    public void log(String format, Object... args) {
        var logMessage = String.format(format, args);
        _target.println(String.format("%s :- %s", LocalDateTime.now().toString(), logMessage));
    }
}
