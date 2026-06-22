package com.afkstatstracker;

import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class SessionHistoryManagerTest
{
    private SessionHistoryManager manager;
    private TestConfigStorage storage;

    @Before
    public void setUp()
    {
        storage = new TestConfigStorage();
        manager = new SessionHistoryManager(storage, new Gson());
    }

    @Test
    public void testAddSession()
    {
        Session session = createTestSession("id1", "Session 1");
        manager.addSession(session);

        List<Session> sessions = manager.getSessions();
        assertEquals(1, sessions.size());
        assertEquals("Session 1", sessions.get(0).getName());
    }

    @Test
    public void testRenameSession()
    {
        Session session = createTestSession("id1", "Original");
        manager.addSession(session);

        manager.renameSession("id1", "Renamed");

        assertEquals("Renamed", manager.getSessions().get(0).getName());
    }

    @Test
    public void testDeleteSession()
    {
        manager.addSession(createTestSession("id1", "Session 1"));
        manager.addSession(createTestSession("id2", "Session 2"));

        manager.deleteSession("id1");

        List<Session> sessions = manager.getSessions();
        assertEquals(1, sessions.size());
        assertEquals("id2", sessions.get(0).getId());
    }

    @Test
    public void testMaxSessionsLimit()
    {
        for (int i = 0; i < 25; i++)
        {
            manager.addSession(createTestSession("id" + i, "Session " + i));
        }

        List<Session> sessions = manager.getSessions();
        assertEquals(20, sessions.size());
        // Oldest sessions (0-4) should be dropped, newest (5-24) kept
        assertEquals("id5", sessions.get(0).getId());
        assertEquals("id24", sessions.get(19).getId());
    }

    @Test
    public void testPersistence()
    {
        manager.addSession(createTestSession("id1", "Persisted"));

        // Create new manager with same storage
        SessionHistoryManager newManager = new SessionHistoryManager(storage, new Gson());

        List<Session> sessions = newManager.getSessions();
        assertEquals(1, sessions.size());
        assertEquals("Persisted", sessions.get(0).getName());
    }

    @Test
    public void testCorruptJsonReturnsEmptyList()
    {
        storage.setData("not valid json [[[");
        SessionHistoryManager newManager = new SessionHistoryManager(storage, new Gson());

        List<Session> sessions = newManager.getSessions();
        assertTrue(sessions.isEmpty());
    }

    private Session createTestSession(String id, String name)
    {
        return new Session(id, name, 1000L, 2000L, 10, 50L, 30000.0, 0.0);
    }

    // Simple test storage implementation
    static class TestConfigStorage implements SessionHistoryManager.ConfigStorage
    {
        private String data = null;

        @Override
        public String load()
        {
            return data;
        }

        @Override
        public void save(String json)
        {
            this.data = json;
        }

        public void setData(String data)
        {
            this.data = data;
        }
    }
}
