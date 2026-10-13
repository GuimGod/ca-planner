/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.ui;

import com.caplanner.core.Bag;
import com.caplanner.core.GearView;
import com.caplanner.core.HowTo;
import com.caplanner.core.Lang;
import com.caplanner.core.Session;
import com.caplanner.core.Planner;
import com.caplanner.core.Report;
import com.caplanner.core.Requirements;
import com.caplanner.core.SetupText;
import com.caplanner.core.SpeedLimit;
import com.caplanner.core.Task;
import com.caplanner.core.TaskType;
import com.caplanner.core.Unlocks;
import com.caplanner.core.Tier;
import com.caplanner.core.Verdict;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The side panel: two tabs (Bosses, All), a search field, and the pages a click opens (a boss's
 * tasks, one task). It only draws the state it is given; it knows nothing about the game.
 * Every method must be called on the Swing thread.
 */
public class CaPlannerPanel extends PluginPanel
{
	/** What the panel asks of the plugin. Called on the Swing thread. */
	public interface Actions
	{
		/** Puts the boss's icon on the label, now or when it has loaded; leaves it empty when there is none. */
		void bossIcon(String boss, JLabel into);

		/** Puts an item's icon on the label, now or when it has loaded. */
		void itemIcon(int itemId, JLabel into);

		/**
		 * Asks for the gear for a boss or, with a task id, for that task of it; the answer comes
		 * later through {@link CaPlannerPanel#gear}, under the same key.
		 *
		 * @param taskId the task whose own rule the set must follow, or -1 for the boss alone
		 */
		void wantGear(String key, int generation, String boss, int taskId);

		/** 0 when the task asks nothing of the gear, 1 when the set is picked for its rule, 2 when its rule is beyond a set. */
		int ruleKind(int taskId);

		/** Whether there is a place to walk to for the boss. */
		boolean hasPlace(String boss);

		/** Whether the Shortest Path plugin is installed and on: without it nothing marks the way. */
		boolean canRoute();

		/** Whether the Inventory Setups plugin is installed and on: without it there is nowhere to put a setup. */
		boolean hasSetups();

		/** Marks the way to the boss on the map. */
		void route(String boss);

		/** Shows a setup of that name in the bank, when Inventory Setups has one. */
		void showSetup(String name);

		/** Opens a page in the player's browser. */
		void openLink(String url);

		/** One line about the account for a bug report (levels, Slayer task), with no name in it. */
		String accountLine();

		/** Whether a report can be sent straight to the plugin's author. */
		boolean canSendReports();

		/**
		 * Sends a report to the plugin's author, when the player asks for it.
		 *
		 * @param done told on the Swing thread whether it went
		 */
		void sendReport(String boss, String task, String words, String report, java.util.function.Consumer<Boolean> done);
	}

	/** The equipment slot index of each cell of the gear grid, row by row, as the game lays the slots out; -1 for a gap. */
	private static final int[] GRID = {-1, 0, -1, 1, 2, 13, 3, 4, 5, -1, 7, -1, 9, 10, 12};

	private static final int ICON = 26;
	private static final int PAD = 8;
	private static final int INNER = PluginPanel.PANEL_WIDTH + PluginPanel.SCROLLBAR_WIDTH - 2 * PAD;
	/** Width of what scrolls: the list leaves room for its scrollbar, so no text ends up under it. */
	private static final int CONTENT = INNER - 18;
	private static final int ROW_TEXT = CONTENT - 2 * 6 - 14;
	/** Task rows drawn at once in the "All" tab; the rest is reached by typing or filtering. */
	private static final int MAX_ROWS = 80;
	/** Swing's switch that stops a component from treating its text as HTML. */
	private static final String NO_HTML = "html.disable";

	private static final Color ACCENT = ColorScheme.BRAND_ORANGE;
	private static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color TEXT = new Color(225, 225, 225);
	private static final Color MUTED = new Color(150, 150, 150);
	private static final Color GOOD = new Color(86, 190, 100);
	private static final Color BAD = new Color(226, 86, 78);
	private static final Color GROUP = new Color(110, 150, 220);
	private static final Color BUTTON = new Color(58, 58, 58);
	private static final Color BUTTON_HOVER = new Color(74, 74, 74);

