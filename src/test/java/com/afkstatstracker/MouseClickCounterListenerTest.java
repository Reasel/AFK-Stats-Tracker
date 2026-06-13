package com.afkstatstracker;

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
}
