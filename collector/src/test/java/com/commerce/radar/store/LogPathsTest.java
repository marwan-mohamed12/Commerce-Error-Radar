package com.commerce.radar.store;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogPathsTest {

    @Test
    void windowsSeparatorsAndDriveCaseDoNotSplitASession() {
        assertEquals(
                "d:/hybris/log/tomcat/console-20260811.log",
                LogPaths.normalize("D:\\hybris\\log\\tomcat\\console-20260811.log")
        );
        assertEquals(
                LogPaths.normalize("D:/hybris/log/tomcat/console-20260811.log"),
                LogPaths.normalize("d:\\hybris\\log\\tomcat\\console-20260811.log")
        );
    }
}
