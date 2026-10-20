package cat.iesesteveterradas.suport;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Captura els missatges de log d'una classe (Logback) mentre està obert.
 * Permet comprovar que un error s'ha registrat amb el nivell i el text correctes.
 */
public final class LogCapturat implements AutoCloseable {

    private final Logger logger;
    private final Level nivellOriginal;
    private final boolean additiuOriginal;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCapturat(Class<?> classe) {
        logger = (Logger) LoggerFactory.getLogger(classe);
        nivellOriginal = logger.getLevel();
        additiuOriginal = logger.isAdditive();
        logger.setLevel(Level.DEBUG);  // durant la captura es registren tots els nivells...
        logger.setAdditive(false);     // ...però no s'escriuen per consola (els errors provocats als tests embrutarien la sortida)
        appender.start();
        logger.addAppender(appender);
    }

    public static LogCapturat de(Class<?> classe) {
        return new LogCapturat(classe);
    }

    /** Missatges (ja amb els {} substituïts) d'un nivell concret. */
    public List<String> missatges(Level nivell) {
        return appender.list.stream()
                .filter(e -> e.getLevel().equals(nivell))
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList());
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        logger.setLevel(nivellOriginal);
        logger.setAdditive(additiuOriginal);
    }
}
