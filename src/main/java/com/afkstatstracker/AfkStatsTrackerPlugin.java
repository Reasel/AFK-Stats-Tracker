package com.afkstatstracker;

import com.google.gson.Gson;

import java.awt.Point;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "AFK Stats Tracker"
)
public class AfkStatsTrackerPlugin extends Plugin
{
	@Inject
	private Client client;


	@Inject
	private ClientToolbar clientToolbar;

    @Inject
    private MouseManager mouseManager;

    private MouseClickCounterListener mouseListener;

	private AfkStatsTrackerPanel panel;
	private NavigationButton navButton;

	private long startTime;
	private long endTime;
	private boolean isTracking = false;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	private SessionHistoryManager sessionHistoryManager;

	@Override
	protected void startUp() throws Exception
	{
		SessionHistoryManager.ConfigStorage storage = new SessionHistoryManager.ConfigStorage()
		{
			private static final String CONFIG_GROUP = "afkStatsTracker";
			private static final String CONFIG_KEY = "sessionHistory";

			@Override
			public String load()
			{
				return configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY);
			}

			@Override
			public void save(String json)
			{
				configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY, json);
			}
		};
		sessionHistoryManager = new SessionHistoryManager(storage, gson);

		log.info("AFK Stats Tracker plugin started!");

		panel = new AfkStatsTrackerPanel(this, sessionHistoryManager);

		// Add to toolbar
		navButton = NavigationButton.builder()
			.tooltip("AFK Stats Tracker")
			.icon(ImageUtil.loadImageResource(getClass(), "icon.png")) // Need to add icon
			.priority(Integer.MAX_VALUE)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);

        mouseListener = new MouseClickCounterListener();
        mouseManager.registerMouseListener(mouseListener);

	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("AFK Stats Tracker plugin stopped!");

		clientToolbar.removeNavigation(navButton);

	       mouseManager.unregisterMouseListener(mouseListener);
	       mouseListener = null;

	       panel.stopTimer();
	}

	public void startSession()
	{
		if (isTracking)
		{
			return;
		}

		mouseListener.resetMouseClickCounterListener();
		startTime = System.currentTimeMillis();
		isTracking = true;
		mouseManager.registerMouseListener(mouseListener);
	}

	public void stopSession()
	{
		if (!isTracking)
		{
			return;
		}

		mouseManager.unregisterMouseListener(mouseListener);
		endTime = System.currentTimeMillis();
		String id = UUID.randomUUID().toString();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
		String name = "Session " + dateFormat.format(new Date(startTime));

		Session session = new Session(
			id,
			name,
			startTime,
			endTime,
			getClickCount(),
			getConsistency(),
			getAverageClickInterval(),
			getAverageClickDistance()
		);

		sessionHistoryManager.addSession(session);
		isTracking = false;
	}

	public boolean isTracking()
	{
		return isTracking;
	}

	public long getSessionElapsedMs()
	{
		if (startTime == 0)
		{
			return 0;
		}
		if (isTracking)
		{
			return System.currentTimeMillis() - startTime;
		}
		return endTime - startTime;
	}

	public long getConsistency()
	{
		return computeConsistency(mouseListener.getClickCounter());
	}

    public long computeConsistency(List<Long> timestamps)
    {
        int n = timestamps.size();
        if (n < 2) return 0;

        List<Long> sorted = new ArrayList<>(timestamps);
        Collections.sort(sorted);

        List<Double> intervals = new ArrayList<>();
        for (int i = 1; i < n; i++)
        {
            intervals.add((double) (sorted.get(i) - sorted.get(i - 1)));
        }

        double totalSpan = sorted.get(n - 1) - sorted.get(0);
        int numIntervals = intervals.size();
        double meanInterval = totalSpan / numIntervals;

        double sumSqDiff = 0;
        for (double interval : intervals)
        {
            sumSqDiff += Math.pow(interval - meanInterval, 2);
        }
        double variance = sumSqDiff / (numIntervals - 1);
        double stdDev = Math.sqrt(variance);

		double cv = stdDev / meanInterval;
        return (long)(1 / (1 + cv) * 100);
    }

    public double getAverageClickInterval()
    {
        List<Long> timestamps = mouseListener.getClickCounter();
        int n = timestamps.size();
        if (n < 2) return 0.0;

        List<Long> sorted = new ArrayList<>(timestamps);
        Collections.sort(sorted);

        List<Double> intervals = new ArrayList<>();
        for (int i = 1; i < n; i++)
        {
            intervals.add((double) (sorted.get(i) - sorted.get(i - 1)));
        }

        double sum = 0.0;
        for (double interval : intervals)
        {
            sum += interval;
        }
        return sum / intervals.size();
    }

    public double computeAverageDistance(List<Point> points)
    {
        int n = points.size();
        if (n < 2) return 0.0;

        double sum = 0.0;
        for (int i = 1; i < n; i++)
        {
            sum += points.get(i).distance(points.get(i - 1));
        }
        return sum / (n - 1);
    }

    public double toDistancePercent(double avgPixelDist, int canvasWidth, int canvasHeight)
    {
        double diagonal = Math.hypot(canvasWidth, canvasHeight);
        if (diagonal <= 0) return 0.0;
        return avgPixelDist / diagonal * 100.0;
    }

    public double getAverageClickDistance()
    {
        double avgPx = computeAverageDistance(mouseListener.getClickPoints());
        return toDistancePercent(avgPx, client.getCanvasWidth(), client.getCanvasHeight());
    }

	public int getClickCount()
	{
		return mouseListener.getClickCounter().size();
	}

}