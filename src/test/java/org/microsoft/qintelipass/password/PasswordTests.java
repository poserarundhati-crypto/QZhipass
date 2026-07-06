package org.microsoft.qintelipass.password;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.microsoft.qintelipass.util.QZhiPasswordPattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class PasswordTests {
    @Test
    public void generatePasswords(){
        QZhiPasswordPattern.Generator passwordPattern = new QZhiPasswordPattern.Generator();
        for (int i = 0; i < 100; i++) {
            String s = passwordPattern.generate();
            assertTrue(QZhiPasswordPattern.validate(s));
            log.info("password: {}, {}", s, QZhiPasswordPattern.validate(s));
        }
    }

    @Test
    public void validateRequiredPasswordPattern(){
        assertTrue(QZhiPasswordPattern.validate("12345@Abc"));
        assertFalse(QZhiPasswordPattern.validate("12345678"));
        assertFalse(QZhiPasswordPattern.validate("12345@abc"));
        assertFalse(QZhiPasswordPattern.validate("12345@ABC"));
        assertFalse(QZhiPasswordPattern.validate("12345Abc"));
        assertFalse(QZhiPasswordPattern.validate("1@Abc"));
    }
}
