package com.afkstatstracker;

import org.junit.Test;
import static org.junit.Assert.*;

public class SessionTest
{
    @Test
    public void testSessionCreation()
    {
        Session session = new Session(
            "test-id",
            "Test Session",
            1000L,
            2000L,
            42,
            85L,
            45000.0,
            12.5
        );

        assertEquals("test-id", session.getId());
        assertEquals("Test Session", session.getName());
        assertEquals(1000L, session.getStartTime());
        assertEquals(2000L, session.getEndTime());
        assertEquals(42, session.getClickCount());
        assertEquals(85L, session.getConsistencyScore());
        assertEquals(45000.0, session.getAvgInterval(), 0.001);
        assertEquals(12.5, session.getAvgDistancePercent(), 0.001);
    }

    @Test
    public void testSessionSetName()
    {
        Session session = new Session(
            "test-id",
            "Original Name",
            1000L,
            2000L,
            10,
            50L,
            30000.0,
            0.0
        );

        session.setName("New Name");
        assertEquals("New Name", session.getName());
    }

}
