package io.github.m_moris.azure.logback;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.ClassicConstants;
import ch.qos.logback.core.joran.spi.JoranException;

public class AppendTest1 {

    //private static final Logger logger = LoggerFactory.getLogger(TestAppenderTest.class);

    @BeforeEach
    public void prepare() throws IOException {
        AzureConfig.ReadAndSetDefault();
    }

    @Test
    public void test1() throws JoranException, InterruptedException {
        System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, "logback-ut1.xml");
        var logger = LoggerFactory.getLogger(AppendTest1.class);
        logger.trace("trace");
        logger.info("info");
        logger.warn("warn");
        logger.error("error", new RuntimeException("test"));
        Thread.sleep(1000);
    }

}
