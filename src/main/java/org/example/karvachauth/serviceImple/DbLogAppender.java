package org.example.karvachauth.serviceImple;


import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import org.example.karvachauth.entity.AppErrorLog;
//import org.example.karvachauth.repository.AppErrorLogRepository;
import org.example.karvachauth.repository.AppErrorLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DbLogAppender extends AppenderBase<ILoggingEvent> {

    @Autowired
    private AppErrorLogRepository errorLogRepository;

    @Override
    protected void append(ILoggingEvent event) {
        try {
            if (!event.getLevel().toString().equals("ERROR") && !event.getLevel().toString().equals("WARN")) {
                return;
            }

            String stackTrace = "";
            IThrowableProxy throwableProxy = event.getThrowableProxy();
            if (throwableProxy != null) {
                stackTrace = throwableProxy.getClassName() + ": " + throwableProxy.getMessage();
            }

            String message = event.getFormattedMessage();
            String phone = extractPhone(message);

            AppErrorLog log = AppErrorLog.builder()
                    .level(event.getLevel().toString())
                    .loggerName(event.getLoggerName())
                    .message(message.length() > 500 ? message.substring(0, 500) : message)
                    .stackTrace(stackTrace.length() > 2000 ? stackTrace.substring(0, 2000) : stackTrace)
                    .phone(phone)
                    .build();

            errorLogRepository.save(log);
        } catch (Exception e) {
            // Silent-fail — logging-khud-crash-nahi-honi-chahiye
        }
    }

    private String extractPhone(String message) {
        if (message == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("phone=(\\d{10,15})").matcher(message);
        return m.find() ? m.group(1) : null;
    }
}

