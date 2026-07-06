package org.microsoft.qintelipass.util;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public class QZhiPasswordPattern {
    private static final Pattern USER_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");
    public static final String REQUIREMENT_MESSAGE = "Password must be at least 8 characters and include uppercase, lowercase, number, and special character.";
    public static class Generator {
        private static final String LOWERCASE = "qwertyuiopasdfghjklzxcvbnm";
        private static final String UPPERCASE = "QWERTYUIOPASDFGHJKLZXCVBNM";
        private static final String DIGITS = "1234567890";
        private static final String SPECIAL = "!@#$%^&*";
        private static final String ALL = LOWERCASE + UPPERCASE + DIGITS + SPECIAL;
        private static final int PASSWORD_LENGTH = 8;
        private final StringBuilder stringBuilder = new StringBuilder();

        public String generate(){
            stringBuilder.setLength(0);
            stringBuilder.append(randomChar(LOWERCASE));
            stringBuilder.append(randomChar(UPPERCASE));
            stringBuilder.append(randomChar(DIGITS));
            stringBuilder.append(randomChar(SPECIAL));
            while (stringBuilder.length() < PASSWORD_LENGTH) {
                stringBuilder.append(randomChar(ALL));
            }
            return stringBuilder.toString();
        }

        private char randomChar(String characters) {
            return characters.charAt(ThreadLocalRandom.current().nextInt(characters.length()));
        }
    }

    public static boolean validate(String password){
        return password != null && USER_PATTERN.matcher(password).matches();
    }
}
