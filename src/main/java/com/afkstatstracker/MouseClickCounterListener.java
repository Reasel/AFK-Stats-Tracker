package com.afkstatstracker;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.input.MouseAdapter;

public class MouseClickCounterListener extends MouseAdapter
{
    private final List<Long> clickTimestamps = new ArrayList<>();
    private final List<Point> clickPoints = new ArrayList<>();

    @Override
    public MouseEvent mousePressed(MouseEvent mouseEvent)
    {
        addClick(mouseEvent.getX(), mouseEvent.getY());
        return mouseEvent;
    }

    public synchronized List<Long> getClickCounter()
    {
        return new ArrayList<>(clickTimestamps);
    }

    public synchronized List<Point> getClickPoints()
    {
        return new ArrayList<>(clickPoints);
    }

    public synchronized void addClick()
    {
        addClick(0, 0);
    }

    public synchronized void addClick(int x, int y)
    {
        clickTimestamps.add(System.currentTimeMillis());
        clickPoints.add(new Point(x, y));
    }

    public synchronized void resetMouseClickCounterListener()
    {
        this.clickTimestamps.clear();
        this.clickPoints.clear();
    }
}
