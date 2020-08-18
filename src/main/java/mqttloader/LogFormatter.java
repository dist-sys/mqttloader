package mqttloader;

import java.text.SimpleDateFormat;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LogFormatter extends Formatter {

    @Override
    public String format(LogRecord record) {
        return String.format(
                "[%s] %s %s#%s %s%n",
                record.getLevel().getName(),
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(record.getMillis()),
                record.getSourceClassName(),
                record.getSourceMethodName(),
                record.getMessage()
        );
    }
}
