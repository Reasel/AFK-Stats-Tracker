package com.afkstatstracker;

import com.google.gson.Gson;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ImageIcon;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.ui.PluginPanel;

public class AfkStatsTrackerPanel extends PluginPanel
{
	private final AfkStatsTrackerPlugin plugin;
	private final SessionHistoryManager sessionHistoryManager;
	private final Gson gson;

	private Timer timer;
	private JButton startButton;
	private JButton stopButton;
	private JLabel consistencyValueLabel;
	private JLabel avgIntervalValueLabel;
	private JLabel avgDistanceValueLabel;
	private JLabel sessionElapsedLabel;
	private JLabel sessionClicksLabel;

	private ConsistencyIndicator consistencyIndicator;
	private IntervalIndicator intervalIndicator;
	private DistanceIndicator distanceIndicator;

	private JPanel historyContainer;
	private JScrollPane historyScrollPane;
	private boolean historyExpanded = true;

	public AfkStatsTrackerPanel(AfkStatsTrackerPlugin plugin, SessionHistoryManager sessionHistoryManager, Gson gson)
	{
		// wrap=false: we manage our own scroll so the buttons/stats stay pinned and only history data scrolls
		super(false);

		this.plugin = plugin;
		this.sessionHistoryManager = sessionHistoryManager;
		this.gson = gson;

		setLayout(new BorderLayout());

		timer = new Timer(1000, e -> updateStats());
		timer.setRepeats(true);

		// Pinned top panel (buttons + stats + history header) — never scrolls
		JPanel topPanel = new JPanel();
		topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));

		// Button panel
		JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 5, 0));
		startButton = new JButton("Start Session");
		stopButton = new JButton("Stop Session");
		stopButton.setEnabled(false);

		startButton.addActionListener(e -> {
			plugin.startSession();
			updateStats();
			timer.start();
			startButton.setEnabled(false);
			stopButton.setEnabled(true);
		});

		stopButton.addActionListener(e -> {
			timer.stop();
			plugin.stopSession();
			startButton.setEnabled(true);
			stopButton.setEnabled(false);
			updateStats();
			refreshHistoryPanel();
		});

		buttonPanel.add(startButton);
		buttonPanel.add(stopButton);
		buttonPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, buttonPanel.getPreferredSize().height));

		// Stats panel
		JPanel statsPanel = new JPanel();
		statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));
		statsPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 5, 0));

		JPanel consistencyPanel = createStatCard("Consistency",
			"Score (0-100) indicating how consistent click intervals are; higher means more regular timing.");
		consistencyValueLabel = (JLabel) ((BorderLayout) consistencyPanel.getLayout()).getLayoutComponent(BorderLayout.CENTER);

		JPanel avgIntervalPanel = createStatCard("Avg Click Interval",
			"Average time between clicks in seconds");
		avgIntervalValueLabel = (JLabel) ((BorderLayout) avgIntervalPanel.getLayout()).getLayoutComponent(BorderLayout.CENTER);

		JPanel avgDistancePanel = createStatCard("Avg Click Distance",
			"Average distance the mouse moved between clicks, as a percentage of the canvas diagonal.");
		avgDistanceValueLabel = (JLabel) ((BorderLayout) avgDistancePanel.getLayout()).getLayoutComponent(BorderLayout.CENTER);

		consistencyIndicator = new ConsistencyIndicator();
		intervalIndicator = new IntervalIndicator();
		distanceIndicator = new DistanceIndicator();

		statsPanel.add(createSessionCard());
		statsPanel.add(consistencyPanel);
		statsPanel.add(consistencyIndicator);
		statsPanel.add(avgIntervalPanel);
		statsPanel.add(intervalIndicator);
		statsPanel.add(avgDistancePanel);
		statsPanel.add(distanceIndicator);
		statsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, statsPanel.getPreferredSize().height));

		// History header (pinned) + scrollable data area
		JPanel historyHeader = createHistoryHeader();

		historyContainer = new ScrollablePanel();

		historyScrollPane = new JScrollPane(historyContainer);
		historyScrollPane.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
		historyScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		refreshHistoryPanel();

		topPanel.add(buttonPanel);
		topPanel.add(statsPanel);
		topPanel.add(historyHeader);

		add(topPanel, BorderLayout.NORTH);
		add(historyScrollPane, BorderLayout.CENTER);
	}

	private JPanel createStatCard(String title, String tooltip)
	{
		JPanel card = new JPanel(new BorderLayout());
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.DARK_GRAY_COLOR),
			BorderFactory.createEmptyBorder(8, 5, 8, 5)
		));
		card.setToolTipText(tooltip);

		JLabel titleLabel = new JLabel(title);
		titleLabel.setForeground(Color.GRAY);
		card.add(titleLabel, BorderLayout.NORTH);

		JLabel valueLabel = new JLabel("0");
		valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 20f));
		valueLabel.setHorizontalAlignment(SwingConstants.LEFT);
		card.add(valueLabel, BorderLayout.CENTER);

		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	private JPanel createSessionCard()
	{
		JPanel card = new JPanel(new BorderLayout());
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.DARK_GRAY_COLOR),
			BorderFactory.createEmptyBorder(8, 5, 8, 5)
		));
		card.setToolTipText("Elapsed time and click count for the current session");

		JLabel titleLabel = new JLabel("Session");
		titleLabel.setForeground(Color.GRAY);
		card.add(titleLabel, BorderLayout.NORTH);

		JPanel valueRow = new JPanel();
		valueRow.setLayout(new BoxLayout(valueRow, BoxLayout.X_AXIS));

		sessionElapsedLabel = new JLabel("0s");
		sessionElapsedLabel.setFont(sessionElapsedLabel.getFont().deriveFont(Font.BOLD, 20f));
		sessionElapsedLabel.setAlignmentY(Component.BOTTOM_ALIGNMENT);

		sessionClicksLabel = new JLabel(" · 0 clicks");
		sessionClicksLabel.setForeground(Color.GRAY);
		sessionClicksLabel.setAlignmentY(Component.BOTTOM_ALIGNMENT);

		valueRow.add(sessionElapsedLabel);
		valueRow.add(sessionClicksLabel);
		card.add(valueRow, BorderLayout.CENTER);

		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	private JPanel createHistoryHeader()
	{
		JPanel header = new JPanel(new BorderLayout());
		header.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

		JLabel headerLabel = new JLabel("▼ Session History");
		headerLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		headerLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				historyExpanded = !historyExpanded;
				headerLabel.setText((historyExpanded ? "▼" : "▶") + " Session History");
				historyScrollPane.setVisible(historyExpanded);
				revalidate();
			}
		});
		header.add(headerLabel, BorderLayout.WEST);
		return header;
	}

	public void refreshHistoryPanel()
	{
		historyContainer.removeAll();

		List<Session> sessions = sessionHistoryManager.getSessions();
		Collections.reverse(sessions);

		for (Session session : sessions)
		{
			historyContainer.add(createSessionRow(session));
			historyContainer.add(Box.createRigidArea(new Dimension(0, 3)));
		}

		if (sessions.isEmpty())
		{
			JLabel emptyLabel = new JLabel("No sessions recorded");
			emptyLabel.setForeground(Color.GRAY);
			historyContainer.add(emptyLabel);
		}

		historyContainer.revalidate();
		historyContainer.repaint();
	}

	private JPanel createSessionRow(Session session)
	{
		JPanel row = new JPanel(new BorderLayout(5, 2));
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			BorderFactory.createEmptyBorder(5, 5, 5, 5)
		));

		// Name (editable on click)
		String displayName = session.getName();
		if (displayName.length() > 30)
		{
			displayName = displayName.substring(0, 27) + "...";
		}

		JLabel nameLabel = new JLabel(displayName);
		nameLabel.setToolTipText(session.getName());
		nameLabel.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
		nameLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				makeEditable(row, session, nameLabel);
			}
		});

		// Stats line
		JLabel statsLabel = new JLabel(String.format("%d | %.0fms | %.1f%% | %d clicks",
			session.getConsistencyScore(),
			session.getAvgInterval(),
			session.getAvgDistancePercent(),
			session.getClickCount()));
		statsLabel.setForeground(Color.GRAY);

		// Copy icon
		JLabel copyIcon = createHoverIcon(
			new ImageIcon(ImageUtil.loadImageResource(AfkStatsTrackerPanel.class, "copy.png")),
			"Copy session");
		copyIcon.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				JPopupMenu menu = new JPopupMenu();

				JMenuItem copyStats = new JMenuItem("Copy stats");
				copyStats.addActionListener(a -> copyToClipboard(formatSessionText(session)));
				menu.add(copyStats);

				JMenuItem copyJson = new JMenuItem("Copy JSON");
				copyJson.addActionListener(a -> copyToClipboard(gson.toJson(roundForExport(session))));
				menu.add(copyJson);

				menu.show(copyIcon, e.getX(), e.getY());
			}
		});

		// Delete icon
		JLabel deleteIcon = createHoverIcon(
			new ImageIcon(ImageUtil.loadImageResource(AfkStatsTrackerPanel.class, "delete.png")),
			"Delete session");
		deleteIcon.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				int choice = JOptionPane.showConfirmDialog(
					AfkStatsTrackerPanel.this,
					"Delete \"" + session.getName() + "\"?",
					"Delete Session",
					JOptionPane.YES_NO_OPTION);
				if (choice == JOptionPane.YES_OPTION)
				{
					sessionHistoryManager.deleteSession(session.getId());
					refreshHistoryPanel();
				}
			}
		});

		// Icons panel
		JPanel iconsPanel = new JPanel();
		iconsPanel.setLayout(new BoxLayout(iconsPanel, BoxLayout.X_AXIS));
		iconsPanel.add(copyIcon);
		iconsPanel.add(Box.createRigidArea(new Dimension(5, 0)));
		iconsPanel.add(deleteIcon);

		JPanel textPanel = new JPanel(new GridLayout(2, 1));
		textPanel.add(nameLabel);
		textPanel.add(statsLabel);

		row.add(textPanel, BorderLayout.CENTER);
		row.add(iconsPanel, BorderLayout.EAST);

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private JLabel createHoverIcon(ImageIcon imageIcon, String tooltip)
	{
		JLabel icon = new JLabel(imageIcon)
		{
			private boolean hovered = false;

			{
				addMouseListener(new MouseAdapter()
				{
					@Override
					public void mouseEntered(MouseEvent e)
					{
						hovered = true;
						repaint();
					}

					@Override
					public void mouseExited(MouseEvent e)
					{
						hovered = false;
						repaint();
					}
				});
			}

			@Override
			protected void paintComponent(Graphics g)
			{
				if (hovered)
				{
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					g2.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
					g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
					g2.dispose();
				}
				super.paintComponent(g);
			}
		};
		icon.setToolTipText(tooltip);
		icon.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
		return icon;
	}

	private String formatSessionText(Session session)
	{
		String lengthStr = DurationFormatter.format(session.getEndTime() - session.getStartTime());
		return String.format(
			"Session: %s\nLength: %s\nConsistency: %d\nAvg Interval: %.0fms\nAvg Distance: %.1f%%\nClicks: %d",
			session.getName(), lengthStr,
			session.getConsistencyScore(),
			session.getAvgInterval(),
			session.getAvgDistancePercent(),
			session.getClickCount());
	}

	private void copyToClipboard(String text)
	{
		Toolkit.getDefaultToolkit().getSystemClipboard()
			.setContents(new StringSelection(text), null);
	}

	static Session roundForExport(Session session)
	{
		return new Session(
			session.getId(),
			session.getName(),
			session.getStartTime(),
			session.getEndTime(),
			session.getClickCount(),
			session.getConsistencyScore(),
			Math.round(session.getAvgInterval() * 100) / 100.0,
			Math.round(session.getAvgDistancePercent() * 100) / 100.0);
	}

	private void makeEditable(JPanel row, Session session, JLabel nameLabel)
	{
		JTextField textField = new JTextField(session.getName());
		textField.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
		Dimension pref = textField.getPreferredSize();
		textField.setPreferredSize(new Dimension(pref.width, pref.height + 5));
		textField.selectAll();

		Runnable saveAndRestore = () -> {
			String newName = textField.getText().trim();
			if (!newName.isEmpty() && !newName.equals(session.getName()))
			{
				sessionHistoryManager.renameSession(session.getId(), newName);
			}
			refreshHistoryPanel();
		};

		textField.addActionListener(e -> saveAndRestore.run());
		textField.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				saveAndRestore.run();
			}
		});
		textField.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
				{
					refreshHistoryPanel();
				}
			}
		});

		JPanel textPanel = (JPanel) row.getComponent(0);
		textPanel.remove(nameLabel);
		textPanel.add(textField, 0);
		textPanel.revalidate();
		textField.requestFocusInWindow();
	}

	public void updateStats()
	{
		sessionElapsedLabel.setText(DurationFormatter.format(plugin.getSessionElapsedMs()));
		sessionClicksLabel.setText(" · " + plugin.getClickCount() + " clicks");
		int consistency = (int) plugin.getConsistency();
		consistencyValueLabel.setText(String.valueOf(consistency));
		consistencyIndicator.setValue(consistency);
		double avgInterval = plugin.getAverageClickInterval();
		avgIntervalValueLabel.setText(String.format("%.0f ms", avgInterval));
		intervalIndicator.setValue(avgInterval);
		double avgDistance = plugin.getAverageClickDistance();
		avgDistanceValueLabel.setText(String.format("%.1f%%", avgDistance));
		distanceIndicator.setValue(avgDistance);
	}

	public void stopTimer()
	{
		if (timer != null)
		{
			timer.stop();
		}
	}

	// Vertical list whose width follows the viewport, so rows shrink/truncate instead of forcing a horizontal scrollbar
	private static class ScrollablePanel extends JPanel implements Scrollable
	{
		ScrollablePanel()
		{
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return visibleRect.height;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	private static class ConsistencyIndicator extends JPanel
	{
		private static final int MARKER_SIZE = 8;
		private static final int PADDING_X = 12;
		private static final int TICK_HEIGHT = 4;
		private static final Color TRACK_COLOR = ColorScheme.MEDIUM_GRAY_COLOR;
		private static final Color TICK_COLOR = ColorScheme.LIGHT_GRAY_COLOR;
		private static final Color MARKER_COLOR = ColorScheme.BRAND_ORANGE;

		private int value = 0;

		ConsistencyIndicator()
		{
			setToolTipText("Score (0-100) indicating how consistent click intervals are; higher means more regular timing.");
			setOpaque(false);
			setBorder(BorderFactory.createEmptyBorder(2, 5, 4, 5));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
			setPreferredSize(new Dimension(0, 36));
		}

		void setValue(int value)
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
			int h = getHeight();

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

			// Draw tick marks every 10 and labels at 0 and 100
			g2.setFont(labelFont);
			for (int i = 0; i <= 100; i += 10)
			{
				int x = trackLeft + (int) (trackWidth * i / 100.0);
				g2.setColor(TICK_COLOR);
				g2.drawLine(x, lineY - TICK_HEIGHT / 2, x, lineY + TICK_HEIGHT / 2);

				if (i == 0 || i == 100)
				{
					String label = String.valueOf(i);
					int labelWidth = g2.getFontMetrics().stringWidth(label);
					int labelX = (i == 0) ? x - labelWidth / 2 : x - labelWidth / 2;
					g2.setColor(Color.GRAY);
					g2.drawString(label, labelX, lineY + TICK_HEIGHT / 2 + labelHeight);
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

	private static class IntervalIndicator extends JPanel
	{
		private static final int MARKER_SIZE = 8;
		private static final int PADDING_X = 12;
		private static final int TICK_HEIGHT = 4;
		private static final double MIN_MS = 0;
		private static final double MAX_MS = 150000;
		private static final Color TRACK_COLOR = ColorScheme.MEDIUM_GRAY_COLOR;
		private static final Color TICK_COLOR = ColorScheme.LIGHT_GRAY_COLOR;
		private static final Color MARKER_COLOR = ColorScheme.BRAND_ORANGE;

		private static final double LOG_FLOOR = 100;
		// Logarithmic tick values in ms
		private static final int[] TICK_VALUES = {0, 1000, 2000, 5000, 10000, 20000, 50000, 100000, 150000};
		// Labels to display (subset to avoid crowding)
		private static final int[] LABEL_VALUES = {0, 5000, 50000, 150000};

		private double value = 0;

		IntervalIndicator()
		{
			setToolTipText("Average time between clicks in seconds");
			setOpaque(false);
			setBorder(BorderFactory.createEmptyBorder(2, 5, 4, 5));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
			setPreferredSize(new Dimension(0, 36));
		}

		void setValue(double value)
		{
			this.value = Math.max(MIN_MS, Math.min(MAX_MS, value));
			repaint();
		}

		private double toLogPosition(double ms)
		{
			double clamped = Math.max(MIN_MS, Math.min(MAX_MS, ms));
			double logMin = Math.log(LOG_FLOOR);
			double logMax = Math.log(MAX_MS);
			if (clamped <= LOG_FLOOR)
			{
				return 0;
			}
			return (Math.log(clamped) - logMin) / (logMax - logMin);
		}

		private String formatMs(int ms)
		{
			if (ms == 0)
			{
				return "0";
			}
			if (ms >= 1000)
			{
				return (ms % 1000 == 0) ? (ms / 1000) + "s" : String.format("%.1fs", ms / 1000.0);
			}
			return ms + "ms";
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			int w = getWidth();
			int h = getHeight();

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

			// Draw tick marks at logarithmic intervals
			java.util.Set<Integer> labelSet = new java.util.HashSet<>();
			for (int v : LABEL_VALUES)
			{
				labelSet.add(v);
			}

			for (int tickMs : TICK_VALUES)
			{
				double pos = toLogPosition(tickMs);
				int x = trackLeft + (int) (trackWidth * pos);
				g2.setColor(TICK_COLOR);
				g2.drawLine(x, lineY - TICK_HEIGHT / 2, x, lineY + TICK_HEIGHT / 2);

				if (labelSet.contains(tickMs))
				{
					String label = formatMs(tickMs);
					int labelWidth = g2.getFontMetrics().stringWidth(label);
					g2.setColor(Color.GRAY);
					g2.drawString(label, x - labelWidth / 2, lineY + TICK_HEIGHT / 2 + labelHeight);
				}
			}

			// Draw marker (triangle pointing down)
			{
				double pos = toLogPosition(value);
				int markerX = trackLeft + (int) (trackWidth * pos);
				int markerTop = lineY - MARKER_SIZE - 1;
				int[] xPoints = {markerX - MARKER_SIZE / 2, markerX + MARKER_SIZE / 2, markerX};
				int[] yPoints = {markerTop, markerTop, lineY - 1};
				g2.setColor(MARKER_COLOR);
				g2.fillPolygon(xPoints, yPoints, 3);
			}

			g2.dispose();
		}
	}

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
}
