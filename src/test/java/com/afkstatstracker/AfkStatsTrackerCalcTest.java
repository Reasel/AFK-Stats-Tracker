package com.afkstatstracker;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class AfkStatsTrackerCalcTest
{
    private final AfkStatsTrackerPlugin plugin = new AfkStatsTrackerPlugin();

    @Test
    public void testAverageDistanceEmpty()
    {
        assertEquals(0.0, plugin.computeAverageDistance(new ArrayList<>()), 1e-9);
    }

    @Test
    public void testAverageDistanceSinglePoint()
    {
        List<Point> points = Arrays.asList(new Point(5, 5));
        assertEquals(0.0, plugin.computeAverageDistance(points), 1e-9);
    }

    @Test
    public void testAverageDistanceTwoLegs()
    {
        // (0,0)->(3,4)=5, (3,4)->(3,4)=0  => avg 2.5
        List<Point> points = Arrays.asList(new Point(0, 0), new Point(3, 4), new Point(3, 4));
        assertEquals(2.5, plugin.computeAverageDistance(points), 1e-9);
    }

    @Test
    public void testAverageDistanceAxisAligned()
    {
        // (0,0)->(0,10)=10, (0,10)->(10,10)=10 => avg 10
        List<Point> points = Arrays.asList(new Point(0, 0), new Point(0, 10), new Point(10, 10));
        assertEquals(10.0, plugin.computeAverageDistance(points), 1e-9);
    }

    @Test
    public void testToDistancePercentZeroCanvas()
    {
        assertEquals(0.0, plugin.toDistancePercent(50, 0, 0), 1e-9);
    }

    @Test
    public void testToDistancePercentHalf()
    {
        // diagonal of 300x400 = 500; 50/500 = 10%
        assertEquals(10.0, plugin.toDistancePercent(50, 300, 400), 1e-9);
    }

    @Test
    public void testToDistancePercentFullDiagonal()
    {
        // 500/500 = 100%
        assertEquals(100.0, plugin.toDistancePercent(500, 300, 400), 1e-9);
    }
}
