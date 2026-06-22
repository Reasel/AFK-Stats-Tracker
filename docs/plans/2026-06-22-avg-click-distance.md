# Avg Click Distance Metric Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a live "Avg Click Distance" metric — mean mouse-jump between consecutive clicks, shown as a percentage of the game canvas diagonal — alongside the existing consistency and interval metrics.

**Architecture:** The mouse listener records each click's `(x,y)` point in parallel with its timestamp. The plugin computes the average Euclidean pixel distance between consecutive clicks (pure helper), then normalizes it against the canvas diagonal to a 0–100% value (second pure helper). The `Session` model gains an `avgDistancePercent` field; the panel gains a stat card and a linear 0–100 `DistanceIndicator`.

**Tech Stack:** Java 11, RuneLite plugin API (`net.runelite.api.Client`, `MouseManager`), Swing panel UI, JUnit 4, Lombok, Gson. Build via `nix-shell -p jdk11 --run "./gradlew ..."`.

## Global Constraints

- Java 11 target. Build/test commands MUST be wrapped: `nix-shell -p jdk11 --run "./gradlew <task>"`.
- The new metric is a **percentage of the canvas diagonal**, range `0–100`, displayed as `"%.1f%%"`.
- Distance points are kept in **click (time) order — never sorted**.
- Fewer than 2 clicks, or canvas diagonal `<= 0`, → `0.0`.
- Follow existing code style: no Lombok on the touched classes beyond what's already there, 4-space indentation matching each file.

---

### Task 1: Capture click coordinates in the listener

**Files:**
- Modify: `src/main/java/com/afkstatstracker/MouseClickCounterListener.java`
- Test: `src/test/java/com/afkstatstracker/MouseClickCounterListenerTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `void addClick(int x, int y)` — records a timestamp **and** a `Point(x,y)`.
  - `void addClick()` — unchanged signature; now delegates to `addClick(0, 0)` so the timestamp and point lists stay equal length.
  - `List<Point> getClickPoints()` — defensive copy, in click order.
  - `mousePressed` records `mouseEvent.getX()`, `mouseEvent.getY()`.
  - `resetMouseClickCounterListener()` clears points too.

- [ ] **Step 1: Write the failing tests**

Add to `MouseClickCounterListenerTest.java` (add imports `java.awt.Point` and keep existing ones):

```java
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
```

Add the import at the top of the test file:

```java
import java.awt.Point;
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `nix-shell -p jdk11 --run "./gradlew test --tests com.afkstatstracker.MouseClickCounterListenerTest"`
Expected: FAIL — compile error / `cannot find symbol: method getClickPoints()` and `addClick(int,int)`.

- [ ] **Step 3: Implement the change**

Replace the full contents of `MouseClickCounterListener.java` with:

```java
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `nix-shell -p jdk11 --run "./gradlew test --tests com.afkstatstracker.MouseClickCounterListenerTest"`
Expected: PASS (all listener tests, old and new).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/afkstatstracker/MouseClickCounterListener.java src/test/java/com/afkstatstracker/MouseClickCounterListenerTest.java
git commit -m "feat: record click coordinates in mouse listener"
```

---

### Task 2: Distance calculation helpers in the plugin

**Files:**
- Modify: `src/main/java/com/afkstatstracker/AfkStatsTrackerPlugin.java`
- Test: `src/test/java/com/afkstatstracker/AfkStatsTrackerCalcTest.java` (create)

**Interfaces:**
- Consumes: `MouseClickCounterListener.getClickPoints()` (Task 1); `client.getCanvasWidth()`, `client.getCanvasHeight()`.
- Produces:
  - `double computeAverageDistance(List<Point> points)` — avg Euclidean pixel distance between consecutive points; `< 2` → `0.0`.
  - `double toDistancePercent(double avgPixelDist, int canvasWidth, int canvasHeight)` — `avgPixelDist / hypot(w,h) * 100`; diagonal `<= 0` → `0.0`.
  - `double getAverageClickDistance()` — live wrapper combining the two using the listener's points and the client's canvas size.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/afkstatstracker/AfkStatsTrackerCalcTest.java`:

```java
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `nix-shell -p jdk11 --run "./gradlew test --tests com.afkstatstracker.AfkStatsTrackerCalcTest"`
Expected: FAIL — `cannot find symbol: method computeAverageDistance` / `toDistancePercent`.

