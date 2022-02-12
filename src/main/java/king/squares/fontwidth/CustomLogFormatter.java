package king.squares.fontwidth;

import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class CustomLogFormatter extends Formatter {
  @Override
  public String format(final LogRecord record) {
    return record.getLevel().intValue() > Level.INFO.intValue() ? record.getLevel().getName() : "" + ": " + record.getMessage() + "\n";
  }
}
