# Avg Click Distance Metric — Design

## Goal

Add a third live metric: **Average Click Distance** — the mean Euclidean distance (in pixels) the mouse jumps between consecutive clicks. Parallel to the existing Avg Click Interval metric (which measures *time* between clicks), this measures *distance* between clicks. Lets a user discuss how much they had to move their mouse during an AFK session.

## Definition

For clicks recorded in time order with positions `p_1 … p_n`:

```
avgDistance = ( Σ distance(p_i, p_{i-1}) for i in 2..n ) / (n - 1)
```

- `distance` = Euclidean: `sqrt(dx² + dy²)`.
- Points are in click order (time order), **not** sorted — distance is between consecutive clicks as they happened.
- Fewer than 2 clicks → `0.0` (no pair to measure), mirroring `getAverageClickInterval`.
- Coordinates come from `MouseEvent.getPoint()`, relative to the RuneLite game canvas. Pixel distances therefore vary with canvas size / display mode; this is inherent and not corrected for.

## Components

### 1. Data capture — `MouseClickCounterListener`

Add a parallel `List<Point> clickPoints` alongside the existing `List<Long> clickTimestamps`.

- `mousePressed` records `mouseEvent.getPoint()` at the same call site that records the timestamp.
- New synchronized `getClickPoints()` returning a defensive copy.
- `resetMouseClickCounterListener()` clears both lists.

Rationale: a parallel list keeps the existing `List<Long>` timestamp API and the `computeConsistency` / `getAverageClickInterval` signatures untouched. No `Click(ts,x,y)` record type — that would churn the existing methods for no gain.

### 2. Calculation — `AfkStatsTrackerPlugin`

New `public double getAverageClickDistance()`, mirroring `getAverageClickInterval`:

```
List<Point> points = mouseListener.getClickPoints();
int n = points.size();
if (n < 2) return 0.0;
double sum = 0.0;
for (int i = 1; i < n; i++) sum += points.get(i).distance(points.get(i - 1));
return sum / (n - 1);
```

Uses `java.awt.Point.distance(Point)` for the Euclidean calc. No sorting (points already in click order).

### 3. Model — `Session`

Add `private final double avgDistance;` with a getter, as the last constructor argument. `stopSession()` passes `getAverageClickDistance()`.

Backward compatibility: sessions persisted before this change have no `avgDistance` in their JSON; gson deserializes the field to `0.0`. These render as `0 px`. Acceptable — no migration needed.

### 4. UI — `AfkStatsTrackerPanel`

- New **"Avg Click Distance"** stat card (clone of the Avg Click Interval card), value formatted `"%.0f px"`. Tooltip: "Average distance the mouse moved between clicks, in pixels."
- New inner class **`DistanceIndicator`** — a clone of `IntervalIndicator` using a **linear** scale (not logarithmic):
  - Range `0–1000 px`. Ticks at `0 / 250 / 500 / 750 / 1000`, labels at each.
  - Linear because click distances are bounded by canvas size — no orders-of-magnitude spread that would justify a log scale.
  - The `1000 px` ceiling is an initial guess; tune if real-world averages cluster at the low end.
- Place the new card + indicator after the interval card/indicator in `statsPanel`.
- `updateStats()` reads `plugin.getAverageClickDistance()`, updates the card label and `distanceIndicator.setValue(...)`.
- History row stats line (`createSessionRow`): extend the format to include distance, e.g. `"%d | %.0fms | %.0fpx | %d clicks"`.
- Copy-to-clipboard text: add an `Avg Distance: %.0fpx` line.

## Testing

Extend the existing test suite (mirroring the interval tests):

- 0 clicks → `0.0`
- 1 click → `0.0`
- Known points, e.g. `(0,0) → (3,4) → (3,4)` → distances `5, 0` → avg `2.5`
- A horizontal/vertical case with an exact expected average

## Scope

Files touched: `MouseClickCounterListener`, `AfkStatsTrackerPlugin`, `Session`, `AfkStatsTrackerPanel`, plus the test class. One new inner class (`DistanceIndicator`).

## Out of scope (YAGNI)

- Tracking the *full* mouse path (every `mouseMoved`/`mouseDragged`) — heavier, not what was asked.
- A consistency-style score for distance — only the average was requested.
- Configurable indicator scale — hardcode `1000 px`, revisit only if needed.

## Follow-up

Panel UI changes → after implementation, capture an updated panel screenshot, refresh `panel-preview.png` + README reference, and include it in the PR description.
