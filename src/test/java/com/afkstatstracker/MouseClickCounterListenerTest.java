package com.afkstatstracker;

import java.awt.Point;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MouseClickCounterListenerTest
{
    @Test
    public void testAddClickRecordsTimestamp()
    {
        MouseClickCounterListener listener = new MouseClickCounterListener();
        listener.addClick();
        listener.addClick();
        assertEquals(2, listener.getClickCounter().size());
    }

    @Test
    public void testResetClearsTimestamps()
    {
        MouseClickCounterListener listener = new MouseClickCounterListener();
        listener.addClick();
        listener.resetMouseClickCounterListener();
        assertTrue(listener.getClickCounter().isEmpty());
    }

    @Test
    public void testGetClickCounterReturnsDefensiveCopy()
    {
        MouseClickCounterListener listener = new MouseClickCounterListener();
        listener.addClick();
        List<Long> copy = listener.getClickCounter();
        copy.clear();
        assertEquals(1, listener.getClickCounter().size());
    }

    @Test
    public void testAddClickWithCoordsRecordsPoint()
    {
        MouseClickCounterListener listener = new MouseClickCounterListener();
        listener.addClick(10, 20);
        listener.addClick(30, 40);
        List<Point> points = listener.getClickPoints();
        assertEquals(2, points.size());
        assertEquals(new Point(10, 20), points.get(0));
        assertEquals(new Point(30, 40), points.get(1));
    }

    @Test
    public void testNoArgAddClickKeepsListsParallel()
    {
        MouseClickCounterListener listener = new MouseClickCounterListener();
        listener.addClick();
        assertEquals(1, listener.getClickCounter().size());
        assertEquals(1, listener.getClickPoints().size());
    }

    @Test
    public void testResetClearsPoints()
    {
        MouseClickCounterListener listener = new MouseClickCounterListener();
        listener.addClick(5, 5);
        listener.resetMouseClickCounterListener();
        assertTrue(listener.getClickPoints().isEmpty());
    }

    @Test
    public void testGetClickPointsReturnsDefensiveCopy()
    {
        MouseClickCounterListener listener = new MouseClickCounterListener();
        listener.addClick(1, 1);
        List<Point> copy = listener.getClickPoints();
        copy.clear();
        assertEquals(1, listener.getClickPoints().size());
    }
}
