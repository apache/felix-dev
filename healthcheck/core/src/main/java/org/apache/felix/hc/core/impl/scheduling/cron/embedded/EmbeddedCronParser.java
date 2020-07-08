/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.felix.hc.core.impl.scheduling.cron.embedded;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Pattern;

/**
 * Used to parse cron expressions.
 * <p>
 *
 * The pattern comprises a list of six single space-separated fields: second,
 * minute, hour, day, month, weekday. <br/>
 * Month and weekday names can be given as the first three letters of the
 * English names.
 * <p/>
 * <p>
 * Examples:
 * <ul>
 * <li>"0 0 * * * *" = the top of every hour of every day.</li>
 * <li>"*&#47;10 * * * * *" = every ten seconds.</li>
 * <li>"0 0 8-10 * * *" = 8, 9 and 10 o'clock of every day.</li>
 * <li>"0 0/30 8-10 * * *" = 8:00, 8:30, 9:00, 9:30 and 10 o'clock every
 * day.</li>
 * <li>"0 0 9-17 * * MON-FRI" = on the hour nine-to-five weekdays</li>
 * <li>"0 0 0 25 12 ?" = every Christmas Day at midnight</li>
 * <li>"@daily" = every day</li>
 * <li>"@annually" = every year</li>
 * <li>"@weekly" = every week</li>
 * <li>"@hourly" = every hour</li>
 * <li>"@monthly" = every month</li>
 * </ul>
 */
public final class EmbeddedCronParser {

    private final String expression;
    private final TimeZone timeZone;

    private int yearMin;
    private int yearMax;

    private final BitSet months = new BitSet(12);
    private final BitSet daysOfMonth = new BitSet(31);
    private final BitSet daysOfWeek = new BitSet(7);
    private final BitSet hours = new BitSet(24);
    private final BitSet minutes = new BitSet(60);
    private final BitSet seconds = new BitSet(60);

    private static final Pattern SPACE_SPLITTER = Pattern.compile("[ ]+");

    /**
     * Construct a {@link EmbeddedCronParser} from the pattern provided, using the
     * default {@link TimeZone}.
     *
     * @param expression a space-separated list of time fields
     * @throws IllegalArgumentException if the pattern cannot be parsed
     * @see java.util.TimeZone#getDefault()
     */
    public EmbeddedCronParser(final String expression) {
        this(expression, TimeZone.getDefault());
    }

    /**
     * Construct a {@link EmbeddedCronParser} from the pattern provided, using the
     * specified {@link TimeZone}.
     *
     * @param expression a space-separated list of time fields
     * @param timeZone   the TimeZone to use for generated trigger times
     * @throws IllegalArgumentException if the pattern cannot be parsed
     */
    public EmbeddedCronParser(final String expression, final TimeZone timeZone) {
        if (expression.startsWith("@")) {
            this.expression = preDeclared(expression);
        } else {
            this.expression = expression;
        }
        this.timeZone = timeZone;
        parse(this.expression);
    }

    /**
     * Get the next {@link Date} in the sequence matching the Cron pattern and after
     * the value provided. The return value will have a whole number of seconds, and
     * will be after the input value.
     *
     * @param date a seed value
     * @return the next value matching the pattern
     */
    public long next(final long date) {
        /*
         * The plan: 1 Round up to the next whole second 2 If seconds match move on,
         * otherwise find the next match: 2.1 If next match is in the next minute then
         * roll forwards 3 If minute matches move on, otherwise find the next match 3.1
         * If next match is in the next hour then roll forwards 3.2 Reset the seconds
         * and go to 2 4 If hour matches move on, otherwise find the next match 4.1 If
         * next match is in the next day then roll forwards, 4.2 Reset the minutes and
         * seconds and go to 2 ...
         */
        final Calendar calendar = new GregorianCalendar();
        calendar.setTimeZone(timeZone);
        calendar.setTimeInMillis(date);

        // First, just reset the milliseconds and try to calculate from there...
        calendar.set(Calendar.MILLISECOND, 0);
        final long originalTimestamp = calendar.getTimeInMillis();
        int currentYear = calendar.get(Calendar.YEAR);
        doNext(calendar, currentYear);

        if (calendar.getTimeInMillis() == originalTimestamp) {
            // We arrived at the original timestamp - round up to the next whole second and
            // try again...
            calendar.add(Calendar.SECOND, 1);
            doNext(calendar, currentYear);
        }

        return calendar.getTimeInMillis();
    }

