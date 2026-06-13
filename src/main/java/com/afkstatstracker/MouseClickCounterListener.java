package com.afkstatstracker;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.input.MouseAdapter;

public class MouseClickCounterListener extends MouseAdapter
{
    private final List<Long> clickTimestamps = new ArrayList<>();

    @Override
    public MouseEvent mousePressed(MouseEvent mouseEvent)
    {
        addClick();
        return mouseEvent;
    }

    public synchronized List<Long> getClickCounter()
    {
        return new ArrayList<>(clickTimestamps);
    }

    public synchronized void addClick()
    {
        clickTimestamps.add(System.currentTimeMillis());
    }

    public synchronized void resetMouseClickCounterListener()
    {
        this.clickTimestamps.clear();
    }
}
