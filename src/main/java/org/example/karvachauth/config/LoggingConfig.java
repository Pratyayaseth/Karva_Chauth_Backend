package org.example.karvachauth.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import jakarta.annotation.PostConstruct;
import org.example.karvachauth.serviceImple.DbLogAppender;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoggingConfig {

    @Autowired
    private DbLogAppender dbLogAppender;

    @PostConstruct
    public void init() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        dbLogAppender.setContext(context);
        dbLogAppender.start();
        rootLogger.addAppender(dbLogAppender);
    }
}
