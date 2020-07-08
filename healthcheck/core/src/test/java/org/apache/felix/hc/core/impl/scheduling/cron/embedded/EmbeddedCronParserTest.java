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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EmbeddedCronParserTest {

    private final Calendar calendar = new GregorianCalendar();

    private final Date date;

    private final TimeZone timeZone;

    public EmbeddedCronParserTest(final Date date, final TimeZone timeZone) {
        this.date = date;
        this.timeZone = timeZone;
    }

    @Parameters(name = "date [{0}], time zone [{1}]")
    public static List<Object[]> getParameters() {
        final List<Object[]> list = new ArrayList<>();
        list.add(new Object[] { new Date(), TimeZone.getTimeZone("PST") });
        list.add(new Object[] { new Date(), TimeZone.getTimeZone("CET") });
        return list;
    }

    private static void roundup(final Calendar calendar) {
        calendar.add(Calendar.SECOND, 1);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    @Before
    public void setUp() {
        calendar.setTimeZone(timeZone);
        calendar.setTime(date);
        roundup(calendar);
    }

    @Test
    public void testAt0And15And45Seconds() {
        
        final EmbeddedCronParser trigger = new EmbeddedCronParser("*/15 * 1-4 * * *", timeZone);

        calendar.set(Calendar.DAY_OF_MONTH, 2);
        calendar.set(Calendar.HOUR_OF_DAY, 9);
        calendar.set(Calendar.MINUTE, 53);
        calendar.set(Calendar.SECOND, 50);

        Date intialTime = calendar.getTime();
        calendar.set(Calendar.DAY_OF_MONTH, 3);
        calendar.set(Calendar.HOUR_OF_DAY, 1);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        assertEquals(calendar.getTime().getTime(), trigger.next(intialTime.getTime()));

        intialTime = calendar.getTime();
        calendar.set(Calendar.SECOND, 15);
        assertEquals(calendar.getTime().getTime(), trigger.next(intialTime.getTime()));

        intialTime = calendar.getTime();
        calendar.set(Calendar.SECOND, 30);
        assertEquals(calendar.getTime().getTime(), trigger.next(intialTime.getTime()));

        intialTime = calendar.getTime();
        calendar.set(Calendar.SECOND, 45);
        assertEquals(calendar.getTime().getTime(), trigger.next(intialTime.getTime()));

    }


    @Test
    public void testAt0Minutes() {

        final EmbeddedCronParser trigger = new EmbeddedCronParser("0 */2 1-4 * * *", timeZone);

        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 9);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        Date intialTime = calendar.getTime();
        calendar.set(Calendar.DAY_OF_MONTH, 2);
        calendar.set(Calendar.HOUR_OF_DAY, 1);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        long next = trigger.next(intialTime.getTime());
        assertEquals(calendar.getTime().getTime(), next);


    }

    @Test(expected = IllegalArgumentException.class)
    public void testWith0Increment() {

        new EmbeddedCronParser("*/0 * * * * *").next(calendar.getTime().getTime());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithNegativeIncrement() {
        new EmbeddedCronParser("*/-1 * * * * *").next(calendar.getTime().getTime());
    }
    
    @Test
    public void testMatchAll() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("* * * * * *", timeZone);
        assertEquals(calendar.getTimeInMillis(), trigger.next(date.getTime()));
    }

    @Test
    public void testMatchLastSecond() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("* * * * * *", timeZone);
        calendar.set(Calendar.SECOND, 58);
        assertMatchesNextSecond(trigger, calendar);
    }

    @Test
    public void testMatchSpecificSecond() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("10 * * * * *", timeZone);
        calendar.set(Calendar.SECOND, 9);
        assertMatchesNextSecond(trigger, calendar);
    }

    @Test
    public void testIncrementSecondByOne() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("11 * * * * *", timeZone);
        calendar.set(Calendar.SECOND, 10);
        final Date date = calendar.getTime();
        calendar.add(Calendar.SECOND, 1);
        assertEquals(calendar.getTimeInMillis(), trigger.next(date.getTime()));
    }

    @Test
    public void testIncrementSecondWithPreviousExecutionTooEarly() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("11 * * * * *", timeZone);
        calendar.set(Calendar.SECOND, 11);
        final Date date = calendar.getTime();

        calendar.add(Calendar.MINUTE, 1);
        assertEquals(calendar.getTimeInMillis(), trigger.next(date.getTime()));
    }

    @Test
    public void testIncrementSecondAndRollover() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("10 * * * * *", timeZone);
        calendar.set(Calendar.SECOND, 11);
        final Date date = calendar.getTime();
        calendar.add(Calendar.SECOND, 59);
        assertEquals(calendar.getTimeInMillis(), trigger.next(date.getTime()));
    }

    @Test
    public void testSecondRange() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("10-15 * * * * *", timeZone);
        calendar.set(Calendar.SECOND, 9);
        assertMatchesNextSecond(trigger, calendar);
        calendar.set(Calendar.SECOND, 14);
        assertMatchesNextSecond(trigger, calendar);
    }

    @Test
    public void testIncrementMinute() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("0 * * * * *", timeZone);
        calendar.set(Calendar.MINUTE, 10);
        Date date = calendar.getTime();
        calendar.add(Calendar.MINUTE, 1);
        calendar.set(Calendar.SECOND, 0);
        date = new Date(trigger.next(date.getTime()));
        assertEquals(calendar.getTimeInMillis(), date.getTime());
        calendar.add(Calendar.MINUTE, 1);
        date = new Date(trigger.next(date.getTime()));
        assertEquals(calendar.getTimeInMillis(), date.getTime());
    }

    @Test
    public void testIncrementMinuteByOne() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("0 11 * * * *", timeZone);
        calendar.set(Calendar.MINUTE, 10);
        final Date date = calendar.getTime();

        calendar.add(Calendar.MINUTE, 1);
        calendar.set(Calendar.SECOND, 0);
        assertEquals(calendar.getTimeInMillis(), trigger.next(date.getTime()));
    }

    @Test
    public void testIncrementMinuteAndRollover() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("0 10 * * * *", timeZone);
        calendar.set(Calendar.MINUTE, 11);
        calendar.set(Calendar.SECOND, 0);
        final Date date = calendar.getTime();
        calendar.add(Calendar.MINUTE, 59);
        assertEquals(calendar.getTimeInMillis(), trigger.next(date.getTime()));
    }

    @Test
    public void testIncrementHour() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("0 0 * * * *", timeZone);
        calendar.set(Calendar.MONTH, 9);
        calendar.set(Calendar.DAY_OF_MONTH, 30);
        calendar.set(Calendar.HOUR_OF_DAY, 11);
        calendar.set(Calendar.MINUTE, 1);
        calendar.set(Calendar.SECOND, 0);
        Date date = calendar.getTime();
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.HOUR_OF_DAY, 12);
        date = new Date(trigger.next(date.getTime()));
        assertEquals(calendar.getTimeInMillis(), date.getTime());
        calendar.set(Calendar.HOUR_OF_DAY, 13);
        assertEquals(calendar.getTimeInMillis(), trigger.next(date.getTime()));
    }

    @Test
    public void testIncrementHourAndRollover() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("0 0 * * * *", timeZone);
        calendar.set(Calendar.MONTH, 9);
        calendar.set(Calendar.DAY_OF_MONTH, 10);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 1);
        calendar.set(Calendar.SECOND, 0);
        Date date = calendar.getTime();
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.DAY_OF_MONTH, 11);
        date = new Date(trigger.next(date.getTime()));
        assertEquals(calendar.getTimeInMillis(), date.getTime());
        calendar.set(Calendar.HOUR_OF_DAY, 1);
        assertEquals(calendar.getTimeInMillis(), trigger.next(date.getTime()));
    }

    @Test
    public void testIncrementDayOfMonth() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("0 0 0 * * *", timeZone);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        Date date = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        date = new Date(trigger.next(date.getTime()));
        assertEquals(calendar.getTimeInMillis(), date.getTime());
        assertEquals(2, calendar.get(Calendar.DAY_OF_MONTH));
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        date = new Date(trigger.next(date.getTime()));
        assertEquals(calendar.getTimeInMillis(), date.getTime());
        assertEquals(3, calendar.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void testIncrementDayOfMonthByOne() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("* * * 10 * *", timeZone);
        calendar.set(Calendar.DAY_OF_MONTH, 9);
        final Date date = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        assertEquals(calendar.getTimeInMillis(), trigger.next(date.getTime()));
    }

    @Test
    public void testIncrementDayOfMonthAndRollover() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("* * * 10 * *", timeZone);
        calendar.set(Calendar.DAY_OF_MONTH, 11);
        final Date date = calendar.getTime();
        calendar.add(Calendar.MONTH, 1);
        calendar.set(Calendar.DAY_OF_MONTH, 10);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        assertEquals(calendar.getTimeInMillis(), trigger.next(date.getTime()));
    }

    @Test
    public void testDailyTriggerInShortMonth() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("0 0 0 * * *", timeZone);
        calendar.set(Calendar.MONTH, 8); // September: 30 days
        calendar.set(Calendar.DAY_OF_MONTH, 30);
        Date date = calendar.getTime();
        calendar.set(Calendar.MONTH, 9); // October
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.DAY_OF_MONTH, 1);

        date = new Date(trigger.next(date.getTime()));
        assertEquals(calendar.getTimeInMillis(), date.getTime());

        calendar.set(Calendar.DAY_OF_MONTH, 2);
        assertEquals(calendar.getTimeInMillis(), trigger.next(date.getTime()));
    }

    @Test
    public void testDailyTriggerInLongMonth() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("0 0 0 * * *", timeZone);
        calendar.set(Calendar.MONTH, 7); // August: 31 days and not a daylight saving boundary
        calendar.set(Calendar.DAY_OF_MONTH, 30);
        Date date = calendar.getTime();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.DAY_OF_MONTH, 31);

        date = new Date(trigger.next(date.getTime()));
        assertEquals(calendar.getTimeInMillis(), date.getTime());

        calendar.set(Calendar.MONTH, 8); // September
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        assertEquals(calendar.getTimeInMillis(), trigger.next(date.getTime()));
    }

    @Test
    public void testDailyTriggerOnDaylightSavingBoundary() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("0 0 0 * * *", timeZone);
        calendar.set(Calendar.MONTH, 9); // October: 31 days and a daylight saving boundary in CET
        calendar.set(Calendar.DAY_OF_MONTH, 30);
        Date date = calendar.getTime();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.DAY_OF_MONTH, 31);

        date = new Date(trigger.next(date.getTime()));
        assertEquals(calendar.getTimeInMillis(), date.getTime());

        calendar.set(Calendar.MONTH, 10); // November
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        assertEquals(calendar.getTimeInMillis(), trigger.next(date.getTime()));
    }

    @Test
    public void testIncrementMonth() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("0 0 0 1 * *", timeZone);
        calendar.set(Calendar.MONTH, 9);
        calendar.set(Calendar.DAY_OF_MONTH, 30);
        Date date = calendar.getTime();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MONTH, 10);

        date = new Date(trigger.next(date.getTime()));
        assertEquals(calendar.getTimeInMillis(), date.getTime());

        calendar.set(Calendar.MONTH, 11);
        assertEquals(calendar.getTimeInMillis(), trigger.next(date.getTime()));
    }

    @Test
    public void testIncrementMonthAndRollover() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("0 0 0 1 * *", timeZone);
        calendar.set(Calendar.MONTH, 11);
        calendar.set(Calendar.DAY_OF_MONTH, 31);
        calendar.set(Calendar.YEAR, 2010);
        Date date = calendar.getTime();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MONTH, 0);
        calendar.set(Calendar.YEAR, 2011);

        date = new Date(trigger.next(date.getTime()));
        assertEquals(calendar.getTimeInMillis(), date.getTime());

        calendar.set(Calendar.MONTH, 1);
        assertEquals(calendar.getTimeInMillis(), trigger.next(date.getTime()));
    }

    @Test
    public void testMonthlyTriggerInLongMonth() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("0 0 0 31 * *", timeZone);
        calendar.set(Calendar.MONTH, 9);
        calendar.set(Calendar.DAY_OF_MONTH, 30);
        final Date date = calendar.getTime();
        calendar.set(Calendar.DAY_OF_MONTH, 31);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        assertEquals(calendar.getTimeInMillis(), trigger.next(date.getTime()));
    }

    @Test
    public void testMonthlyTriggerInShortMonth() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("0 0 0 1 * *", timeZone);
        calendar.set(Calendar.MONTH, 9);
        calendar.set(Calendar.DAY_OF_MONTH, 30);
        final Date date = calendar.getTime();
        calendar.set(Calendar.MONTH, 10);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        assertEquals(calendar.getTimeInMillis(), trigger.next(date.getTime()));
    }

    @Test
    public void testIncrementDayOfWeekByOne() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("* * * * * 2", timeZone);
        calendar.set(Calendar.DAY_OF_WEEK, 2);
        final Date date = calendar.getTime();
        calendar.add(Calendar.DAY_OF_WEEK, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        assertEquals(calendar.getTimeInMillis(), trigger.next(date.getTime()));
        assertEquals(Calendar.TUESDAY, calendar.get(Calendar.DAY_OF_WEEK));
    }

    @Test
    public void testIncrementDayOfWeekAndRollover() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("* * * * * 2", timeZone);
        calendar.set(Calendar.DAY_OF_WEEK, 4);
        final Date date = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, 6);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        assertEquals(calendar.getTimeInMillis(), trigger.next(date.getTime()));
        assertEquals(Calendar.TUESDAY, calendar.get(Calendar.DAY_OF_WEEK));
    }

    @Test
    public void testSpecificMinuteSecond() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("55 5 * * * *", timeZone);
        calendar.set(Calendar.MINUTE, 4);
        calendar.set(Calendar.SECOND, 54);
        Date date = calendar.getTime();

        calendar.add(Calendar.MINUTE, 1);
        calendar.set(Calendar.SECOND, 55);

        date = new Date(trigger.next(date.getTime()));
        assertEquals(calendar.getTimeInMillis(), date.getTime());

        calendar.add(Calendar.HOUR, 1);
        assertEquals(calendar.getTimeInMillis(), trigger.next(date.getTime()));
    }

    @Test
    public void testSpecificHourSecond() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("55 * 10 * * *", timeZone);
        calendar.set(Calendar.HOUR_OF_DAY, 9);
        calendar.set(Calendar.SECOND, 54);
        Date date = calendar.getTime();

        calendar.add(Calendar.HOUR_OF_DAY, 1);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 55);

        date = new Date(trigger.next(date.getTime()));
        assertEquals(calendar.getTimeInMillis(), date.getTime());

        calendar.add(Calendar.MINUTE, 1);
        assertEquals(calendar.getTimeInMillis(), trigger.next(date.getTime()));
    }

    @Test
    public void testSpecificMinuteHour() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("* 5 10 * * *", timeZone);
        calendar.set(Calendar.MINUTE, 4);
        calendar.set(Calendar.HOUR_OF_DAY, 9);
        Date date = calendar.getTime();
        calendar.add(Calendar.MINUTE, 1);
        calendar.add(Calendar.HOUR_OF_DAY, 1);
        calendar.set(Calendar.SECOND, 0);

        date = new Date(trigger.next(date.getTime()));
        assertEquals(calendar.getTimeInMillis(), date.getTime());

        // next trigger is in one second because second is wildcard
        calendar.add(Calendar.SECOND, 1);
        assertEquals(calendar.getTimeInMillis(), trigger.next(date.getTime()));
    }

    @Test
    public void testSpecificDayOfMonthSecond() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("55 * * 3 * *", timeZone);
        calendar.set(Calendar.DAY_OF_MONTH, 2);
        calendar.set(Calendar.SECOND, 54);
        Date date = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 55);

        date = new Date(trigger.next(date.getTime()));
        assertEquals(calendar.getTimeInMillis(), date.getTime());

        calendar.add(Calendar.MINUTE, 1);
        assertEquals(calendar.getTimeInMillis(), trigger.next(date.getTime()));
    }

    @Test
    public void testSpecificDate() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("* * * 3 11 *", timeZone);
        calendar.set(Calendar.DAY_OF_MONTH, 2);
        calendar.set(Calendar.MONTH, 9);
        Date date = calendar.getTime();

        calendar.add(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MONTH, 10); // 10=November
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        date = new Date(trigger.next(date.getTime()));
        assertEquals(calendar.getTimeInMillis(), date.getTime());

        calendar.add(Calendar.SECOND, 1);
        date = new Date(trigger.next(date.getTime()));
        assertEquals(calendar.getTimeInMillis(), date.getTime());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNonExistentSpecificDate() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("0 0 0 31 6 *", timeZone);
        calendar.set(Calendar.DAY_OF_MONTH, 10);
        calendar.set(Calendar.MONTH, 2);
        final Date date = calendar.getTime();
        trigger.next(date.getTime());
    }

    @Test
    public void testLeapYearSpecificDate() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("0 0 0 29 2 *", timeZone);
        calendar.set(Calendar.YEAR, 2007);
        calendar.set(Calendar.DAY_OF_MONTH, 10);
        calendar.set(Calendar.MONTH, 1); // 2=February
        Date date = calendar.getTime();

        calendar.set(Calendar.YEAR, 2008);
        calendar.set(Calendar.DAY_OF_MONTH, 29);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        date = new Date(trigger.next(date.getTime()));
        assertEquals(calendar.getTimeInMillis(), date.getTime());
        calendar.add(Calendar.YEAR, 4);
        date = new Date(trigger.next(date.getTime()));
        assertEquals(calendar.getTimeInMillis(), date.getTime());
    }

    @Test
    public void testWeekDaySequence() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("0 0 7 ? * MON-FRI", timeZone);
        // This is a Saturday
        calendar.set(2009, Calendar.SEPTEMBER, 26);
        Date date = calendar.getTime();
        // 7 am is the trigger time
        calendar.set(Calendar.HOUR_OF_DAY, 7);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        // Add two days because we start on Saturday
        calendar.add(Calendar.DAY_OF_MONTH, 2);
        date = new Date(trigger.next(date.getTime()));
        assertEquals(calendar.getTimeInMillis(), date.getTime());
        // Next day is a week day so add one
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        date = new Date(trigger.next(date.getTime()));
        assertEquals(calendar.getTimeInMillis(), date.getTime());
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        date = new Date(trigger.next(date.getTime()));
        assertEquals(calendar.getTimeInMillis(), date.getTime());
    }

    @Test
    public void testDayOfWeekIndifferent() {
        final EmbeddedCronParser trigger1 = new EmbeddedCronParser("* * * 2 * *", timeZone);
        final EmbeddedCronParser trigger2 = new EmbeddedCronParser("* * * 2 * ?", timeZone);
        assertEquals(trigger1, trigger2);
    }

    @Test
    public void testSecondIncrementer() {
        final EmbeddedCronParser trigger1 = new EmbeddedCronParser("57,59 * * * * *", timeZone);
        final EmbeddedCronParser trigger2 = new EmbeddedCronParser("57/2 * * * * *", timeZone);
        assertEquals(trigger1, trigger2);
    }

    @Test
    public void testSecondIncrementerWithRange() {
        final EmbeddedCronParser trigger1 = new EmbeddedCronParser("1,3,5 * * * * *", timeZone);
        final EmbeddedCronParser trigger2 = new EmbeddedCronParser("1-6/2 * * * * *", timeZone);
        assertEquals(trigger1, trigger2);
    }

    @Test
    public void testHourIncrementer() {
        final EmbeddedCronParser trigger1 = new EmbeddedCronParser("* * 4,8,12,16,20 * * *", timeZone);
        final EmbeddedCronParser trigger2 = new EmbeddedCronParser("* * 4/4 * * *", timeZone);
        assertEquals(trigger1, trigger2);
    }

    @Test
    public void testDayNames() {
        final EmbeddedCronParser trigger1 = new EmbeddedCronParser("* * * * * 0-6", timeZone);
        final EmbeddedCronParser trigger2 = new EmbeddedCronParser("* * * * * TUE,WED,THU,FRI,SAT,SUN,MON", timeZone);
        assertEquals(trigger1, trigger2);
    }

    @Test
    public void testSundayIsZero() {
        final EmbeddedCronParser trigger1 = new EmbeddedCronParser("* * * * * 0", timeZone);
        final EmbeddedCronParser trigger2 = new EmbeddedCronParser("* * * * * SUN", timeZone);
        assertEquals(trigger1, trigger2);
    }

    @Test
    public void testSundaySynonym() {
        final EmbeddedCronParser trigger1 = new EmbeddedCronParser("* * * * * 0", timeZone);
        final EmbeddedCronParser trigger2 = new EmbeddedCronParser("* * * * * 7", timeZone);
        assertEquals(trigger1, trigger2);
    }

    @Test
    public void testMonthNames() {
        final EmbeddedCronParser trigger1 = new EmbeddedCronParser("* * * * 1-12 *", timeZone);
        final EmbeddedCronParser trigger2 = new EmbeddedCronParser(
                "* * * * FEB,JAN,MAR,APR,MAY,JUN,JUL,AUG,SEP,OCT,NOV,DEC *", timeZone);
        assertEquals(trigger1, trigger2);
    }

    @Test
    public void testMonthNamesMixedCase() {
        final EmbeddedCronParser trigger1 = new EmbeddedCronParser("* * * * 2 *", timeZone);
        final EmbeddedCronParser trigger2 = new EmbeddedCronParser("* * * * Feb *", timeZone);
        assertEquals(trigger1, trigger2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSecondInvalid() {
        new EmbeddedCronParser("77 * * * * *", timeZone);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSecondRangeInvalid() {
        new EmbeddedCronParser("44-77 * * * * *", timeZone);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMinuteInvalid() {
        new EmbeddedCronParser("* 77 * * * *", timeZone);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMinuteRangeInvalid() {
        new EmbeddedCronParser("* 44-77 * * * *", timeZone);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHourInvalid() {
        new EmbeddedCronParser("* * 27 * * *", timeZone);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHourRangeInvalid() {
        new EmbeddedCronParser("* * 23-28 * * *", timeZone);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDayInvalid() {
        new EmbeddedCronParser("* * * 45 * *", timeZone);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDayRangeInvalid() {
        new EmbeddedCronParser("* * * 28-45 * *", timeZone);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMonthInvalid() {
        new EmbeddedCronParser("0 0 0 25 13 ?", timeZone);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMonthInvalidTooSmall() {
        new EmbeddedCronParser("0 0 0 25 0 ?", timeZone);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDayOfMonthInvalid() {
        new EmbeddedCronParser("0 0 0 32 12 ?", timeZone);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMonthRangeInvalid() {
        new EmbeddedCronParser("* * * * 11-13 *", timeZone);
    }

    @Test
    public void testWhitespace() {
        final EmbeddedCronParser trigger1 = new EmbeddedCronParser("*  *  * *  1 *", timeZone);
        final EmbeddedCronParser trigger2 = new EmbeddedCronParser("* * * * 1 *", timeZone);
        assertEquals(trigger1, trigger2);
    }

    @Test
    public void testMonthSequence() {
        final EmbeddedCronParser trigger = new EmbeddedCronParser("0 30 23 30 1/3 ?", timeZone);
        calendar.set(2010, Calendar.DECEMBER, 30);
        Date date = calendar.getTime();
        // set expected next trigger time
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 30);
        calendar.set(Calendar.SECOND, 0);
        calendar.add(Calendar.MONTH, 1);

        date = new Date(trigger.next(date.getTime()));
        assertEquals(calendar.getTime(), date);

        // Next trigger is 3 months latter
        calendar.add(Calendar.MONTH, 3);
        date = new Date(trigger.next(date.getTime()));
        assertEquals(calendar.getTime(), date);

        // Next trigger is 3 months latter
        calendar.add(Calendar.MONTH, 3);
        date = new Date(trigger.next(date.getTime()));
        assertEquals(calendar.getTime(), date);
    }

    @Test
    public void testDaylightSavingMissingHour() {
        // This trigger has to be somewhere in between 2am and 3am
        final EmbeddedCronParser trigger = new EmbeddedCronParser("0 10 2 * * *", timeZone);
        calendar.set(Calendar.DAY_OF_MONTH, 31);
        calendar.set(Calendar.MONTH, Calendar.MARCH);
        calendar.set(Calendar.YEAR, 2013);
        calendar.set(Calendar.HOUR_OF_DAY, 1);
        calendar.set(Calendar.SECOND, 54);
        final Date date = calendar.getTime();
        if (timeZone.equals(TimeZone.getTimeZone("CET"))) {
            // Clocks go forward an hour so 2am doesn't exist in CET for this date
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        calendar.add(Calendar.HOUR_OF_DAY, 1);
        calendar.set(Calendar.MINUTE, 10);
        calendar.set(Calendar.SECOND, 0);
        assertEquals(calendar.getTimeInMillis(), trigger.next(date.getTime()));
    }

    @Test
    public void testYearFieldSetToStar() {

        final EmbeddedCronParser trigger = new EmbeddedCronParser("30 30 7 * * * *", timeZone);

        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 2);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        Date intialTime = calendar.getTime();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 7);
        calendar.set(Calendar.MINUTE, 30);
        calendar.set(Calendar.SECOND, 30);
        long next = trigger.next(intialTime.getTime());
        assertEquals(calendar.getTime().getTime(), next);
        
    }
    

    @Test
    public void testYearInThePast() {

        final EmbeddedCronParser trigger = new EmbeddedCronParser("* * * * * * 1977", timeZone);

        calendar.set(Calendar.YEAR, 2020);
        calendar.set(Calendar.MONTH, 0);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        
        Date intialTime = calendar.getTime();
        calendar.set(Calendar.YEAR, calendar.getMaximum(Calendar.YEAR));
        long next = trigger.next(intialTime.getTime());
        assertEquals(calendar.getTime().getTime(), next);

    }

    @Test
    public void testJumpToCronYear() {

        final EmbeddedCronParser trigger = new EmbeddedCronParser("* * * * * * 2022", timeZone);

        calendar.set(Calendar.YEAR, 2020);
        calendar.set(Calendar.MONTH, 5);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 3);
        calendar.set(Calendar.MINUTE, 4);
        calendar.set(Calendar.SECOND, 33);
        
        Date intialTime = calendar.getTime();
        calendar.set(Calendar.YEAR, 2022);
        calendar.set(Calendar.MONTH, Calendar.JANUARY);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        long next = trigger.next(intialTime.getTime());
        assertEquals(calendar.getTime().getTime(), next);
        
        final EmbeddedCronParser triggerWithRange = new EmbeddedCronParser("* * * * * * 2024-2030", timeZone);
        
        calendar.set(Calendar.YEAR, 2024);
        calendar.set(Calendar.MONTH, 0);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        long nextForRange = triggerWithRange.next(intialTime.getTime());
        assertEquals(calendar.getTime().getTime(), nextForRange);
    }

    @Test
    public void testCronYearStayOnSameYear() {

        final EmbeddedCronParser trigger = new EmbeddedCronParser("* * * * * * 2022", timeZone);

        calendar.set(Calendar.YEAR, 2022);
        calendar.set(Calendar.MONTH, Calendar.MAY);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 3);
        calendar.set(Calendar.MINUTE, 4);
        calendar.set(Calendar.SECOND, 33);
        
        Date intialTime = calendar.getTime();
        calendar.set(Calendar.SECOND, 34);
        long next = trigger.next(intialTime.getTime());
        assertEquals(calendar.getTime().getTime(), next);
        
        final EmbeddedCronParser triggerWithRange = new EmbeddedCronParser("* * * * * * 2022-2030", timeZone);
        calendar.set(Calendar.SECOND, 34);
        long nextForRange = triggerWithRange.next(intialTime.getTime());
        assertEquals(calendar.getTime().getTime(), nextForRange);

        final EmbeddedCronParser triggerWithDayAndTime = new EmbeddedCronParser("0 0 7 15 * * 2022-2030", timeZone);
        calendar.set(Calendar.DAY_OF_MONTH, 15);
        calendar.set(Calendar.HOUR_OF_DAY, 7);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        long nextForTriggerWithDayAndTime = triggerWithDayAndTime.next(intialTime.getTime());
        assertEquals(calendar.getTime().getTime(), nextForTriggerWithDayAndTime);

    }

    @Test
    public void testJumpToNextYearWithinRange() {

        final EmbeddedCronParser trigger = new EmbeddedCronParser("0 0 7 15 * * 2020-2025", timeZone);

        calendar.set(Calendar.YEAR, 2023);
        calendar.set(Calendar.MONTH, Calendar.DECEMBER);
        calendar.set(Calendar.DAY_OF_MONTH, 24);
        calendar.set(Calendar.HOUR_OF_DAY, 18);
        calendar.set(Calendar.MINUTE, 4);
        calendar.set(Calendar.SECOND, 33);
        
        Date intialTime = calendar.getTime();
        calendar.set(Calendar.YEAR, 2024);
        calendar.set(Calendar.MONTH, Calendar.JANUARY);
        calendar.set(Calendar.DAY_OF_MONTH, 15);
        calendar.set(Calendar.HOUR_OF_DAY, 7);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        long next = trigger.next(intialTime.getTime());
        assertEquals(calendar.getTime().getTime(), next);
    }

    @Test
    public void testJumpToNextYearLeavingRange() {

        final EmbeddedCronParser trigger = new EmbeddedCronParser("0 0 7 15 * * 2020-2023", timeZone);

        calendar.set(Calendar.YEAR, 2023);
        calendar.set(Calendar.MONTH, Calendar.DECEMBER);
        calendar.set(Calendar.DAY_OF_MONTH, 24);
        calendar.set(Calendar.HOUR_OF_DAY, 18);
        calendar.set(Calendar.MINUTE, 4);
        calendar.set(Calendar.SECOND, 33);
        
        Date intialTime = calendar.getTime();
        
        calendar.set(Calendar.YEAR, calendar.getMaximum(Calendar.YEAR));
        calendar.set(Calendar.MONTH, Calendar.JANUARY);
        calendar.set(Calendar.DAY_OF_MONTH, 0);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        long next = trigger.next(intialTime.getTime());
        assertTrue(next > calendar.getTime().getTime());
    }

    @Test
    public void testPreDeclared() {
        assertEquals("0 0 0 1 1 *", new EmbeddedCronParser("@annually", timeZone).getExpression());
        assertEquals("0 0 0 1 1 *", new EmbeddedCronParser("@yearly", timeZone).getExpression());
        assertEquals("0 0 0 1 * *", new EmbeddedCronParser("@monthly", timeZone).getExpression());
        assertEquals("0 0 0 ? * MON", new EmbeddedCronParser("@weekly", timeZone).getExpression());
        assertEquals("0 0 0 * * ?", new EmbeddedCronParser("@daily", timeZone).getExpression());
        assertEquals("0 0 * * * ?", new EmbeddedCronParser("@hourly", timeZone).getExpression());
    }

    private void assertMatchesNextSecond(final EmbeddedCronParser trigger, final Calendar calendar) {
        final Date date = calendar.getTime();
        roundup(calendar);
        assertEquals(calendar.getTimeInMillis(), trigger.next(date.getTime()));
    }

}