	private static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
	private static final Font BODY_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 13);
	private static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 10);

	/** The headings of a boss's page, by {@link Planner#section}. */
	private static final String[] SECTIONS = {"CAN DO NOW", "NEEDS A GROUP", "MISSING REQUIREMENTS", "DONE"};
	/** The dot of a task, by {@link Planner#section}: orange can be done, blue needs a group, red is blocked. */
	private static final Color[] DOTS = {ACCENT, GROUP, BAD, GOOD};

	/** The language everything is written in, and each task's objective in it. Set before a panel is made. */
	private static volatile Lang lang = Lang.none();
	private static volatile HowTo objectives = HowTo.none();

	/**
	 * Says which language panels made from now on write in.
	 *
	 * @param words          the plugin's own lines in that language; none for English
	 * @param taskObjectives each task's objective in that language; none for English
	 */
	public static void language(Lang words, HowTo taskObjectives)
	{
		lang = words == null ? Lang.none() : words;
		objectives = taskObjectives == null ? HowTo.none() : taskObjectives;
	}

	private enum Tab
	{
		BOSSES, ALL, UNLOCK, AUTOPILOT
	}

	private final JLabel points = label(" ", SMALL, MUTED);
	private final JLabel bank = label(" ", SMALL, MUTED);
	private final JButton bossesTab = flatButton("Bosses");
	private final JButton allTab = flatButton("All");
	private final JButton unlockTab = flatButton("Unlock");
	private final JButton autopilotTab = flatButton("Autopilot");
	/** What is written about each task: the wiki's text, and notes of the plugin's own. */
	private HowTo wikiText = HowTo.none();
	private HowTo ownNotes = HowTo.none();
	private Session.Lengths lengths;
	/** How long the player said they have; null until they say. */
	private Session.Time time;
	/** The boss the autopilot is at, and what the player set aside for this sitting. */
	private String autoBoss;
	private final Set<Integer> autoSkipped = new HashSet<>();
	private final Set<String> autoSkippedBosses = new HashSet<>();
	/** The task the autopilot last pointed at, to say so when the game marks it done. */
	private Task autoLast;
	/** The name of the task that was last done under the autopilot; said until the player moves on. */
	private String autoDone;
	private final JComboBox<String> sortBox = new JComboBox<>();
	private final JTextField search = new HintField(lang.of("Search"));
	private final JCheckBox pendingOnly = new JCheckBox(lang.of("Left to do only"), true);
	private final JCheckBox canOnly = new JCheckBox(lang.of("Can do now only"), false);
	private final JComboBox<String> tierBox = new JComboBox<>();
	private final JComboBox<String> typeBox = new JComboBox<>();
	private final JPanel filters = column();
	private final JPanel content = column();
	private final Actions icons;
	/** The gear last worked out for each boss; a boss that is not here has not been asked for yet. */
	private final Map<String, GearView> gear = new HashMap<>();
	private String notice;
	/** Goes up each time the sets are forgotten: an answer asked for before that is not kept. */
	private int gearGeneration;
	/** The card whose setup was last copied or created, and what to say about it. */
	private String setupDoneFor;
	private String setupDone;
	private boolean setupFailed;

	private Planner.State state = Planner.State.empty();
	private Tab tab = Tab.BOSSES;
	/** The boss whose page is open, or null. */
	private String openBoss;
	/** The task whose page is open, or null. */
	private Task openTask;
	/** The missing thing whose tasks are listed, in the Unlock tab; or null. */
	private String openUnlock;

	public CaPlannerPanel(Actions icons)
	{
		super(false);
		this.icons = icons;
		setLayout(new BorderLayout(0, PAD));
		setBorder(new EmptyBorder(PAD, PAD, PAD, PAD));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel top = column();
		JLabel title = new JLabel("CA PLANNER");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ACCENT);
		top.add(leftAligned(title));
		top.add(Box.createVerticalStrut(2));
		top.add(leftAligned(points));
		top.add(leftAligned(bank));
		top.add(Box.createVerticalStrut(PAD));

		JPanel tabs = new JPanel(new GridLayout(2, 2, 4, 4));
		tabs.setOpaque(false);
		tabs.setAlignmentX(Component.LEFT_ALIGNMENT);
		tabs.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
		tabs.setPreferredSize(new Dimension(INNER, 56));
		bossesTab.setToolTipText(lang.of("Tasks grouped by boss"));
		allTab.setToolTipText(lang.of("Every task, with filters"));
		bossesTab.addActionListener(e -> show(Tab.BOSSES));
		allTab.addActionListener(e -> show(Tab.ALL));
		unlockTab.setToolTipText(lang.of("What would open the most tasks"));
		unlockTab.addActionListener(e -> show(Tab.UNLOCK));
		tabs.add(bossesTab);
		tabs.add(allTab);
		tabs.add(unlockTab);
		autopilotTab.setToolTipText(lang.of("Say how long you have: it names the boss and the next task, one after another"));
		autopilotTab.addActionListener(e -> show(Tab.AUTOPILOT));
		tabs.add(autopilotTab);
		top.add(tabs);
		top.add(Box.createVerticalStrut(6));

		search.setFont(BODY);
		search.setBackground(CARD);
		search.setForeground(TEXT);
		search.setCaretColor(TEXT);
		search.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BUTTON), new EmptyBorder(5, 6, 5, 6)));
		search.setAlignmentX(Component.LEFT_ALIGNMENT);
		search.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		search.setPreferredSize(new Dimension(INNER, 28));
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				typed();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				typed();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				typed();
			}
		});
		top.add(search);

		for (JCheckBox box : List.of(pendingOnly, canOnly))
		{
			box.setOpaque(false);
			box.setForeground(TEXT);
			box.setFont(SMALL);
			box.setFocusPainted(false);
			box.addActionListener(e -> rebuild());
		}
		tierBox.addItem(lang.of("Every tier"));
		for (Tier t : Tier.values())
		{
			tierBox.addItem(lang.of(t.label()));
		}
		typeBox.addItem(lang.of("Every type"));
		for (TaskType t : TaskType.values())
		{
			if (t != TaskType.OTHER)
			{
				typeBox.addItem(lang.of(t.label()));
			}
		}
		sortBox.addItem(lang.of("Easiest first"));
		sortBox.addItem(lang.of("Most points first"));
		sortBox.addItem(lang.of("Best value first"));
		sortBox.setToolTipText(lang.of("Best value: the points, cut down when few players have done the task"));
		sortBox.setAlignmentX(Component.LEFT_ALIGNMENT);
		sortBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		for (JComboBox<String> box : List.of(tierBox, typeBox, sortBox))
		{
			box.setFont(SMALL);
			box.setFocusable(false);
			box.addActionListener(e -> rebuild());
		}
		JPanel boxes = new JPanel(new GridLayout(1, 2, 4, 0));
		boxes.setOpaque(false);
		boxes.setAlignmentX(Component.LEFT_ALIGNMENT);
		boxes.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		boxes.add(tierBox);
		boxes.add(typeBox);
		filters.add(Box.createVerticalStrut(4));
		filters.add(leftAligned(pendingOnly));
		filters.add(leftAligned(canOnly));
		filters.add(boxes);
		filters.add(Box.createVerticalStrut(3));
		filters.add(sortBox);
		filters.setAlignmentX(Component.LEFT_ALIGNMENT);
		top.add(filters);
		add(top, BorderLayout.NORTH);

		content.setOpaque(true);
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JPanel holder = new JPanel(new BorderLayout());
		holder.setBackground(ColorScheme.DARK_GRAY_COLOR);
		holder.add(content, BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(holder,
			ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(scroll, BorderLayout.CENTER);

		rebuild();
	}

	/**
	 * Says how much of the bank is known, without redrawing anything else.
	 *
	 * @param bankItems different items last seen in the bank, or -1 when it was never opened
	 */
	public void bankSeen(int bankItems)
	{
		bank.setText(lang.of(bankItems < 0 ? (state.tasks.isEmpty() ? " " : "Bank not seen yet: open it once") : "Bank: " + bankItems + " items seen"));
	}

	/** Something to say in place of the points while there are no tasks to show; null for nothing. */
	public void notice(String text)
	{
		notice = text;
		if (state.tasks.isEmpty())
		{
			points.setText(notice != null ? notice : "Log in to read your tasks");
		}
	}

	/** A setting of the plugin changed: what is open is drawn again. */
	public void settingsChanged()
	{
		setupDone = null;
		setupDoneFor = null;
		if (openBoss != null || openTask != null || tab == Tab.AUTOPILOT)
		{
			rebuild();
		}
	}

	/**
	 * Draws a new state.
	 *
	 * @param newState the tasks, the finished ones and what can be attempted; empty before login and after logout
	 */
	public void render(Planner.State newState)
	{
		state = newState;
		if (state.tasks.isEmpty())
		{
			points.setText(notice != null ? notice : "Log in to read your tasks");
		}
		else
		{
			points.setText(lang.of(Planner.points(state) + " / " + Planner.totalPoints(state) + " points · "
				+ Planner.canNow(state) + " tasks you can do now"));
		}
		if (openTask != null)
		{
			// the same task, as it is now
			Task again = null;
			for (Task t : state.tasks)
			{
				if (t.id == openTask.id)
				{
					again = t;
				}
			}
			openTask = again;
		}
		rebuild();
	}

	/** The answer to {@link Actions#wantGear}. */
	public void gear(String key, int generation, GearView view)
	{
		if (generation != gearGeneration)
		{
			return;
		}
		gear.put(key, view);
		String boss = key.contains("#") ? key.substring(0, key.indexOf('#')) : key;
		if (boss.equals(openBoss) || (openTask != null && boss.equals(openTask.boss)) || (tab == Tab.AUTOPILOT && boss.equals(autoBoss)))
		{
			rebuild();
		}
	}

	/** What the player owns, or can do, has changed: every set is worked out again when next looked at. */
	public void forgetGear()
	{
		gearGeneration++;
		gear.clear();
		if (openBoss != null || openTask != null || tab == Tab.AUTOPILOT)
		{
			rebuild();
		}
	}

	/** For the preview and the tests: puts the panel on a page as if the player had clicked there. */
	void open(boolean allTab, String typed, String boss, int taskId)
	{
		tab = allTab ? Tab.ALL : Tab.BOSSES;
		search.setText(typed == null ? "" : typed);
		openBoss = boss;
		openTask = null;
		for (Task t : state.tasks)
		{
			if (t.id == taskId)
			{
				openTask = t;
			}
		}
		rebuild();
	}

	/** For the preview: the Unlock tab, as if the player had clicked it. */
	void openUnlocks()
	{
		show(Tab.UNLOCK);
	}

	/** For the preview: the autopilot, as if the player had said how long they have. */
	void openAutopilot(Session.Time length)
	{
		time = length;
		show(Tab.AUTOPILOT);
	}

	private void show(Tab which)
	{
		tab = which;
		openBoss = null;
		openTask = null;
		openUnlock = null;
		rebuild();
	}

	private void typed()
	{
		// typing always searches the list, so leave whatever page was open
		openTask = null;
		openBoss = null;
		openUnlock = null;
		rebuild();
	}

	private void rebuild()
	{
		mark(bossesTab, tab == Tab.BOSSES);
		mark(allTab, tab == Tab.ALL);
		mark(unlockTab, tab == Tab.UNLOCK);
		mark(autopilotTab, tab == Tab.AUTOPILOT);
		filters.setVisible(tab == Tab.ALL && openTask == null);
		// the autopilot has nothing to search
		search.setVisible(tab != Tab.AUTOPILOT);
		content.removeAll();
		if (state.tasks.isEmpty())
		{
			content.add(leftAligned(wrapped("Nothing to show yet. The task list comes from the game after you log in.",
				BODY, MUTED, CONTENT)));
		}
		else if (openTask != null)
		{
			taskPage(openTask);
		}
		else if (tab == Tab.AUTOPILOT)
		{
			autopilotPage();
		}
		else if (tab == Tab.BOSSES && openBoss != null)
		{
			bossPage(openBoss);
		}
		else if (tab == Tab.BOSSES)
		{
			bossList();
		}
		else if (tab == Tab.UNLOCK)
		{
			unlockList();
		}
		else
		{
			allList();
		}
		content.revalidate();
		content.repaint();
		revalidate();
	}

	private void bossList()
	{
		List<Planner.BossRow> rows = Planner.bosses(state, search.getText());
		if (rows.isEmpty())
		{
			content.add(leftAligned(label("No boss with that name", SMALL, MUTED)));
			return;
		}
		for (Planner.BossRow r : rows)
		{
			String sub;
			Color colour = MUTED;
			if (r.left() == 0)
			{
				sub = "All " + r.total + " done";
				colour = GOOD;
			}
			else
			{
				sub = (r.canNow == 0 ? "None you can do now" : r.canNow + " you can do now")
					+ " · " + r.left() + " left · " + r.pointsLeft + " pts";
			}
			content.add(row(r.name, sub, r.left() == 0 || r.canNow == 0 ? MUTED : TEXT, colour, bossIcon(r.name), () ->
			{
				openBoss = r.name;
				rebuild();
			}));
			content.add(Box.createVerticalStrut(2));
		}
	}

	private void bossPage(String boss)
	{
		content.add(back("All bosses", () ->
		{
			openBoss = null;
			rebuild();
		}));
		content.add(Box.createVerticalStrut(4));
		JPanel title = new JPanel(new BorderLayout(6, 0));
		title.setOpaque(false);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		title.add(bossIcon(boss), BorderLayout.WEST);
		title.add(wrappedRaw(boss, BODY_BOLD, Color.WHITE, CONTENT - ICON - 6), BorderLayout.CENTER);
		content.add(title);
		content.add(Box.createVerticalStrut(6));
		content.add(gearCard(boss, null));
		List<Task> list = Planner.ofBoss(state, boss);
		int section = -1;
		for (Task t : list)
		{
			int s = Planner.section(state, t);
			if (s != section)
			{
				section = s;
				int count = 0;
				for (Task other : list)
				{
					if (Planner.section(state, other) == s)
					{
						count++;
					}
				}
				content.add(Box.createVerticalStrut(8));
				content.add(leftAligned(label(SECTIONS[s] + " (" + count + ")", SMALL, DOTS[s])));
				content.add(Box.createVerticalStrut(3));
			}
			content.add(taskRow(t, false));
			content.add(Box.createVerticalStrut(2));
		}
	}

	/** What is missing, the thing that opens the most first; or the tasks one missing thing is in the way of. */
	private void unlockList()
	{
		List<Unlocks.Unlock> all = Unlocks.of(state);
		if (openUnlock != null)
		{
			for (Unlocks.Unlock u : all)
			{
				if (u.what.equals(openUnlock))
				{
					content.add(back("Everything missing", () ->
					{
						openUnlock = null;
						rebuild();
					}));
					content.add(Box.createVerticalStrut(4));
					content.add(leftAligned(wrapped(u.what, BODY_BOLD, Color.WHITE, CONTENT)));
					content.add(Box.createVerticalStrut(3));
					content.add(leftAligned(wrapped(u.alone.size() + " of these " + u.tasks.size()
						+ " tasks need nothing else.", SMALL, MUTED, CONTENT)));
					content.add(Box.createVerticalStrut(6));
					List<Task> ordered = new ArrayList<>(u.alone);
					for (Task t : u.tasks)
					{
						if (!u.alone.contains(t))
						{
							ordered.add(t);
						}
					}
					for (Task t : ordered)
					{
						content.add(taskRow(t, true));
						content.add(Box.createVerticalStrut(2));
					}
					return;
				}
			}
			openUnlock = null;
		}
		String typedKey = Planner.key(search.getText());
		int shown = 0;
		for (Unlocks.Unlock u : all)
		{
			if (!typedKey.isEmpty() && !Planner.key(u.what).contains(typedKey))
			{
				continue;
			}
			String sub = u.alone.isEmpty() ? "Opens none by itself"
				: "Opens " + u.alone.size() + (u.alone.size() == 1 ? " task · " : " tasks · ") + points(u.alonePoints());
			if (u.tasks.size() > u.alone.size())
			{
				sub += " · helps with " + (u.tasks.size() - u.alone.size()) + " more";
			}
			JPanel dot = new JPanel(new BorderLayout());
			dot.setOpaque(false);
			dot.setBorder(new EmptyBorder(4, 0, 0, 0));
			dot.add(new Dot(u.alone.isEmpty() ? MUTED : ACCENT), BorderLayout.NORTH);
			content.add(row(lang.of(u.what), sub, TEXT, MUTED, dot, () ->
			{
				openUnlock = u.what;
				rebuild();
			}));
			content.add(Box.createVerticalStrut(2));
			shown++;
		}
		if (shown == 0)
		{
			content.add(leftAligned(wrapped(all.isEmpty() ? "Nothing is missing: every task left is one you can attempt."
				: "Nothing missing with that name.", BODY, MUTED, CONTENT)));
		}
	}

	private void allList()
	{
		Planner.Filter filter = new Planner.Filter();
		filter.sort = Planner.Sort.values()[Math.max(0, sortBox.getSelectedIndex())];
		filter.pendingOnly = pendingOnly.isSelected();
		filter.canOnly = canOnly.isSelected();
		filter.tier = tierBox.getSelectedIndex() <= 0 ? null : Tier.values()[tierBox.getSelectedIndex() - 1];
		filter.type = typeBox.getSelectedIndex() <= 0 ? null : TaskType.values()[typeBox.getSelectedIndex() - 1];
		filter.typed = search.getText();
		List<Task> list = Planner.all(state, filter);
		content.add(Box.createVerticalStrut(4));
		content.add(leftAligned(label(list.size() + (list.size() == 1 ? " task" : " tasks"), SMALL, MUTED)));
		content.add(Box.createVerticalStrut(4));
		int shown = 0;
		for (Task t : list)
		{
			if (shown == MAX_ROWS)
			{
				content.add(Box.createVerticalStrut(4));
				content.add(leftAligned(label("+ " + (list.size() - shown) + " more · type or filter to narrow", SMALL, MUTED)));
				break;
			}
			content.add(taskRow(t, true));
			content.add(Box.createVerticalStrut(2));
			shown++;
		}
	}

	private void taskPage(Task t)
	{
		String from = tab == Tab.AUTOPILOT ? "Autopilot" : tab == Tab.BOSSES ? (openBoss == null ? "All bosses" : openBoss)
			: tab == Tab.UNLOCK ? (openUnlock == null ? "Everything missing" : openUnlock) : "All tasks";
		content.add(back(from, () ->
		{
			openTask = null;
			rebuild();
		}));
		content.add(Box.createVerticalStrut(4));
		int section = Planner.section(state, t);
		Verdict verdict = state.verdict(t);
		JPanel card = column();
		card.setOpaque(true);
		card.setBackground(CARD);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, DOTS[section]), new EmptyBorder(PAD, PAD, PAD, PAD)));
		int width = CONTENT - 3 - 2 * PAD;
		card.add(leftAligned(wrappedRaw(t.name, BODY_BOLD, Color.WHITE, width)));
		card.add(Box.createVerticalStrut(3));
		card.add(leftAligned(wrappedRaw(t.boss, BODY, TEXT, width)));
		card.add(Box.createVerticalStrut(3));
		card.add(leftAligned(label(t.tier.label() + " · " + t.type.label() + " · " + points(t.points()), SMALL, MUTED)));
		card.add(Box.createVerticalStrut(8));
		card.add(leftAligned(label("OBJECTIVE", SMALL, ACCENT)));
		card.add(Box.createVerticalStrut(2));
		objective(card, t, width);
		card.add(Box.createVerticalStrut(8));
		String status = section == 3 ? "Done" : section == 0 ? "You can do this now"
			: section == 1 ? "You have what it needs, but not alone" : "You cannot do this yet";
		card.add(leftAligned(label(status, SMALL, DOTS[section])));
		if (section != 3)
		{
			for (String reason : verdict.reasons)
			{
				card.add(Box.createVerticalStrut(2));
				card.add(leftAligned(wrapped("· " + reason, BODY, TEXT, width)));
			}
		}
		double ease = state.ease(t);
		if (ease >= 0)
		{
			card.add(Box.createVerticalStrut(8));
			card.add(leftAligned(label("Done by " + percent(ease) + " of players", SMALL, MUTED)));
		}
		howTo(card, t, width);
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(card);
		if (section != 3)
		{
			content.add(Box.createVerticalStrut(6));
			content.add(gearCard(t.boss, t));
		}
	}

	/**
	 * The task's objective. In another language it is the plugin's own translation, which may be
	 * wrong: it says so, and the game's own words follow.
	 */
	private void objective(JPanel card, Task t, int width)
	{
		List<String> translated = objectives.of(t.id);
		if (translated.isEmpty())
		{
			card.add(leftAligned(wrappedRaw(t.description, BODY, TEXT, width)));
			return;
		}
		for (String paragraph : translated)
		{
			card.add(leftAligned(wrappedRaw(paragraph, BODY, TEXT, width)));
		}
		card.add(Box.createVerticalStrut(4));
		card.add(leftAligned(wrapped("Translated by the plugin: it may be wrong. The game's own words are below.", SMALL, BAD, width)));
		card.add(Box.createVerticalStrut(3));
		card.add(leftAligned(label("IN THE GAME'S WORDS", SMALL, MUTED)));
		card.add(Box.createVerticalStrut(2));
		card.add(leftAligned(wrappedRaw(t.description, SMALL, MUTED, width)));
	}

	/** What is written about how to go about the task: the wiki's text under its own name and licence, then the plugin's notes. */
	private void howTo(JPanel card, Task t, int width)
	{
		List<String> notes = ownNotes.of(t.id);
		if (!notes.isEmpty())
		{
			card.add(Box.createVerticalStrut(8));
			card.add(leftAligned(label("NOTES OF THIS PLUGIN", SMALL, ACCENT)));
			for (String paragraph : notes)
			{
				card.add(Box.createVerticalStrut(3));
				card.add(leftAligned(wrappedRaw(paragraph, SMALL, TEXT, width)));
			}
		}
		List<String> wiki = wikiText.of(t.id);
		if (!wiki.isEmpty())
		{
			card.add(Box.createVerticalStrut(8));
			card.add(leftAligned(label("HOW, FROM THE OSRS WIKI", SMALL, ACCENT)));
			for (String paragraph : wiki)
			{
				card.add(Box.createVerticalStrut(3));
				card.add(leftAligned(wrappedRaw(paragraph, SMALL, TEXT, width)));
			}
			card.add(Box.createVerticalStrut(4));
			card.add(leftAligned(wrapped("Text of the Old School RuneScape Wiki (oldschool.runescape.wiki) and its contributors, shortened, "
				+ "under the CC BY-NC-SA 3.0 licence.",
				SMALL, MUTED, width)));
			// the page the text is from: the credit a licence of that kind asks for
			final String page = "https://oldschool.runescape.wiki/w/" + java.net.URLEncoder.encode(t.name.replace(' ', '_'),
				java.nio.charset.StandardCharsets.UTF_8).replace("+", "_");
			card.add(Box.createVerticalStrut(3));
			card.add(action("Open this task on the wiki", "Opens the page the text above is from, in your browser",
				() -> icons.openLink(page)));
		}
	}

	/** Nobody is logged in: what the autopilot was at belongs to the account that left. */
	public void forgetSession()
	{
		time = null;
		autoBoss = null;
		autoLast = null;
		autoDone = null;
		autoSkipped.clear();
		autoSkippedBosses.clear();
	}

	/** The texts about each task and how long a sitting at each boss is. */
	public void guide(HowTo wiki, HowTo own, Session.Lengths bossLengths)
	{
		wikiText = wiki == null ? HowTo.none() : wiki;
		ownNotes = own == null ? HowTo.none() : own;
		lengths = bossLengths;
	}

	/** The autopilot: how long the player has, then the boss worth the most in that time and the next task there. */
	private void autopilotPage()
	{
		if (time == null)
		{
			content.add(leftAligned(wrapped("How long do you have?", BODY_BOLD, Color.WHITE, CONTENT)));
			content.add(Box.createVerticalStrut(3));
			content.add(leftAligned(wrapped("It names the boss worth the most in that time and the next task to go for there, "
				+ "one after another, among the tasks you can do now. It moves on when the game marks a task as done.",
				SMALL, MUTED, CONTENT)));
			for (Session.Time choice : Session.Time.values())
			{
				content.add(Box.createVerticalStrut(4));
				content.add(action(choice.label(), "Bosses that fit a sitting of that length", () ->
				{
					time = choice;
					autoBoss = null;
					autoLast = null;
					rebuild();
				}));
			}
			return;
		}
		Session.Lengths known = lengths;
		Session.Pick pick = known == null ? null : Session.next(state, known, time, autoBoss, autoSkipped, autoSkippedBosses);
		content.add(leftAligned(label(lang.of("FOR " + time.label()).toUpperCase(), SMALL, ACCENT)));
		content.add(Box.createVerticalStrut(4));
		if (autoLast != null && state.done.contains(autoLast.id))
		{
			autoDone = autoLast.name;
		}
		if (autoDone != null)
		{
			content.add(leftAligned(wrapped("Done: " + autoDone + ".", BODY, GOOD, CONTENT)));
			content.add(Box.createVerticalStrut(4));
		}
		if (pick == null)
		{
			autoBoss = null;
			content.add(leftAligned(wrapped(autoSkipped.isEmpty() && autoSkippedBosses.isEmpty()
				? "Nothing you can do now fits that time. Pick a longer one, or see the Unlock tab for what would open more."
				: "Nothing else fits that time besides what you set aside.", BODY, TEXT, CONTENT)));
			if (!autoSkipped.isEmpty() || !autoSkippedBosses.isEmpty())
			{
				content.add(Box.createVerticalStrut(4));
				content.add(action("Bring back what I set aside", "Tasks and bosses you skipped in this sitting", () ->
				{
					autoSkipped.clear();
					autoSkippedBosses.clear();
					rebuild();
				}));
			}
			content.add(Box.createVerticalStrut(4));
			content.add(action("Change the time", "Say again how long you have", () ->
			{
				// a new sitting: what was set aside comes back
				forgetSession();
				rebuild();
			}));
			return;
		}
		autoBoss = pick.boss;
		autoLast = pick.task;
		final Task now = pick.task;
		JPanel title = new JPanel(new BorderLayout(6, 0));
		title.setOpaque(false);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		title.add(bossIcon(pick.boss), BorderLayout.WEST);
		title.add(wrappedRaw(pick.boss, BODY_BOLD, Color.WHITE, CONTENT - ICON - 6), BorderLayout.CENTER);
		content.add(title);
		content.add(Box.createVerticalStrut(2));
		int here = pick.after.size() + 1;
		content.add(leftAligned(label(here + (here == 1 ? " task" : " tasks") + " you can do here now", SMALL, MUTED)));
		content.add(Box.createVerticalStrut(6));
		JPanel card = column();
		card.setOpaque(true);
		card.setBackground(CARD);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT), new EmptyBorder(PAD, PAD, PAD, PAD)));
		int width = CONTENT - 3 - 2 * PAD;
		card.add(leftAligned(label("NEXT", SMALL, ACCENT)));
		card.add(Box.createVerticalStrut(2));
		card.add(leftAligned(wrappedRaw(now.name, BODY_BOLD, Color.WHITE, width)));
		card.add(Box.createVerticalStrut(3));
		card.add(leftAligned(label(now.tier.label() + " · " + now.type.label() + " · " + points(now.points()), SMALL, MUTED)));
		card.add(Box.createVerticalStrut(6));
		objective(card, now, width);
		howTo(card, now, width);
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(card);
		content.add(Box.createVerticalStrut(4));
		content.add(action("Skip this task", "Leave it for another sitting", () ->
		{
			autoSkipped.add(now.id);
			autoLast = null;
			autoDone = null;
			rebuild();
		}));
		content.add(Box.createVerticalStrut(4));
		content.add(action("Another boss", "Leave this boss for another sitting", () ->
		{
			autoSkippedBosses.add(autoBoss);
			autoBoss = null;
			autoLast = null;
			autoDone = null;
			rebuild();
		}));
		content.add(Box.createVerticalStrut(4));
		content.add(action("Change the time", "Say again how long you have", () ->
		{
			// a new sitting: what was set aside comes back
			forgetSession();
			rebuild();
		}));
		content.add(Box.createVerticalStrut(6));
		content.add(gearCard(pick.boss, now));
		if (!pick.after.isEmpty())
		{
			content.add(Box.createVerticalStrut(8));
			content.add(leftAligned(label("AFTER THIS, HERE", SMALL, ACCENT)));
			content.add(Box.createVerticalStrut(2));
			int shown = 0;
			for (Task t : pick.after)
			{
				if (shown++ == 5)
				{
					content.add(leftAligned(label("+ " + (pick.after.size() - 5) + " more", SMALL, MUTED)));
					break;
				}
				content.add(taskRow(t, false));
				content.add(Box.createVerticalStrut(2));
			}
		}
	}

	/** The set picked from the player's bank for a boss, with the way there and the setup; or why there is none. */
	private JComponent gearCard(String boss, Task task)
	{
		final int taskId = task == null ? -1 : task.id;
		int ruleKind = taskId < 0 ? 0 : icons.ruleKind(taskId);
		// a task with a rule of its own gets its own set, and so does one against the clock (it carries more
		// pieces to change into); the others share their boss's
		final boolean own = ruleKind != 0 || (task != null && task.type == TaskType.SPEED);
		final String key = own ? boss + "#" + taskId : boss;
		JPanel card = column();
		card.setOpaque(true);
		card.setBackground(CARD);
		card.setBorder(new EmptyBorder(PAD, PAD, PAD, PAD));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		int width = CONTENT - 2 * PAD;
		card.add(leftAligned(label("GEAR FROM YOUR BANK", SMALL, ACCENT)));
		card.add(Box.createVerticalStrut(4));
		GearView view = gear.get(key);
		if (view == null)
		{
			view = GearView.of(GearView.Status.WORKING);
			gear.put(key, view);
			icons.wantGear(key, gearGeneration, boss, own ? taskId : -1);
		}
		if (ruleKind == 3)
		{
			card.add(leftAligned(wrapped("Picked for the part of this task's rule a set can follow. There is more to it: "
				+ "read the objective.", SMALL, BAD, width)));
			card.add(Box.createVerticalStrut(4));
		}
		else if (ruleKind == 2)
		{
			card.add(leftAligned(wrapped("This task has a rule of its own that a set cannot follow. Read the objective: "
				+ "what is below is only what deals the most.", SMALL, BAD, width)));
			card.add(Box.createVerticalStrut(4));
		}
		else if (ruleKind == 1)
		{
			card.add(leftAligned(wrapped("Picked for this task's own rule.", SMALL, GOOD, width)));
			card.add(Box.createVerticalStrut(4));
		}
		for (String note : view.notes)
		{
			card.add(leftAligned(wrapped(note, SMALL, TEXT, width)));
			card.add(Box.createVerticalStrut(4));
		}
		switch (view.status)
		{
			case NO_BANK:
				card.add(leftAligned(wrapped("Open your bank once, so the plugin knows what you own.", BODY, TEXT, width)));
				break;
			case NO_BOSS:
				card.add(leftAligned(wrapped("No gear or inventory is worked out for this one yet: raids, the Gauntlet (where nothing is taken in) and "
					+ "fights that are not about damage are left out.", BODY, MUTED, width)));
				inventory(card, view, width);
				break;
			case NOTHING:
				card.add(leftAligned(wrapped(ruleKind == 1 || ruleKind == 3
					? "Nothing you own fits this task's rule, as far as the plugin can tell."
					: "Nothing you own can hit it, as far as the plugin can tell.", BODY, MUTED, width)));
				break;
			case NO_FIGURE:
				card.add(leftAligned(wrapped("You own what this task asks for: " + String.join(", ", view.weapons)
					+ ". The plugin has no damage figures for that weapon, so it picks no set around it.", BODY, TEXT, width)));
				break;
			case WORKING:
				card.add(leftAligned(label("Working it out...", BODY, MUTED)));
				break;
			default:
				if (!view.wornFor.isEmpty())
				{
					card.add(leftAligned(wrapped("Worn for: " + view.wornFor, SMALL, GOOD, width)));
					card.add(Box.createVerticalStrut(4));
				}
				gearGrid(card, view);
				card.add(Box.createVerticalStrut(6));
				String line = view.style + " · " + Math.round(view.dps * 10) / 10.0 + " damage a second";
				if (view.bossHitpoints > 0 && view.dps > 0)
				{
					int seconds = (int) Math.round(view.bossHitpoints / view.dps);
					line += " · " + seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60 + " of hitting";
				}
				card.add(leftAligned(wrapped(line, BODY, TEXT, width)));
				card.add(Box.createVerticalStrut(3));
				for (Bag.Line must : view.worn)
				{
					String what = must.name + (must.why.isEmpty() ? "" : ": " + must.why);
					card.add(leftAligned(wrapped(must.owned ? "Worn because the fight needs it. " + what
						: "Must be worn here and is not in your bank. " + what, SMALL, must.owned ? GOOD : BAD, width)));
					card.add(Box.createVerticalStrut(3));
				}
				if (task != null && task.type == TaskType.SPEED && !Requirements.needsGroup(task)
					&& SpeedLimit.isOneKill(task.description) && view.bossHitpoints > 0 && view.dps > 0)
				{
					// a Speed task of one kill: the time this set needs to deal the boss's hitpoints, against the limit
					int limit = SpeedLimit.seconds(task.description);
					int needs = (int) Math.round(view.bossHitpoints / view.dps);
					boolean fits = needs <= limit;
					card.add(leftAligned(wrapped("The limit is " + SpeedLimit.clock(limit) + ". " + (fits
						? "By damage alone this set fits, with " + SpeedLimit.clock(limit - needs) + " to spare for everything else."
						: "By damage alone this set is " + SpeedLimit.clock(needs - limit) + " over it, before anything else in the fight."),
						SMALL, fits ? GOOD : BAD, width)));
					card.add(Box.createVerticalStrut(3));
				}
				for (GearView.Part part : view.parts)
				{
					// another part of the fight: what to carry and change into for it
					card.add(Box.createVerticalStrut(6));
					card.add(leftAligned(wrapped(lang.of("TO CHANGE INTO FOR: " + part.label).toUpperCase(), SMALL, ACCENT, width)));
					card.add(Box.createVerticalStrut(3));
					if (part.nothing)
					{
						card.add(leftAligned(wrapped("Nothing you own does this part, as far as the plugin can tell.", SMALL, BAD, width)));
					}
					else if (part.carried.isEmpty())
					{
						card.add(leftAligned(wrapped("The set you wear does this part too.", SMALL, MUTED, width)));
					}
					else
					{
						pieces(card, part.carried);
						card.add(Box.createVerticalStrut(3));
						card.add(leftAligned(wrapped(part.style + (part.dps > 0 ? " · " + Math.round(part.dps * 10) / 10.0
							+ " damage a second with these on" : "") + (part.spell == null ? "" : " · casting " + part.spell), SMALL, TEXT, width)));
					}
					if (!part.note.isEmpty())
					{
						card.add(Box.createVerticalStrut(2));
						card.add(leftAligned(wrapped(part.note, SMALL, MUTED, width)));
					}
				}
				if (!view.parts.isEmpty())
				{
					card.add(Box.createVerticalStrut(6));
				}
				card.add(leftAligned(wrapped("An empty slot means nothing you own adds damage there. Counted with your best prayer"
					+ ("Melee".equals(view.style) ? " and a super combat potion" : "Ranged".equals(view.style) ? " and a ranging potion" : "")
					+ ", without special attacks.", SMALL, MUTED, width)));
				inventory(card, view, width);
				if (view.inventoryToCome)
				{
					card.add(Box.createVerticalStrut(8));
					card.add(leftAligned(label("INVENTORY TO EXPECT", SMALL, ACCENT)));
					card.add(Box.createVerticalStrut(3));
					card.add(leftAligned(wrapped("Under construction for this boss. The set above is what to wear; the inventory is yours to fill for now.",
						SMALL, MUTED, width)));
				}
				else if (view.bag.isEmpty() && view.carriedSlots().isEmpty())
				{
					card.add(Box.createVerticalStrut(3));
					card.add(leftAligned(wrapped("What to wear only: the inventory is yours to fill.", SMALL, MUTED, width)));
				}
				break;
		}
		final GearView shown = view;
		boolean ready = view.status == GearView.Status.READY;
		finishCard(card, view, shown, ready, boss, key, ruleKind, taskId, width);
		card.add(Box.createVerticalStrut(8));
		card.add(action("Bug or suggestion", "Something here is wrong, or could be better? Write what, and send it with what the planner gave", () -> report(boss, task, shown)));
		return card;
	}

	/**
	 * A window to report what is wrong for a boss or a task: the player's words, and under them
	 * what the planner gave, as it will be posted. Nothing is sent from here: one button opens the
	 * page where it is posted, already filled in, and another copies it.
	 */
	private void report(String boss, Task task, GearView view)
	{
		final Verdict verdict = task == null ? null : state.verdict(task);
		final String account = icons.accountLine();
		final String language = lang.isNone() ? "English" : "Portuguese";
		final JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), lang.of("Bug or suggestion") + ": " + boss);
		final JTextArea words = new JTextArea(5, 46);
		words.setLineWrap(true);
		words.setWrapStyleWord(true);
		final JTextArea sent = new JTextArea(14, 46);
		sent.setEditable(false);
		sent.setLineWrap(true);
		sent.setWrapStyleWord(true);
		sent.setText(Report.body(boss, task, "", view, verdict, account, language));
		sent.setCaretPosition(0);
		words.getDocument().addDocumentListener(new DocumentListener()
		{
			private void again()
			{
				sent.setText(Report.body(boss, task, words.getText(), view, verdict, account, language));
				sent.setCaretPosition(0);
			}

			@Override
			public void insertUpdate(DocumentEvent e)
			{
				again();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				again();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				again();
			}
		});
		final JLabel said = new JLabel(" ");
		JButton send = new JButton(lang.of("Send the report"));
		send.addActionListener(e ->
		{
			send.setEnabled(false);
			said.setForeground(TEXT);
			said.setText(lang.of("Sending..."));
			icons.sendReport(boss, task == null ? "" : task.name + " (" + task.id + ")", words.getText(), sent.getText(), went ->
			{
				// green and bold, so that it is seen: the window stays open
				said.setForeground(went ? GOOD : BAD);
				said.setFont(BODY_BOLD);
				said.setText(lang.of(went ? "Sent. Thank you." : "It did not go. Try again, or use one of the other two."));
				send.setEnabled(!went);
			});
		});
		JButton post = new JButton(lang.of("Open the report on GitHub"));
		post.addActionListener(e ->
		{
			icons.openLink(Report.link(Report.title(boss, task, words.getText()), sent.getText()));
			said.setText(lang.of("Opened in your browser: post it there. It needs a GitHub account."));
		});
		JButton copy = new JButton(lang.of("Copy the report"));
		copy.addActionListener(e ->
		{
			try
			{
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sent.getText()), null);
				said.setForeground(GOOD);
				said.setText(lang.of("Copied."));
			}
			catch (IllegalStateException busy)
			{
				said.setText(lang.of("The clipboard is busy. Try again."));
			}
		});
		JButton close = new JButton(lang.of("Close"));
		close.addActionListener(e -> dialog.dispose());
		JPanel buttons = new JPanel();
		final boolean direct = icons.canSendReports();
		if (direct)
		{
			buttons.add(send);
		}
		buttons.add(post);
		buttons.add(copy);
		buttons.add(close);
		JPanel all = column();
		all.setBorder(new EmptyBorder(10, 10, 10, 10));
		all.add(leftAligned(new JLabel(lang.of("What is wrong, or what would you like? Say what you expected instead."))));
		all.add(Box.createVerticalStrut(4));
		all.add(leftAligned(new JScrollPane(words)));
		all.add(Box.createVerticalStrut(8));
		all.add(leftAligned(new JLabel(lang.of("This is what is posted with it. It has no name of yours or of your account:"))));
		all.add(Box.createVerticalStrut(4));
		all.add(leftAligned(new JScrollPane(sent)));
		all.add(Box.createVerticalStrut(6));
		all.add(leftAligned(new JLabel(lang.of(direct
			? "\"Send\" sends what is above, and nothing else, to the plugin's author. Nothing goes until you press it."
			: "Nothing is sent from here: you post it on the page that opens, or copy it."))));
		all.add(leftAligned(said));
		all.add(leftAligned(buttons));
		dialog.setContentPane(all);
		dialog.pack();
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
	}

	/** The inventory to expect, when there is one to show. */
	private void inventory(JPanel card, GearView view, int width)
	{
		final Bag.Provisional provisional = view.provisional;
		if (!view.bag.isEmpty() || !view.carriedSlots().isEmpty() || provisional != null)
		{
			card.add(Box.createVerticalStrut(8));
			card.add(leftAligned(label(provisional != null ? "INVENTORY TO EXPECT (PROVISIONAL)" : "INVENTORY TO EXPECT", SMALL, ACCENT)));
			card.add(Box.createVerticalStrut(4));
			bagGrid(card, view);
			for (Bag.Line item : view.bag)
			{
				if (item.needed)
				{
					card.add(Box.createVerticalStrut(3));
					String what = item.name + (item.why.isEmpty() ? "" : ": " + item.why);
					card.add(leftAligned(wrapped(item.owned ? "Needed here. " + what : "Needed here and not in your bank. " + what,
						SMALL, item.owned ? GOOD : BAD, width)));
				}
			}
			card.add(Box.createVerticalStrut(3));
			if (provisional != null)
			{
				card.add(leftAligned(wrapped("Provisional: the amounts are those of the OSRS Wiki's inventory for this boss, filled with "
					+ "what you own. This plugin has not gone through the fight yet.", SMALL, ACCENT, width)));
				if (!provisional.lacking.isEmpty())
				{
					card.add(Box.createVerticalStrut(3));
					card.add(leftAligned(wrapped("The wiki's inventory also has, and you do not own: "
						+ String.join(", ", provisional.lacking) + ".", SMALL, BAD, width)));
				}
				if (!provisional.switches.isEmpty())
				{
					card.add(Box.createVerticalStrut(3));
					card.add(leftAligned(wrapped("The wiki also carries pieces to change into (" + String.join(", ", provisional.switches)
						+ "): this fight may ask for switches, which are not worked out yet.", SMALL, MUTED, width)));
				}
				return;
			}
			card.add(leftAligned(wrapped("Supplies are only ones you own; what the fight needs is listed either way. "
				+ "A place to start from: the amounts and your switches are yours to settle.", SMALL, MUTED, width)));
		}
	}

	private JComponent finishCard(JPanel card, GearView view, GearView shown, boolean ready, String boss, String key,
		int ruleKind, int taskId, int width)
	{
		if (!icons.hasPlace(boss))
		{
			card.add(Box.createVerticalStrut(6));
			card.add(leftAligned(wrapped("The way there: under construction for this boss.", SMALL, MUTED, width)));
		}
		else
		{
			card.add(Box.createVerticalStrut(6));
			if (icons.canRoute())
			{
				card.add(action("Mark the way on the map", "Asks your route plugin (Shortest Path or GPS) for the way to " + boss,
					() -> icons.route(boss)));
			}
			else
			{
				card.add(leftAligned(wrapped("To have the way there marked on the map, install the Shortest Path plugin or the GPS plugin "
					+ "from the Plugin Hub and turn it on.", SMALL, MUTED, width)));
			}
		}
		if (ready && !icons.hasSetups())
		{
			card.add(Box.createVerticalStrut(4));
			card.add(leftAligned(wrapped("To turn this set into a setup you can pull from the bank, install the Inventory Setups "
				+ "plugin from the Plugin Hub and turn it on.", SMALL, MUTED, width)));
		}
		else if (ready)
		{
			// a task with a set of its own gets a setup of its own, so it does not replace its boss's
			final String name = SetupText.nameFor(boss, ruleKind == 0 ? -1 : taskId);
			card.add(Box.createVerticalStrut(4));
			card.add(action("1. Copy the setup text", "Copies the set as text for Inventory Setups' Import setup", () ->
			{
				setupDoneFor = key;
				try
				{
					Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
						new StringSelection(SetupText.of(name, shown.itemIdBySlot, shown.inventory())), null);
					setupFailed = false;
					setupDone = "Copied. Now, in Inventory Setups: Import setup, paste. It is saved as \"" + name
						+ "\"; if you already have one of that name, delete it first or the new one gets another name.";
				}
				catch (IllegalStateException e)
				{
					// another program is holding the clipboard
					setupFailed = true;
					setupDone = "The clipboard is busy. Try again.";
				}
				rebuild();
			}));
			card.add(Box.createVerticalStrut(4));
			card.add(action("2. Show it in the bank", "After you imported it: opens \"" + name + "\" in Inventory Setups",
				() -> icons.showSetup(name)));
			card.add(Box.createVerticalStrut(4));
			card.add(leftAligned(label("HOW TO PUT IT IN INVENTORY SETUPS", SMALL, ACCENT)));
			for (String step : new String[]{
				"1. Press \"1. Copy the setup text\" above.",
				"2. Open the Inventory Setups panel on RuneLite's side bar.",
				"3. Click the import button at the top of it (\"Import a new setup or section\") and pick \"Import setup..\".",
				"4. Paste with Ctrl+V and confirm: \"" + name + "\" is added to your setups.",
				"5. Press \"2. Show it in the bank\" above, or open your bank and pick it."})
			{
				card.add(Box.createVerticalStrut(2));
				card.add(leftAligned(wrapped(step, SMALL, MUTED, width)));
			}
			if (key.equals(setupDoneFor) && setupDone != null)
			{
				card.add(Box.createVerticalStrut(3));
				card.add(leftAligned(wrapped(setupDone, SMALL, setupFailed ? BAD : GOOD, width)));
			}
		}
		return card;
	}

	/** The items of the set, laid out as the game's equipment tab. */
	private void gearGrid(JPanel card, GearView view)
	{
		JPanel grid = new JPanel(new GridLayout(5, 3, 2, 2));
		grid.setOpaque(false);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		Dimension size = new Dimension(3 * 40 + 4, 5 * 36 + 8);
		grid.setPreferredSize(size);
		grid.setMaximumSize(size);
		for (int slot : GRID)
		{
			JLabel cell = new JLabel();
			cell.setHorizontalAlignment(SwingConstants.CENTER);
			Integer id = slot < 0 ? null : view.itemIdBySlot.get(slot);
			if (slot >= 0)
			{
				cell.setOpaque(true);
				cell.setBackground(BUTTON);
			}
			if (id != null && id > 0)
			{
				cell.putClientProperty(NO_HTML, Boolean.TRUE);
				cell.setToolTipText(view.nameBySlot.get(slot));
				icons.itemIcon(id, cell);
			}
			grid.add(cell);
		}
		card.add(grid);
	}

	/** A row of pieces, four across: what to carry for a part of the fight. */
	private void pieces(JPanel card, List<Bag.Line> carried)
	{
		int rows = Math.max(1, (carried.size() + 3) / 4);
		JPanel grid = new JPanel(new GridLayout(rows, 4, 2, 2));
		grid.setOpaque(false);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		Dimension size = new Dimension(4 * 40 + 6, rows * 36 + (rows - 1) * 2);
		grid.setPreferredSize(size);
		grid.setMaximumSize(size);
		for (int i = 0; i < rows * 4; i++)
		{
			JLabel cell = new JLabel();
			cell.setHorizontalAlignment(SwingConstants.CENTER);
			cell.setOpaque(true);
			cell.setBackground(BUTTON);
			if (i < carried.size())
			{
				cell.putClientProperty(NO_HTML, Boolean.TRUE);
				cell.setToolTipText(carried.get(i).name);
				icons.itemIcon(carried.get(i).itemId, cell);
			}
			grid.add(cell);
		}
		card.add(grid);
	}

	/** The inventory as the game lays it out, four across. */
	private void bagGrid(JPanel card, GearView view)
	{
		// the pieces to change into come first, each part in rows of its own
		List<Bag.Line> cells = new ArrayList<>(view.carriedSlots());
		for (Bag.Line line : view.bag)
		{
			for (int i = 0; i < line.slots() && cells.size() < Bag.SLOTS; i++)
			{
				cells.add(line);
			}
		}
		int rows = Math.max(1, (cells.size() + 3) / 4);
		JPanel grid = new JPanel(new GridLayout(rows, 4, 2, 2));
		grid.setOpaque(false);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		Dimension size = new Dimension(4 * 40 + 6, rows * 36 + (rows - 1) * 2);
		grid.setPreferredSize(size);
		grid.setMaximumSize(size);
		for (int i = 0; i < rows * 4; i++)
		{
			JLabel cell = new JLabel();
			cell.setHorizontalAlignment(SwingConstants.CENTER);
			cell.setOpaque(true);
			cell.setBackground(BUTTON);
			if (i < cells.size())
			{
				Bag.Line line = cells.get(i);
				if (line == null)
				{
					grid.add(cell);
					continue;
				}
				cell.putClientProperty(NO_HTML, Boolean.TRUE);
				cell.setToolTipText(line.name + (line.stack && line.quantity > 1 ? " x" + line.quantity : "")
					+ (line.owned ? "" : " (not in your bank)"));
				if (!line.owned)
				{
					cell.setBorder(BorderFactory.createLineBorder(BAD));
				}
				if (line.itemId > 0)
				{
					icons.itemIcon(line.itemId, cell);
				}
			}
			grid.add(cell);
		}
		card.add(grid);
	}

	private JComponent action(String text, String tip, Runnable onClick)
	{
		JButton b = flatButton(text);
		b.setToolTipText(lang.of(tip));
		b.setAlignmentX(Component.LEFT_ALIGNMENT);
		b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		b.setPreferredSize(new Dimension(CONTENT - 2 * PAD, 26));
		b.addActionListener(e -> onClick.run());
		return b;
	}

	private JComponent taskRow(Task t, boolean withBoss)
	{
		int section = Planner.section(state, t);
		double ease = state.ease(t);
		String sub = (withBoss ? t.boss + " · " : "") + t.tier.label() + " · " + t.type.label() + " · " + points(t.points())
			+ (ease >= 0 && section != 3 ? " · " + percent(ease) + " did it" : "");
		JPanel dot = new JPanel(new BorderLayout());
		dot.setOpaque(false);
		dot.setBorder(new EmptyBorder(4, 0, 0, 0));
		dot.add(new Dot(DOTS[section]), BorderLayout.NORTH);
		return row(t.name, sub, section == 0 ? TEXT : MUTED, MUTED, dot, () ->
		{
			openTask = t;
			rebuild();
		});
	}

	private static String points(int n)
	{
		return n + (n == 1 ? " point" : " points");
	}

	private static String percent(double p)
	{
		return (p >= 10 ? String.valueOf(Math.round(p)) : String.valueOf(Math.round(p * 10) / 10.0)) + "%";
	}

	// ---------------------------------------------------------------- pieces

	private JLabel bossIcon(String boss)
	{
		JLabel icon = new JLabel();
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		Dimension d = new Dimension(ICON, ICON);
		icon.setPreferredSize(d);
		icon.setMinimumSize(d);
		icon.setMaximumSize(d);
		icons.bossIcon(boss, icon);
		return icon;
	}

	/** A clickable line: something on the left (an icon, a dot), a name and a smaller second line. */
	private JComponent row(String name, String sub, Color nameColour, Color subColour, JComponent left, Runnable onClick)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(BUTTON);
		row.setBorder(new EmptyBorder(4, 6, 4, 6));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.add(left, BorderLayout.WEST);
		int textWidth = ROW_TEXT + 14 - Math.max(8, left.getPreferredSize().width);
		JPanel text = column();
		text.add(leftAligned(wrappedRaw(name, BODY, nameColour, textWidth)));
		text.add(leftAligned(wrapped(sub, SMALL, subColour, textWidth)));
		row.add(text, BorderLayout.CENTER);
		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				onClick.run();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				row.setBackground(BUTTON_HOVER);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				row.setBackground(BUTTON);
			}
		});
		return row;
	}

	private JComponent back(String where, Runnable onClick)
	{
		JLabel l = label("◂ " + where, SMALL, ACCENT);
		l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		l.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				onClick.run();
			}
		});
		return leftAligned(l);
	}

	private static JPanel column()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setOpaque(false);
		return p;
	}

	/** A one-line label. Its text is always plain text, even when it starts with an HTML tag. */
	private static JLabel label(String text, Font font, Color colour)
	{
		JLabel l = new JLabel();
		l.putClientProperty(NO_HTML, Boolean.TRUE);
		l.setText(lang.of(text));
		l.setFont(font);
		l.setForeground(colour);
		return l;
	}

	/** As {@link #wrapped}, for words that are not the plugin's own (a name the game gives, the wiki's text): never put into another language. */
	private static JLabel wrappedRaw(String text, Font font, Color colour, int width)
	{
		JLabel l = new JLabel("<html><div style='width:" + Math.max(40, Math.round(width / 1.3f)) + "px'>" + escape(text) + "</div></html>");
		l.setFont(font);
		l.setForeground(colour);
		l.setVerticalAlignment(SwingConstants.TOP);
		return l;
	}

	/** A label that wraps at a fixed width. The text is escaped: the only HTML is the wrapper. */
	private static JLabel wrapped(String text, Font font, Color colour, int width)
	{
		// Swing lays out a CSS px at 1.3 screen pixels
		JLabel l = new JLabel("<html><div style='width:" + Math.max(40, Math.round(width / 1.3f)) + "px'>" + escape(lang.of(text)) + "</div></html>");
		l.setFont(font);
		l.setForeground(colour);
		l.setVerticalAlignment(SwingConstants.TOP);
		return l;
	}

	static String escape(String text)
	{
		return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static <T extends JComponent> T leftAligned(T c)
	{
		c.setAlignmentX(Component.LEFT_ALIGNMENT);
		return c;
	}

	private static JButton flatButton(String text)
	{
		JButton b = new JButton();
		b.putClientProperty(NO_HTML, Boolean.TRUE);
		b.setText(lang.of(text));
		b.setFont(BODY);
		b.setFocusPainted(false);
		b.setBorder(new EmptyBorder(4, 4, 4, 4));
		b.setBackground(BUTTON);
		b.setForeground(TEXT);
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return b;
	}

	private static void mark(JButton button, boolean active)
	{
		button.setBackground(active ? ACCENT : BUTTON);
		button.setForeground(active ? Color.BLACK : TEXT);
	}

	/** A text field that shows a hint while it is empty. */
	private static final class HintField extends JTextField
	{
		private final String hint;

		HintField(String hint)
		{
			this.hint = hint;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			if (getText().isEmpty())
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				g2.setColor(MUTED);
				g2.setFont(getFont());
				int y = (getHeight() - g2.getFontMetrics().getHeight()) / 2 + g2.getFontMetrics().getAscent();
				g2.drawString(hint, getInsets().left, y);
				g2.dispose();
			}
		}
	}

	/** A small filled circle. */
	private static final class Dot extends JComponent
	{
		private static final int SIZE = 8;
		private final Color colour;

		Dot(Color colour)
		{
			this.colour = colour;
			Dimension d = new Dimension(SIZE, SIZE);
			setPreferredSize(d);
			setMinimumSize(d);
			setMaximumSize(d);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(colour);
			g2.fillOval(0, 0, SIZE, SIZE);
			g2.dispose();
		}
	}
}
