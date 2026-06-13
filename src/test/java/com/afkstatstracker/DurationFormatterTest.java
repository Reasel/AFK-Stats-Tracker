package com.afkstatstracker;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class DurationFormatterTest
{
    @Test
    public void testZero()
    {
        assertEquals("0s", DurationFormatter.format(0));
    }

    @Test
    public void testSubSecondRoundsDown()
    {
        assertEquals("0s", DurationFormatter.format(999));
    }

    @Test
    public void testSecondsOnly()
    {
        assertEquals("45s", DurationFormatter.format(45_000));
    }

    @Test
    public void testMinutesAndSeconds()
    {
        assertEquals("12m 34s", DurationFormatter.format(754_000));
    }

    @Test
    public void testHoursMinutesSeconds()
    {
        assertEquals("2h 5m 9s", DurationFormatter.format(7_509_000));
    }

    @Test
    public void testNegativeClampsToZero()
    {
        assertEquals("0s", DurationFormatter.format(-5_000));
    }
}
