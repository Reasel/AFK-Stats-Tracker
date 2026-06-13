package com.afkstatstracker;

public final class DurationFormatter
{
    private DurationFormatter()
    {
    }

    public static String format(long durationMs)
    {
        long totalSeconds = Math.max(0, durationMs) / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0)
        {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        }
        if (minutes > 0)
        {
            return String.format("%dm %ds", minutes, seconds);
        }
        return String.format("%ds", seconds);
    }
}