- [ ] **Step 3: Implement the helpers**

In `AfkStatsTrackerPlugin.java`, add the import near the other imports:

```java
import java.awt.Point;
```

Add these three methods inside the class (e.g. directly after `getAverageClickInterval()`):

```java
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `nix-shell -p jdk11 --run "./gradlew test --tests com.afkstatstracker.AfkStatsTrackerCalcTest"`
Expected: PASS (all 7 calc tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/afkstatstracker/AfkStatsTrackerPlugin.java src/test/java/com/afkstatstracker/AfkStatsTrackerCalcTest.java
git commit -m "feat: compute average click distance as % of canvas diagonal"
```

---

### Task 3: Add `avgDistancePercent` to Session and wire it in

**Files:**
- Modify: `src/main/java/com/afkstatstracker/Session.java`
- Modify: `src/main/java/com/afkstatstracker/AfkStatsTrackerPlugin.java` (`stopSession`)
- Test: `src/test/java/com/afkstatstracker/SessionTest.java`

**Interfaces:**
- Consumes: `getAverageClickDistance()` (Task 2).
- Produces: `Session` constructor gains a trailing `double avgDistancePercent` arg; `double getAvgDistancePercent()` getter.

> Note: this changes the `Session` constructor arity. The only other caller is `stopSession()` (updated in this task) and `SessionTest` (updated in this task). `SessionHistoryManager` deserializes via Gson and does not call the constructor directly — verify with a grep in Step 3.

- [ ] **Step 1: Update the failing test**

In `SessionTest.java`, update both `new Session(...)` calls to pass a trailing distance arg, and assert the getter in `testSessionCreation`.

`testSessionCreation` — change the constructor call and add an assertion:

```java
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
```

`testSessionSetName` — change its constructor call to add a trailing `0.0`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `nix-shell -p jdk11 --run "./gradlew test --tests com.afkstatstracker.SessionTest"`
Expected: FAIL — constructor arity mismatch / `cannot find symbol: method getAvgDistancePercent`.

- [ ] **Step 3: Implement the model change**

First confirm the only direct constructor caller is `stopSession`:

Run: `grep -rn "new Session(" src/main`
Expected: a single match in `AfkStatsTrackerPlugin.java` (`stopSession`).

Replace the full contents of `Session.java` with:

```java
package com.afkstatstracker;

public class Session
{
    private final String id;
    private String name;
    private final long startTime;
    private final long endTime;
    private final int clickCount;
    private final long consistencyScore;
    private final double avgInterval;
    private final double avgDistancePercent;

    public Session(String id, String name, long startTime, long endTime,
                   int clickCount, long consistencyScore, double avgInterval,
                   double avgDistancePercent)
    {
        this.id = id;
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.clickCount = clickCount;
        this.consistencyScore = consistencyScore;
        this.avgInterval = avgInterval;
        this.avgDistancePercent = avgDistancePercent;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public int getClickCount() { return clickCount; }
    public long getConsistencyScore() { return consistencyScore; }
    public double getAvgInterval() { return avgInterval; }
    public double getAvgDistancePercent() { return avgDistancePercent; }

}
```

Then in `AfkStatsTrackerPlugin.java`, update the `Session` construction inside `stopSession()` to pass the new value as the final argument:

