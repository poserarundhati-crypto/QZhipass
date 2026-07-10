package org.microsoft.qintelipass.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public class ExpirationTimeHelper {
    private ExpirationTimeHelper() {
    }

    public static Instant getNextDayTime(){
        ZoneId zoneId = ZoneId.systemDefault();
        return LocalDate.now(zoneId).plusDays(1).atStartOfDay(zoneId).toInstant();
    }
}
