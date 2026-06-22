# Avg Click Distance Metric — Design

## Goal

Add a third live metric: **Average Click Distance** — the mean distance the mouse jumps between consecutive clicks, expressed as a **percentage of the game canvas diagonal**. Parallel to the existing Avg Click Interval metric (which measures *time* between clicks), this measures *distance* between clicks. Lets a user discuss how much they had to move their mouse during an AFK session, independent of resolution / display mode.

## Definition

For clicks recorded in time order with positions `p_1 … p_n` (pixels), and a canvas of width `w`, height `h`:

```
avgPixelDist = ( Σ distance(p_i, p_{i-1}) for i in 2..n ) / (n - 1)
diagonal     = sqrt(w² + h²)
avgDistancePercent = avgPixelDist / diagonal × 100
```

- `distance` = Euclidean: `sqrt(dx² + dy²)`.
- Normalized against the canvas **diagonal** — the maximum possible distance between two on-canvas points — so the value is a clean `0–100%` regardless of resolution or fixed/resizable mode.
- Points are in click order (time order), **not** sorted — distance is between consecutive clicks as they happened.
- Fewer than 2 clicks → `0.0`. Also `0.0` if `diagonal <= 0` (canvas not yet available, e.g. before login). Mirrors `getAverageClickInterval`'s `< 2` guard.
- Coordinates come from `MouseEvent.getPoint()`, relative to the game canvas. Canvas size comes from `client.getCanvasWidth()` / `client.getCanvasHeight()` (confirmed present in the RuneLite API).
- Caveat: if the canvas is resized mid-session, stored pixel distances are normalized by the canvas diagonal at compute time, introducing a small skew. Acceptable for an AFK stat; not corrected for.

## Components

### 1. Data capture — `MouseClickCounterListener`

Add a parallel `List<Point> clickPoints` alongside the existing `List<Long> clickTimestamps`.

- `mousePressed` records `mouseEvent.getPoint()` at the same call site that records the timestamp.
- New synchronized `getClickPoints()` returning a defensive copy.
- `resetMouseClickCounterListener()` clears both lists.

Rationale: a parallel list keeps the existing `List<Long>` timestamp API and the `computeConsistency` / `getAverageClickInterval` signatures untouched. No `Click(ts,x,y)` record type — that would churn the existing methods for no gain.

### 2. Calculation — `AfkStatsTrackerPlugin`

Split into a pure, testable helper plus a thin live wrapper — mirrors how `computeConsistency(List<Long>)` is separated from `getConsistency()` so the math is unit-testable without a live client.

Pure helper (no client dependency):

```
// Average Euclidean pixel distance between consecutive clicks. < 2 points → 0.
public double computeAverageDistance(List<Point> points)
{
    int n = points.size();
    if (n < 2) return 0.0;
    double sum = 0.0;
    for (int i = 1; i < n; i++) sum += points.get(i).distance(points.get(i - 1));
    return sum / (n - 1);
}

// Normalize an average pixel distance to % of the canvas diagonal. diagonal <= 0 → 0.
public double toDistancePercent(double avgPixelDist, int canvasWidth, int canvasHeight)
{
    double diagonal = Math.hypot(canvasWidth, canvasHeight);
    if (diagonal <= 0) return 0.0;
    return avgPixelDist / diagonal * 100.0;
}
```

Live wrapper:

```
public double getAverageClickDistance()
{
    double avgPx = computeAverageDistance(mouseListener.getClickPoints());
    return toDistancePercent(avgPx, client.getCanvasWidth(), client.getCanvasHeight());
}
```

Uses `java.awt.Point.distance(Point)` for the Euclidean calc and `Math.hypot` for the diagonal. No sorting (points already in click order).

### 3. Model — `Session`

Add `private final double avgDistancePercent;` with a getter, as the last constructor argument. `stopSession()` passes `getAverageClickDistance()`.

Backward compatibility: sessions persisted before this change have no `avgDistancePercent` in their JSON; gson deserializes the field to `0.0`. These render as `0.0%`. Acceptable — no migration needed.

### 4. UI — `AfkStatsTrackerPanel`

- New **"Avg Click Distance"** stat card (clone of the Avg Click Interval card), value formatted `"%.1f%%"`. Tooltip: "Average distance the mouse moved between clicks, as a percentage of the canvas diagonal."
- New inner class **`DistanceIndicator`** — a linear `0–100` indicator. Model it on `ConsistencyIndicator` (already a linear 0–100 slider) rather than the logarithmic `IntervalIndicator`, but take a `double` value:
  - Range `0–100%`. Ticks every `25` (`0 / 25 / 50 / 75 / 100`), labels at `0` and `100` with a `%` suffix.
  - `setValue(double)` clamps to `[0, 100]`.
- Place the new card + indicator after the interval card/indicator in `statsPanel`.
- `updateStats()` reads `plugin.getAverageClickDistance()`, updates the card label (`"%.1f%%"`) and `distanceIndicator.setValue(...)`.
- History row stats line (`createSessionRow`): extend the format to include distance, e.g. `"%d | %.0fms | %.1f%% | %d clicks"`.
- Copy-to-clipboard text: add an `Avg Distance: %.1f%%` line.

## Testing

Extend the existing test suite, targeting the two pure helpers (no client mock needed):

`computeAverageDistance(List<Point>)`:
- 0 points → `0.0`
- 1 point → `0.0`
- `(0,0) → (3,4) → (3,4)` → distances `5, 0` → avg `2.5`
- `(0,0) → (0,10) → (10,10)` → distances `10, 10` → avg `10.0`

`toDistancePercent(avgPixelDist, w, h)`:
- `w = h = 0` (or either ≤ 0) → `0.0`
- `avgPixelDist = 50`, canvas `300×400` (diagonal `500`) → `10.0`
- `avgPixelDist = 500`, canvas `300×400` → `100.0` (full-diagonal move)

Use a small delta for double assertions (e.g. `assertEquals(expected, actual, 1e-9)`).

## Scope

Files touched: `MouseClickCounterListener`, `AfkStatsTrackerPlugin`, `Session`, `AfkStatsTrackerPanel`, plus the test class. One new inner class (`DistanceIndicator`).

## Out of scope (YAGNI)

- Tracking the *full* mouse path (every `mouseMoved`/`mouseDragged`) — heavier, not what was asked.
- A consistency-style score for distance — only the average was requested.
- Correcting for mid-session canvas resize — normalize at compute time, accept the small skew.

## Follow-up

Panel UI changes → after implementation, capture an updated panel screenshot, refresh `panel-preview.png` + README reference, and include it in the PR description.