    private void doNext(final Calendar calendar, final int yearOfInputDate) {
        final List<Integer> resets = new ArrayList<>();

        final int second = calendar.get(Calendar.SECOND);
        final int updateSecond = findNext(seconds, second, calendar, Calendar.SECOND, Calendar.MINUTE, Collections.emptyList());
        if (second == updateSecond) {
            resets.add(Calendar.SECOND);
        }

        final int minute = calendar.get(Calendar.MINUTE);
        final int updateMinute = findNext(minutes, minute, calendar, Calendar.MINUTE, Calendar.HOUR_OF_DAY, resets);
        if (minute == updateMinute) {
            resets.add(Calendar.MINUTE);
        } else {
            doNext(calendar, yearOfInputDate);
        }

        final int hour = calendar.get(Calendar.HOUR_OF_DAY);
        final int updateHour = findNext(hours, hour, calendar, Calendar.HOUR_OF_DAY, Calendar.DAY_OF_WEEK, resets);
        if (hour == updateHour) {
            resets.add(Calendar.HOUR_OF_DAY);
        } else {
            doNext(calendar, yearOfInputDate);
        }

        final int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        final int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
        final int updateDayOfMonth = findNextDay(calendar, daysOfMonth, dayOfMonth, daysOfWeek, dayOfWeek, resets);
        if (dayOfMonth == updateDayOfMonth) {
            resets.add(Calendar.DAY_OF_MONTH);
        } else {
            doNext(calendar, yearOfInputDate);
        }

        final int month = calendar.get(Calendar.MONTH);
        final int updateMonth = findNext(months, month, calendar, Calendar.MONTH, Calendar.YEAR, resets);
        if (month == updateMonth) {
            resets.add(Calendar.MONTH);
        } else {
            if (calendar.get(Calendar.YEAR) - yearOfInputDate > 4) {
                throw new IllegalArgumentException(
                        "Invalid cron expression \"" + expression + "\" led to runaway search for next trigger");
            }
            doNext(calendar, yearOfInputDate);
        }

        final int year = calendar.get(Calendar.YEAR);
        final int updateYear = findNextYear(yearMin, yearMax, year, calendar, yearOfInputDate, resets);
        if (year == updateYear) {
            resets.add(Calendar.YEAR);
        } else {
            doNext(calendar, yearOfInputDate);
        }
    }

    private int findNextYear(int yearMin, int yearMax, int year, Calendar calendar, int yearOfInputDate, List<Integer> lowerOrders) {

        int nextYearVal = year;
        if(year < yearMin) {
            nextYearVal = yearMin;
        } else if(year > yearMax) {
            nextYearVal = calendar.getMaximum(Calendar.YEAR);
        }
        if(nextYearVal != year) {
            calendar.set(Calendar.YEAR, nextYearVal);
            reset(calendar, lowerOrders);
        }
        return nextYearVal;
    }