```java
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `nix-shell -p jdk11 --run "./gradlew test --tests com.afkstatstracker.SessionTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/afkstatstracker/Session.java src/main/java/com/afkstatstracker/AfkStatsTrackerPlugin.java src/test/java/com/afkstatstracker/SessionTest.java
git commit -m "feat: store avg click distance on session"
```

---

### Task 4: Panel UI — stat card, indicator, history, clipboard

**Files:**
- Modify: `src/main/java/com/afkstatstracker/AfkStatsTrackerPanel.java`

**Interfaces:**
- Consumes: `plugin.getAverageClickDistance()` (Task 2); `Session.getAvgDistancePercent()` (Task 3).
- Produces: a new `DistanceIndicator` inner class and an `avgDistanceValueLabel` field; no external API.

> No unit tests — the panel is Swing UI with no existing test coverage. Verification is a clean full build plus the existing test suite passing.

- [ ] **Step 1: Add the field and the distance stat card**

Add a field beside the other value labels (near line 48):

```java
	private JLabel avgDistanceValueLabel;
```

Add a field beside the other indicators (near line 53):

```java
	private DistanceIndicator distanceIndicator;
```

In the constructor, after the `avgIntervalPanel` block (around line 110), add:

```java
		JPanel avgDistancePanel = createStatCard("Avg Click Distance",
			"Average distance the mouse moved between clicks, as a percentage of the canvas diagonal.");
		avgDistanceValueLabel = (JLabel) ((BorderLayout) avgDistancePanel.getLayout()).getLayoutComponent(BorderLayout.CENTER);
```

Where `intervalIndicator` is constructed (around line 113), add:

```java
		distanceIndicator = new DistanceIndicator();
```

In the `statsPanel.add(...)` sequence (around lines 115-119), add the new card and indicator after the interval indicator:

```java
		statsPanel.add(createSessionCard());
		statsPanel.add(consistencyPanel);
		statsPanel.add(consistencyIndicator);
		statsPanel.add(avgIntervalPanel);
		statsPanel.add(intervalIndicator);
		statsPanel.add(avgDistancePanel);
		statsPanel.add(distanceIndicator);
```

- [ ] **Step 2: Update `updateStats()`**

In `updateStats()` (around line 433), after the interval lines, add:

```java
		double avgDistance = plugin.getAverageClickDistance();
		avgDistanceValueLabel.setText(String.format("%.1f%%", avgDistance));
		distanceIndicator.setValue(avgDistance);
```

- [ ] **Step 3: Update the history row stats line and clipboard text**

In `createSessionRow(...)`, change the `statsLabel` format (around line 275) to include distance:

```java
		JLabel statsLabel = new JLabel(String.format("%d | %.0fms | %.1f%% | %d clicks",
			session.getConsistencyScore(),
			session.getAvgInterval(),
			session.getAvgDistancePercent(),
			session.getClickCount()));
```

In the copy icon's click handler (around line 292), add an `Avg Distance` line to the copied text:

```java
				String text = String.format(
					"Session: %s\nLength: %s\nConsistency: %d\nAvg Interval: %.0fms\nAvg Distance: %.1f%%\nClicks: %d",
					session.getName(), lengthStr,
					session.getConsistencyScore(),
					session.getAvgInterval(),
					session.getAvgDistancePercent(),
					session.getClickCount());
