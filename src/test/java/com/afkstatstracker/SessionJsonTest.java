package com.afkstatstracker;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SessionJsonTest
{
	@Test
	public void sessionSerializesWithWebsiteContractFieldNames()
	{
		Session session = new Session("id-1", "Test", 1000L, 61000L, 42, 87L, 1500.5, 12.3);
		JsonObject json = new JsonParser().parse(new Gson().toJson(session)).getAsJsonObject();

		assertTrue(json.has("name"));
		assertTrue(json.has("startTime"));
		assertTrue(json.has("endTime"));
		assertTrue(json.has("clickCount"));
		assertTrue(json.has("consistencyScore"));
		assertTrue(json.has("avgInterval"));
		assertTrue(json.has("avgDistancePercent"));
		assertEquals(42, json.get("clickCount").getAsInt());
		assertEquals(87L, json.get("consistencyScore").getAsLong());
		assertEquals(1000L, json.get("startTime").getAsLong());
	}

	@Test
	public void roundForExportRoundsFloatsToTwoDecimals()
	{
		Session session = new Session("id-1", "Test", 1000L, 61000L, 7, 68L, 521.1666666666666, 4.014945489298668);
		Session rounded = AfkStatsTrackerPanel.roundForExport(session);

		assertEquals(521.17, rounded.getAvgInterval(), 0.0);
		assertEquals(4.01, rounded.getAvgDistancePercent(), 0.0);
		assertEquals(7, rounded.getClickCount());
		assertEquals(68L, rounded.getConsistencyScore());
	}
}