    private int findNextDay(final Calendar calendar, final BitSet daysOfMonth, int dayOfMonth, final BitSet daysOfWeek,
            int dayOfWeek, final List<Integer> resets) {
        int count = 0;
        final int max = 366;
        // the DAY_OF_WEEK values in java.util.Calendar start with 1 (Sunday),
        // but in the cron pattern, they start with 0, so we subtract 1 here
        while ((!daysOfMonth.get(dayOfMonth) || !daysOfWeek.get(dayOfWeek - 1)) && count++ < max) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
            dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
            reset(calendar, resets);
        }
        if (count >= max) {
            throw new IllegalArgumentException("Overflow in day for expression \"" + expression + "\"");
        }
        return dayOfMonth;
    }

    /**
     * Search the bits provided for the next set bit after the value provided, and
     * reset the calendar.
     *
     * @param bits        a {@link BitSet} representing the allowed values of the
     *                    field
     * @param value       the current value of the field
     * @param calendar    the calendar to increment as we move through the bits
     * @param field       the field to increment in the calendar (@see
     *                    {@link Calendar} for the static constants defining valid
     *                    fields)
     * @param lowerOrders the Calendar field ids that should be reset (i.e. the ones
     *                    of lower significance than the field of interest)
     * @return the value of the calendar field that is next in the sequence
     */
    private int findNext(final BitSet bits, final int value, final Calendar calendar, final int field,
            final int nextField, final List<Integer> lowerOrders) {
        int nextValue = bits.nextSetBit(value);
        // roll over if needed
        if (nextValue == -1) {
            calendar.add(nextField, 1);
            reset(calendar, Collections.singletonList(field));
            nextValue = bits.nextSetBit(0);
        }
        if (nextValue != value) {
            calendar.set(field, nextValue);
            reset(calendar, lowerOrders);
        }
        return nextValue;
    }

    /**
     * Reset the calendar setting all the fields provided to zero.
     */
    private void reset(final Calendar calendar, final List<Integer> fields) {
        for (final int field : fields) {
            calendar.set(field, field == Calendar.DAY_OF_MONTH ? 1 : 0);
        }
    }

    // Parsing logic invoked by the constructor

    /**
     * Parse the given pattern expression.
     */
    private void parse(final String expression) {
        String[] fields = SPACE_SPLITTER.split(expression);

        setYears(expression, fields);
        setNumberHits(seconds, fields[0], 0, 60);
        setNumberHits(minutes, fields[1], 0, 60);
        setNumberHits(hours, fields[2], 0, 24);
        setDaysOfMonth(daysOfMonth, fields[3]);
        setMonths(months, fields[4]);
        setDays(daysOfWeek, replaceOrdinals(fields[5], "SUN,MON,TUE,WED,THU,FRI,SAT"), 8);
        if (daysOfWeek.get(7)) {
            // Sunday can be represented as 0 or 7
            daysOfWeek.set(0);
            daysOfWeek.clear(7);
        }
    }

    private void setYears(final String expression, String[] fields) {
        if (fields.length == 7) {
            String yearField = fields[6];
            if("*".equals(yearField)) {
                yearMin = 0;
                yearMax = Integer.MAX_VALUE;
            } else {
                String[] yearParts = yearField.split("-");
                if(yearParts.length == 1) {
                    yearMin = yearMax = Integer.parseInt(yearParts[0]);
                } else if(yearParts.length == 2) {
                    yearMin = Integer.parseInt(yearParts[0]);
                    yearMax = Integer.parseInt(yearParts[1]);
                } else {
                    throw new IllegalArgumentException(String.format("Invalid year field '%s' in expression '%s'", yearField, expression)) ;
                }

            }
        } else if (fields.length == 6) {
            yearMin = 0;
            yearMax = Integer.MAX_VALUE;
        } else {
            throw new IllegalArgumentException(String.format(
                    "Cron expression must consist of 6 or 7 fields (found %d in \"%s\")", fields.length, expression));
        }
    }

    /**
     * Replace the values in the comma-separated list (case insensitive) with their
     * index in the list.
     *
     * @return a new String with the values from the list replaced
     */
    private String replaceOrdinals(String value, final String commaSeparatedList) {
        final String[] list = commaSeparatedList.split(",");
        for (int i = 0; i < list.length; i++) {
            final String item = list[i].toUpperCase();
            value = value.toUpperCase().replace(item, "" + i);
        }
        return value;
    }

    private void setDaysOfMonth(final BitSet bits, final String field) {
        final int max = 31;
        // Days of month start with 1 (in Cron and Calendar) so add one
        setDays(bits, field, max + 1);
        // ... and remove it from the front
        bits.clear(0);
    }

    private void setDays(final BitSet bits, String field, final int max) {
        if (field.contains("?")) {
            field = "*";
        }
        setNumberHits(bits, field, 0, max);
    }

    private void setMonths(final BitSet bits, String value) {
        final int max = 12;
        value = replaceOrdinals(value, "FOO,JAN,FEB,MAR,APR,MAY,JUN,JUL,AUG,SEP,OCT,NOV,DEC");
        final BitSet months = new BitSet(13);
        // Months start with 1 in Cron and 0 in Calendar, so push the values first into
        // a longer bit set
        setNumberHits(months, value, 1, max + 1);
        // ... and then rotate it to the front of the months
        for (int i = 1; i <= max; i++) {
            if (months.get(i)) {
                bits.set(i - 1);
            }
        }
    }

    private void setNumberHits(final BitSet bits, final String value, final int min, final int max) {
        final String[] fields = value.split(",");
        for (final String field : fields) {
            if (!field.contains("/")) {
                // Not an incrementer so it must be a range (possibly empty)
                final int[] range = getRange(field, min, max);
                bits.set(range[0], range[1] + 1);
            } else {
                final String[] split = field.split("/");
                if (split.length > 2) {
                    throw new IllegalArgumentException("Incrementer has more than two fields: '" + field
                            + "' in expression \"" + expression + "\"");
                }
                final int[] range = getRange(split[0], min, max);
                if (!split[0].contains("-")) {
                    range[1] = max - 1;
                }
                final int delta = Integer.parseInt(split[1]);
                if (delta <= 0) {
                    throw new IllegalArgumentException("Incrementer delta must be 1 or higher: '" + field
                            + "' in expression \"" + expression + "\"");
                }
                for (int i = range[0]; i <= range[1]; i += delta) {
                    bits.set(i);
                }
            }
        }
    }

    private int[] getRange(final String field, final int min, final int max) {
        final int[] result = new int[2];
        if (field.contains("*")) {
            result[0] = min;
            result[1] = max - 1;
            return result;
        }
        if (!field.contains("-")) {
            result[0] = result[1] = Integer.valueOf(field);
        } else {
            final String[] split = field.split("-");
            if (split.length > 2) {
                throw new IllegalArgumentException(
                        "Range has more than two fields: '" + field + "' in expression \"" + expression + "\"");
            }
            result[0] = Integer.valueOf(split[0]);
            result[1] = Integer.valueOf(split[1]);
        }
        if (result[0] >= max || result[1] >= max) {
            throw new IllegalArgumentException(
                    "Range exceeds maximum (" + max + "): '" + field + "' in expression \"" + expression + "\"");
        }
        if (result[0] < min || result[1] < min) {
            throw new IllegalArgumentException(
                    "Range less than minimum (" + min + "): '" + field + "' in expression \"" + expression + "\"");
        }
        return result;
    }

    /**
     * Convert predeclared words into their representing cron expression.
     *
     * <pre>
     * &#64;yearly (or &#64;annually)  Run once a year at midnight on the morning of January 1                      0 0 0 1 1 *
     * &#64;monthly                    Run once a month at midnight on the morning of the first day of the month    0 0 0 1 * *
     * &#64;weekly                     Run once a week at midnight on Sunday morning                                0 0 0 * * 0
     * &#64;daily                      Run once a day at midnight                                                   0 0 0 * * *
     * &#64;hourly                     Run once an hour at the beginning of the hour                                0 0 * * * *
     * &#64;reboot                     Run at startup                                                               0 0 0 1 1 ? 1900
     * </pre>
     */
    private String preDeclared(final String expression) {
        switch (expression) {
        case "@annually":
        case "@yearly":
            return "0 0 0 1 1 *";
        case "@monthly":
            return "0 0 0 1 * *";
        case "@weekly":
            return "0 0 0 ? * MON";
        case "@daily":
            return "0 0 0 * * ?";
        case "@hourly":
            return "0 0 * * * ?";
        default:
            throw new IllegalArgumentException("Unrecognized @ expression: '" + expression + "'");
        }
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EmbeddedCronParser)) {
            return false;
        }
        final EmbeddedCronParser otherCron = (EmbeddedCronParser) other;
        return months.equals(otherCron.months) && daysOfMonth.equals(otherCron.daysOfMonth)
                && daysOfWeek.equals(otherCron.daysOfWeek) && hours.equals(otherCron.hours)
                && minutes.equals(otherCron.minutes) && seconds.equals(otherCron.seconds);
    }

    @Override
    public int hashCode() {
        return 17 * months.hashCode() + 29 * daysOfMonth.hashCode() + 37 * daysOfWeek.hashCode() + 41 * hours.hashCode()
                + 53 * minutes.hashCode() + 61 * seconds.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ": " + expression;
    }

    String getExpression() {
        return expression;
    }

}