```

- [ ] **Step 4: Add the `DistanceIndicator` inner class**

Add this inner class beside `ConsistencyIndicator` / `IntervalIndicator` (before the closing brace of `AfkStatsTrackerPanel`). It is a linear 0–100 slider taking a `double`, with ticks every 25 and `%` labels at 0 and 100:

```java
	private static class DistanceIndicator extends JPanel
	{
		private static final int MARKER_SIZE = 8;
		private static final int PADDING_X = 12;
		private static final int TICK_HEIGHT = 4;
		private static final Color TRACK_COLOR = ColorScheme.MEDIUM_GRAY_COLOR;
		private static final Color TICK_COLOR = ColorScheme.LIGHT_GRAY_COLOR;
		private static final Color MARKER_COLOR = ColorScheme.BRAND_ORANGE;

		private double value = 0;

		DistanceIndicator()
		{
			setToolTipText("Average distance the mouse moved between clicks, as a percentage of the canvas diagonal.");
			setOpaque(false);
			setBorder(BorderFactory.createEmptyBorder(2, 5, 4, 5));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
			setPreferredSize(new Dimension(0, 36));
		}

		void setValue(double value)
		{
			this.value = Math.max(0, Math.min(100, value));
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			int w = getWidth();

			Font labelFont = g2.getFont().deriveFont(Font.PLAIN, 9f);
			g2.setFont(labelFont);
			int labelHeight = g2.getFontMetrics().getHeight();

			int trackLeft = PADDING_X;
			int trackRight = w - PADDING_X;
			int trackWidth = trackRight - trackLeft;
			int lineY = MARKER_SIZE + 2;

			// Draw horizontal line
			g2.setColor(TRACK_COLOR);
			g2.drawLine(trackLeft, lineY, trackRight, lineY);

			// Draw tick marks every 25 and labels at 0 and 100
			for (int i = 0; i <= 100; i += 25)
			{
				int x = trackLeft + (int) (trackWidth * i / 100.0);
				g2.setColor(TICK_COLOR);
				g2.drawLine(x, lineY - TICK_HEIGHT / 2, x, lineY + TICK_HEIGHT / 2);

				if (i == 0 || i == 100)
				{
					String label = i + "%";
					int labelWidth = g2.getFontMetrics().stringWidth(label);
					g2.setColor(Color.GRAY);
					g2.drawString(label, x - labelWidth / 2, lineY + TICK_HEIGHT / 2 + labelHeight);
				}
			}

			// Draw marker (triangle pointing down)
			int markerX = trackLeft + (int) (trackWidth * value / 100.0);
			int markerTop = lineY - MARKER_SIZE - 1;
			int[] xPoints = {markerX - MARKER_SIZE / 2, markerX + MARKER_SIZE / 2, markerX};
			int[] yPoints = {markerTop, markerTop, lineY - 1};
			g2.setColor(MARKER_COLOR);
			g2.fillPolygon(xPoints, yPoints, 3);

			g2.dispose();
		}
	}
```

- [ ] **Step 5: Build and run the full test suite**

Run: `nix-shell -p jdk11 --run "./gradlew build"`
Expected: `BUILD SUCCESSFUL`, all existing + new tests pass, panel compiles.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/afkstatstracker/AfkStatsTrackerPanel.java
git commit -m "feat: show avg click distance card and indicator in panel"
```

---

### Task 5: Screenshot + README refresh

**Files:**
- Modify: `panel-preview.png`, `README.md` (if it references the preview)

> Per project workflow: panel UI changed, so the preview image and README must be refreshed and the screenshot included in the PR description.

- [ ] **Step 1: Capture an updated panel screenshot**

Ask the user for a fresh screenshot of the panel showing the new Avg Click Distance card + indicator (the agent cannot launch the RuneLite client to capture it).

- [ ] **Step 2: Replace the preview image**

Save the new screenshot over `panel-preview.png` in the repo root. Confirm `README.md`'s image reference still points at `panel-preview.png` (no change needed if the filename is unchanged).

- [ ] **Step 3: Commit**

```bash
git add panel-preview.png README.md
git commit -m "docs: update panel preview with avg click distance"
```

---

## Self-Review

**Spec coverage:**
- Data capture (listener records points) → Task 1. ✓
- `computeAverageDistance` + `toDistancePercent` + `getAverageClickDistance` → Task 2. ✓
- `Session.avgDistancePercent` + `stopSession` wiring → Task 3. ✓
- Stat card, `DistanceIndicator` (linear 0–100), `updateStats`, history line, clipboard → Task 4. ✓
- Testing of both pure helpers → Task 2 (7 cases). ✓
- Follow-up screenshot/README → Task 5. ✓

**Type consistency:**
- `getClickPoints(): List<Point>` produced in Task 1, consumed in Task 2. ✓
- `computeAverageDistance(List<Point>)`, `toDistancePercent(double,int,int)`, `getAverageClickDistance()` named identically across Tasks 2–4. ✓
- `Session` constructor trailing `double avgDistancePercent` + `getAvgDistancePercent()` — defined Task 3, consumed Task 4. ✓
- Display format `"%.1f%%"` used in card, history line, and clipboard. ✓

**Placeholder scan:** none — all steps contain concrete code and exact commands.
