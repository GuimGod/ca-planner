/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner;

import com.caplanner.bridge.AccountReader;
import com.caplanner.bridge.GameTasks;
import com.caplanner.core.Account;
import com.caplanner.core.Bag;
import com.caplanner.core.BossIcons;
import com.caplanner.core.Completion;
import com.caplanner.core.Ease;
import com.caplanner.core.GearView;
import com.caplanner.core.OwnedItems;
import com.caplanner.core.Places;
import com.caplanner.core.Planner;
import com.caplanner.core.Requirements;
import com.caplanner.core.Rules;
import com.caplanner.core.SetupText;
import com.caplanner.core.Task;
import com.caplanner.core.Verdict;
import com.caplanner.gear.Gear;
import com.caplanner.ui.CaPlannerPanel;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

/**
 * Lists the player's Combat Achievement tasks in a side panel, by boss and all together, and
 * says which of them the account can attempt now. The tasks and which of them are done are read
 * from the game; the bank is remembered per account for the gear this plugin will pick later.
 * It draws nothing over the game.
 */
@Slf4j
@PluginDescriptor(
	name = "CA Planner",
	description = "Lists your pending Combat Achievements by boss and tells you which ones you can do now",
	tags = {"combat", "achievements", "ca", "tasks", "boss", "planner", "pvm"}
)
public class CaPlannerPlugin extends Plugin implements CaPlannerPanel.Actions
{
	private static final int KNIGHT_WAVES_DONE = 8;
	private static final String INVENTORY_SETUPS = "Inventory Setups";
	private static final String SHORTEST_PATH = "Shortest Path";
	private static final String GPS = "GPS";
	private static final int SHOW_AFTER_MILLIS = 1500;
	static final String CONFIG_GROUP = "caplanner";
	private static final String BANK_KEY = "bank";
	/** Quests change without an event the plugin listens to: look again this often. */
	private static final int LOOK_AGAIN_TICKS = 100;
	/** How soon to try again when the game's task tables come back empty. */
	private static final int RETRY_TICKS = 5;
	private static final int EMPTY_READS_TO_WARN = 20;
	private static final int NOT_SHOWN = Integer.MIN_VALUE;
	/** Hiscores name to its entry; filled on first use. */
	private static final Map<String, HiscoreSkill> HISCORES = new HashMap<>();

	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private EventBus eventBus;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private CaPlannerConfig config;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private Gson gson;

	@Inject
	private okhttp3.OkHttpClient okHttpClient;

	/** Where a report is sent when the player asks: the form's address and its field names; null for nowhere. */
	private volatile String[] reportForm;

	@Provides
	CaPlannerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CaPlannerConfig.class);
	}

	// Started and stopped on the Swing thread, used on the client thread: hence volatile.
	private volatile CaPlannerPanel panel;
	private NavigationButton navigation;
	private volatile Requirements requirements;
	private volatile Map<Integer, Double> ease = new HashMap<>();
	/** The game's task list; read once, and again while it comes back empty. */
	private volatile List<Task> tasks = new ArrayList<>();
	/** Something the panel shows has changed; it is redrawn on the next game tick. */
	private volatile boolean dirty;
	private volatile int ticksSinceLook;
	/** What the panel was last given, in short: the same again is not drawn again. */
	private volatile String lastShown;
	/** The bank count the panel was last told; {@link #NOT_SHOWN} before the first. */
	private volatile int lastBank = NOT_SHOWN;
	private int emptyReads;
	private volatile Gear gear;
	/** Fights of several parts, and what to carry for each. */
	private volatile com.caplanner.gear.Fights fights;
	private volatile Rules rules;
	/** Names of the wearable items of the remembered bank, and the bank text they were read from. */
	private volatile Set<String> bankNames;
	private volatile String bankNamesFor;
	private volatile Places places;
	private volatile Bag bag;
	/** Everything in the remembered bank by name, and the bank text it was read from. */
	private volatile Map<String, Bag.Have> bankHave;
	private volatile String bankHaveFor;
	/** The account as last read, for the gear: which boss is on task. */
	private volatile Account account;
	/** What the sets were picked from has changed (a level, the Slayer task, what is carried): they are picked again. */
	private volatile boolean gearStale;
	/** The remembered bank as last looked at: a different one means other sets. */
	private volatile String lastBankText;
	/** The wearable items carried and worn, as last seen. */
	private volatile String lastCarried;
	/** The Slayer task and the arrows in Dizana's quiver, as last seen. */
	private volatile String lastTaskAndQuiver;
	/** Real levels as last seen, to tell a new level from a mere experience drop. */
	private final Map<Skill, Integer> levels = new ConcurrentHashMap<>();

	@Override
	protected void startUp()
	{
		requirements = readRequirements();
		ease = readEase();
		places = readPlaces();
		rules = readRules();
		reportForm = readReportForm();
		bag = readBag();
		bankHave = null;
		bankHaveFor = null;
		bankNames = null;
		bankNamesFor = null;
		fights = null;
		try (InputStream in = Gear.class.getResourceAsStream("fights.tsv"))
		{
			fights = in == null ? null : com.caplanner.gear.Fights.read(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("could not read the table of fights of several parts", e);
		}
		try
		{
			gear = new Gear();
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("could not read the gear tables: no sets will be picked", e);
			gear = null;
		}
		makePanel();
		lastShown = null;
		lastBank = NOT_SHOWN;
		ticksSinceLook = 0;
		emptyReads = 0;
		levels.clear();
		gearStale = false;
		lastCarried = null;
		lastTaskAndQuiver = null;
		dirty = client.getGameState() == GameState.LOGGED_IN;
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navigation);
		navigation = null;
		panel = null;
		tasks = new ArrayList<>();
	}

	private static Requirements readRequirements()
	{
		try (InputStream in = CaPlannerPlugin.class.getResourceAsStream("requirements.tsv"))
		{
			if (in != null)
			{
				return Requirements.read(new InputStreamReader(in, StandardCharsets.UTF_8));
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("could not read the requirements table", e);
		}
		try
		{
			// with no table every task counts as possible
			return Requirements.read(new StringReader(""));
		}
		catch (IOException e)
		{
			throw new IllegalStateException(e);
		}
	}

	private static Rules readRules()
	{
		try (InputStream in = CaPlannerPlugin.class.getResourceAsStream("rules.tsv"))
		{
			return Rules.read(new InputStreamReader(in == null ? new java.io.ByteArrayInputStream(new byte[0]) : in, StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			throw new IllegalStateException(e);
		}
	}

	/** Makes the side panel in the language chosen, in place of the one there is. Swing thread. */
	private void makePanel()
	{
		if (navigation != null)
		{
			clientToolbar.removeNavigation(navigation);
		}
		boolean portuguese = config.language() == CaPlannerConfig.Language.PORTUGUESE;
		com.caplanner.core.Lang words = com.caplanner.core.Lang.none();
		if (portuguese)
		{
			try (InputStream in = CaPlannerPlugin.class.getResourceAsStream("lang/pt.tsv"))
			{
				if (in != null)
				{
					words = com.caplanner.core.Lang.read(new InputStreamReader(in, StandardCharsets.UTF_8));
				}
			}
			catch (IOException | RuntimeException e)
			{
				log.warn("could not read the Portuguese words: the panel stays in English", e);
			}
		}
		CaPlannerPanel.language(words, portuguese ? readHowTo("lang/pt-tasks.tsv") : com.caplanner.core.HowTo.none());
		panel = new CaPlannerPanel(this);
		panel.guide(readHowTo("howto-wiki.tsv"), readHowTo("howto-own.tsv"), readLengths());
		navigation = NavigationButton.builder()
			.tooltip("CA Planner")
			.icon(icon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigation);
	}

	private static com.caplanner.core.HowTo readHowTo(String file)
	{
		try (InputStream in = CaPlannerPlugin.class.getResourceAsStream(file))
		{
			return in == null ? com.caplanner.core.HowTo.none()
				: com.caplanner.core.HowTo.read(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			log.warn("could not read {}", file, e);
			return com.caplanner.core.HowTo.none();
		}
	}

	private static com.caplanner.core.Session.Lengths readLengths()
	{
		try (InputStream in = CaPlannerPlugin.class.getResourceAsStream("sessions.tsv"))
		{
			return com.caplanner.core.Session.Lengths.read(
				new InputStreamReader(in == null ? new java.io.ByteArrayInputStream(new byte[0]) : in, StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			throw new IllegalStateException(e);
		}
	}

	private static Bag readBag()
	{
		try (InputStream in = CaPlannerPlugin.class.getResourceAsStream("bag.tsv"))
		{
			Bag out = Bag.read(new InputStreamReader(in == null ? new java.io.ByteArrayInputStream(new byte[0]) : in, StandardCharsets.UTF_8));
			try (InputStream wiki = CaPlannerPlugin.class.getResourceAsStream("bag-wiki.tsv"))
			{
				if (wiki != null)
				{
					out.readWiki(new InputStreamReader(wiki, StandardCharsets.UTF_8));
				}
			}
			return out;
		}
		catch (IOException e)
		{
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Everything the player owns, wearable or not, by its name in lower case: the remembered bank
	 * (read again only when it changes) and what is carried now. Client thread.
	 */
	private Map<String, Bag.Have> have(String savedBank)
	{
		if (!savedBank.equals(bankHaveFor) || bankHave == null)
		{
			Map<String, Bag.Have> names = new HashMap<>();
			OwnedItems bank = OwnedItems.decode(savedBank);
			for (int raw : bank.ids())
			{
				add(names, raw, bank.quantity(raw));
			}
			bankHave = names;
			bankHaveFor = savedBank;
		}
		Map<String, Bag.Have> all = new HashMap<>(bankHave);
		for (int container : new int[]{InventoryID.INV, InventoryID.WORN})
		{
			ItemContainer carried = client.getItemContainer(container);
			if (carried == null)
			{
				continue;
			}
			for (Item item : carried.getItems())
			{
				if (item.getId() > 0 && item.getQuantity() > 0)
				{
					add(all, item.getId(), item.getQuantity());
				}
			}
		}
		return all;
	}

	private void add(Map<String, Bag.Have> into, int rawId, int quantity)
	{
		int id = itemManager.canonicalize(rawId);
		String name = itemManager.getItemComposition(id).getName();
		if (name == null || name.isEmpty() || "null".equals(name))
		{
			return;
		}
		Bag.Have before = into.get(name.toLowerCase());
		long total = (long) quantity + (before == null ? 0 : before.quantity);
		into.put(name.toLowerCase(), new Bag.Have(id, (int) Math.min(Integer.MAX_VALUE, total), name));
	}

	private static Places readPlaces()
	{
		try (InputStream in = CaPlannerPlugin.class.getResourceAsStream("places.tsv"))
		{
			return Places.read(new InputStreamReader(in == null ? new java.io.ByteArrayInputStream(new byte[0]) : in, StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			throw new IllegalStateException(e);
		}
	}

	private static Map<Integer, Double> readEase()
	{
		try (InputStream in = CaPlannerPlugin.class.getResourceAsStream("completion.tsv"))
		{
			if (in != null)
			{
				return Ease.read(new InputStreamReader(in, StandardCharsets.UTF_8));
			}
		}
		catch (IOException e)
		{
			log.warn("could not read the completion table", e);
		}
		return new HashMap<>();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			dirty = true;
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// nobody is logged in: the last account's tasks and bank must not stay on screen
			lastShown = null;
			lastBank = NOT_SHOWN;
			lastBankText = null;
			emptyReads = 0;
			levels.clear();
			final CaPlannerPanel target = panel;
			SwingUtilities.invokeLater(() ->
			{
				if (target != null)
				{
					target.notice(null);
					target.forgetSession();
					target.render(Planner.State.empty());
					target.bankSeen(-1);
				}
			});
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		// another account: its own bank is the one to show
		dirty = true;
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (GameTasks.isCompletionVariable(event.getVarpId()))
		{
			dirty = true;
		}
		else if (AccountReader.isSlayerVariable(event.getVarpId())
			|| event.getVarpId() == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO
			|| event.getVarpId() == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT)
		{
			// the Slayer helmet counts or stops counting; the arrows in the quiver are owned or not. The
			// counts themselves go down with every kill and every shot: only which task, which arrows,
			// and whether there are any left, change a set
			String now = client.getVarpValue(VarPlayerID.SLAYER_TARGET) + ":" + (client.getVarpValue(VarPlayerID.SLAYER_COUNT) > 0)
				+ ":" + client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO)
				+ ":" + (client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT) > 0);
			if (!now.equals(lastTaskAndQuiver))
			{
				lastTaskAndQuiver = now;
				gearStale = true;
				dirty = true;
			}
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		// experience changes all the time; only a new level can change what is possible
		Integer before = levels.put(event.getSkill(), event.getLevel());
		if (before == null || before != event.getLevel())
		{
			gearStale = true;
			dirty = true;
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.INV || event.getContainerId() == InventoryID.WORN)
		{
			// food and potions come and go all the time: only what can be worn matters to the sets,
			// and moving a piece between the inventory and the body changes nothing
			String now = carried();
			if (!now.equals(lastCarried))
			{
				lastCarried = now;
				gearStale = true;
				dirty = true;
			}
			return;
		}
		if (event.getContainerId() != InventoryID.BANK)
		{
			return;
		}
		ItemContainer bank = event.getItemContainer();
		Item[] items = bank == null ? new Item[0] : bank.getItems();
		int[] ids = new int[items.length];
		int[] quantities = new int[items.length];
		for (int i = 0; i < items.length; i++)
		{
			ids[i] = items[i].getId();
			quantities[i] = items[i].getQuantity();
		}
		// the bank changes with every deposit: only a different content is written again
		String now = OwnedItems.of(ids, quantities).encode();
		if (!now.equals(configManager.getRSProfileConfiguration(CONFIG_GROUP, BANK_KEY)))
		{
			configManager.setRSProfileConfiguration(CONFIG_GROUP, BANK_KEY, now);
			dirty = true;
		}
	}

	/** The wearable items carried and worn, in order, as one text. Client thread. */
	private String carried()
	{
		java.util.TreeSet<Integer> ids = new java.util.TreeSet<>();
		for (int container : new int[]{InventoryID.INV, InventoryID.WORN})
		{
			ItemContainer c = client.getItemContainer(container);
			if (c == null)
			{
				continue;
			}
			for (Item item : c.getItems())
			{
				if (item.getId() > 0 && item.getQuantity() > 0)
				{
					int id = itemManager.canonicalize(item.getId());
					ItemStats stats = itemManager.getItemStats(id);
					if (stats != null && stats.getEquipment() != null)
					{
						ids.add(id);
					}
				}
			}
		}
		return ids.toString();
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (++ticksSinceLook >= LOOK_AGAIN_TICKS)
		{
			dirty = true;
		}
		if (!dirty)
		{
			return;
		}
		ticksSinceLook = 0;
		dirty = false;
		if (tasks.isEmpty())
		{
			tasks = GameTasks.read(client);
			if (tasks.isEmpty())
			{
				// the game's tables were not ready, or are not where this version expects them:
				// look again soon, not on every tick
				ticksSinceLook = LOOK_AGAIN_TICKS - RETRY_TICKS;
				if (++emptyReads == EMPTY_READS_TO_WARN)
				{
					log.warn("the game's Combat Achievement tables came back empty {} times", emptyReads);
					final CaPlannerPanel told = panel;
					SwingUtilities.invokeLater(() ->
					{
						if (told != null)
						{
							told.notice("The game's task list could not be read. The plugin may need an update.");
						}
					});
				}
			}
			else
			{
				if (emptyReads >= EMPTY_READS_TO_WARN)
				{
					// it was said that the list could not be read: now it was
					final CaPlannerPanel told = panel;
					SwingUtilities.invokeLater(() ->
					{
						if (told != null)
						{
							told.notice(null);
						}
					});
				}
				emptyReads = 0;
			}
		}
		final List<Task> shown = new ArrayList<>(tasks);
		final Set<Integer> done = Completion.doneIds(shown, GameTasks.completionWords(client));
		Account account = AccountReader.read(client, requirements.questNames());
		// a task of the harder fight already done says as much as the game's own count
		for (Task t : shown)
		{
			if (done.contains(t.id) && t.boss != null)
			{
				if (t.boss.contains("Hunllef"))
				{
					account.done.add("gauntlet-done");
				}
				if (t.boss.startsWith("Theatre of Blood") && !t.boss.contains("Entry"))
				{
					account.done.add("tob-done");
				}
				if (t.boss.equals("TzKal-Zuk"))
				{
					account.done.add("fire-cape-given");
				}
			}
		}
		this.account = account;
		String savedBank = configManager.getRSProfileConfiguration(CONFIG_GROUP, BANK_KEY);
		Set<String> ownedNames = ownedNames(savedBank);
		Map<Integer, Verdict> verdicts = new HashMap<>();
		final Bag table = bag;
		final Map<String, Bag.Have> everything = savedBank == null ? null : have(savedBank);
		final Set<String> things = doneThings();
		final Map<String, List<String>> lacking = new HashMap<>();
		for (Task t : shown)
		{
			if (!done.contains(t.id))
			{
				Verdict v = requirements.check(t, account);
				// an item the task itself needs (a weapon it must be done with, one for the last hit)
				String item = rules.missing(t.id, ownedNames);
				// and what the fight itself cannot be done without: a key, a tool, something to wear, a weapon that harms it
				List<String> ofBoss = lacking.computeIfAbsent(t.boss,
					b -> table == null || everything == null ? new ArrayList<String>() : table.missing(b, everything, things));
				if (item != null || !ofBoss.isEmpty())
				{
					List<String> reasons = new ArrayList<>(v.status == Verdict.Status.BLOCKED ? v.reasons : new ArrayList<String>());
					if (item != null)
					{
						reasons.add(item);
					}
					reasons.addAll(ofBoss);
					v = Verdict.blocked(reasons);
				}
				verdicts.put(t.id, v);
			}
		}
		final Planner.State state = new Planner.State(shown, done, verdicts, ease);
		final CaPlannerPanel target = panel;
		String saved = savedBank;
		final int bankItems = saved == null ? -1 : OwnedItems.decode(saved).size();
		boolean stale = gearStale;
		gearStale = false;
		boolean bankChanged = saved == null ? lastBankText != null : !saved.equals(lastBankText);
		lastBankText = saved;
		if (bankItems != lastBank || bankChanged)
		{
			stale = false;
			// only the line that says so changes: the lists stay as they are
			lastBank = bankItems;
			SwingUtilities.invokeLater(() ->
			{
				if (target != null)
				{
					target.bankSeen(bankItems);
					target.forgetGear();
				}
			});
		}
		// the periodic look usually finds nothing new: then the panel is left alone, scroll and all
		StringBuilder now = new StringBuilder().append(shown.size()).append('|').append(done.hashCode());
		for (Task t : shown)
		{
			Verdict v = verdicts.get(t.id);
			now.append(v == null ? '-' : v.status.ordinal()).append(v == null ? "" : String.valueOf(v.reasons.hashCode()));
		}
		if (now.toString().equals(lastShown))
		{
			if (stale)
			{
				SwingUtilities.invokeLater(() ->
				{
					if (target != null)
					{
						target.forgetGear();
					}
				});
			}
			return;
		}
		lastShown = now.toString();
		SwingUtilities.invokeLater(() ->
		{
			if (target != null)
			{
				target.forgetGear();
				target.render(state);
			}
		});
	}

	// ---------------------------------------------------------------- what the panel asks for

	@Override
	public void itemIcon(int itemId, JLabel into)
	{
		itemManager.getImage(itemId).addTo(into);
	}

	@Override
	public boolean hasPlace(String boss)
	{
		Places p = places;
		return p != null && p.of(boss) != null;
	}

	@Override
	public boolean canSendReports()
	{
		return reportForm != null;
	}

	/**
	 * Sends one report to the author's form, because the player pressed the button: the boss, the
	 * task, what the player wrote and what the planner showed. Nothing about the account but its
	 * combat levels and Slayer task, which the report already shows the player.
	 */
	@Override
	public void sendReport(String boss, String task, String words, String report, java.util.function.Consumer<Boolean> done)
	{
		final String[] form = reportForm;
		if (form == null)
		{
			SwingUtilities.invokeLater(() -> done.accept(false));
			return;
		}
		okhttp3.FormBody body = new okhttp3.FormBody.Builder()
			.add(form[1], "CA Planner")
			.add(form[2], boss == null ? "" : boss)
			.add(form[3], task == null ? "" : task)
			.add(form[4], words == null ? "" : words)
			.add(form[5], report == null ? "" : report)
			.build();
		okhttp3.Request request = new okhttp3.Request.Builder().url(form[0]).post(body).build();
		okHttpClient.newCall(request).enqueue(new okhttp3.Callback()
		{
			@Override
			public void onFailure(okhttp3.Call call, IOException e)
			{
				log.debug("the report did not go", e);
				SwingUtilities.invokeLater(() -> done.accept(false));
			}

			@Override
			public void onResponse(okhttp3.Call call, okhttp3.Response response)
			{
				final boolean went = response.isSuccessful();
				response.close();
				SwingUtilities.invokeLater(() -> done.accept(went));
			}
		});
	}

	/** The address of the author's form and the names of its fields, or null when the table gives none. */
	private static String[] readReportForm()
	{
		try (InputStream in = CaPlannerPlugin.class.getResourceAsStream("report-form.tsv"))
		{
			if (in == null)
			{
				return null;
			}
			java.io.BufferedReader reader = new java.io.BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			while ((line = reader.readLine()) != null)
			{
				String[] c = line.split("\t");
				if (!line.startsWith("#") && c.length == 6 && c[0].startsWith("https://"))
				{
					return c;
				}
			}
		}
		catch (IOException e)
		{
			log.debug("could not read where reports go", e);
		}
		return null;
	}

	@Override
	public void openLink(String url)
	{
		net.runelite.client.util.LinkBrowser.browse(url);
	}

	/** Levels as last seen and the Slayer task: what a report needs to be read, and no name. */
	@Override
	public String accountLine()
	{
		StringBuilder out = new StringBuilder();
		for (Skill skill : new Skill[]{Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC, Skill.PRAYER,
			Skill.HITPOINTS, Skill.SLAYER})
		{
			Integer level = levels.get(skill);
			if (level != null)
			{
				out.append(out.length() == 0 ? "" : ", ").append(skill.getName()).append(' ').append(level);
			}
		}
		Account a = account;
		if (a != null)
		{
			out.append(out.length() == 0 ? "" : "; ").append("Slayer task: ").append(a.slayerTask == null ? "none" : a.slayerTask);
		}
		return out.toString();
	}

	@Override
	public boolean canRoute()
	{
		// either of the two plugins that walk a route for another plugin
		return isOn(SHORTEST_PATH) || isOn(GPS);
	}

	@Override
	public boolean hasSetups()
	{
		return isOn(INVENTORY_SETUPS);
	}

	/** Whether a plugin of that name is installed and turned on. */
	private boolean isOn(String name)
	{
		for (Plugin p : pluginManager.getPlugins())
		{
			if (name.equals(p.getName()))
			{
				return pluginManager.isPluginEnabled(p);
			}
		}
		return false;
	}

	@Override
	public void route(String boss)
	{
		Places p = places;
		int[] tile = p == null ? null : p.of(boss);
		if (tile == null)
		{
			return;
		}
		clientThread.invokeLater(() ->
		{
			Map<String, Object> data = new HashMap<>();
			data.put("target", new WorldPoint(tile[0], tile[1], tile[2]));
			eventBus.post(new PluginMessage("shortestpath", "path", data));
		});
	}

	@Override
	public void showSetup(String name)
	{
		clientThread.invokeLater(() ->
		{
			Map<String, Object> data = new HashMap<>();
			data.put("setup", name);
			eventBus.post(new PluginMessage("inventory-setups", "view", data));
		});
	}

	@Override
	public int ruleKind(int taskId)
	{
		Rules r = rules;
		Rules.Rule rule = r == null ? null : r.of(taskId);
		return rule == null ? 0 : rule.beyondTheSet ? (rule.shapesTheSet() ? 3 : 2) : rule.shapesTheSet() ? 1 : 0;
	}

	/** A task's rule as the limits of the search, or null when it does not shape the set. */
	private Gear.Limits limits(int taskId)
	{
		Rules r = rules;
		Rules.Rule rule = taskId < 0 || r == null ? null : r.of(taskId);
		if (rule == null || !rule.shapesTheSet())
		{
			return null;
		}
		Gear.Limits l = new Gear.Limits();
		l.styles.addAll(rule.styles);
		l.weapon.addAll(rule.weapon);
		l.exclude.addAll(rule.exclude);
		l.oneHanded = rule.oneHanded;
		l.shield = rule.shield;
		l.meleeType = rule.meleeType;
		l.ammoWord = rule.ammoWord;
		l.spellElement = rule.spellElement;
		return l;
	}

	/**
	 * The names of everything wearable the player owns, for the items a task itself needs; null
	 * when the bank was never seen. The bank's part is read again only when the bank changes.
	 * Client thread.
	 */
	private Set<String> ownedNames(String savedBank)
	{
		if (savedBank == null)
		{
			return null;
		}
		if (!savedBank.equals(bankNamesFor) || bankNames == null)
		{
			Set<String> names = new HashSet<>();
			for (int raw : OwnedItems.decode(savedBank).ids())
			{
				int id = itemManager.canonicalize(raw);
				ItemStats stats = itemManager.getItemStats(id);
				if (stats != null && stats.getEquipment() != null)
				{
					names.add(itemManager.getItemComposition(id).getName());
				}
			}
			bankNames = names;
			bankNamesFor = savedBank;
		}
		Set<String> all = new HashSet<>(bankNames);
		for (int container : new int[]{InventoryID.INV, InventoryID.WORN})
		{
			ItemContainer c = client.getItemContainer(container);
			if (c != null)
			{
				for (Item item : c.getItems())
				{
					if (item.getId() > 0 && item.getQuantity() > 0)
					{
						all.add(itemManager.getItemComposition(itemManager.canonicalize(item.getId())).getName());
					}
				}
			}
		}
		return all;
	}

	@Override
	public void wantGear(String key, int generation, String boss, int taskId)
	{
		// a task against the clock carries more pieces to change into
		boolean speed = false;
		for (Task t : tasks)
		{
			speed |= t.id == taskId && t.type == com.caplanner.core.TaskType.SPEED;
		}
		final boolean againstTheClock = speed;
		clientThread.invokeLater(() ->
		{
			final Gear g = gear;
			final List<Gear.Stat> owned;
			final Gear.Levels levels;
			final boolean onTask;
			final Gear.Limits limits;
			final List<Bag.Wear> wear;
			final List<String> notes;
			final Map<String, Bag.Have> have;
			final Set<String> done = new HashSet<>();
			try
			{
				String saved = client.getGameState() == GameState.LOGGED_IN
					? configManager.getRSProfileConfiguration(CONFIG_GROUP, BANK_KEY) : null;
				final com.caplanner.gear.Fights several = fights;
				final boolean inParts = several != null && several.has(boss);
				if (g == null || (!g.knows(boss) && !inParts))
				{
					// no set is worked out for it, and so no inventory either: half an answer would pass for a whole one
					// (and the Gauntlet takes nothing in at all)
					answer(key, generation, GearView.of(GearView.Status.NO_BOSS));
					return;
				}
				if (saved == null)
				{
					answer(key, generation, GearView.of(GearView.Status.NO_BANK));
					return;
				}
				owned = owned(saved);
				levels = levels();
				// the runes owned, for the spells that can be cast
				levels.runes = new HashSet<>();
				for (String name : have(saved).keySet())
				{
					if (name.endsWith(" rune"))
					{
						levels.runes.add(name);
					}
				}
				// and the runes kept in a rune pouch, which are in no container
				int[] types = {VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3,
					VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6};
				int[] amounts = {VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3,
					VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6};
				net.runelite.api.EnumComposition pouch = client.getEnum(net.runelite.api.EnumID.RUNEPOUCH_RUNE);
				for (int i = 0; i < types.length; i++)
				{
					int type = client.getVarbitValue(types[i]);
					if (type > 0 && client.getVarbitValue(amounts[i]) > 0 && pouch != null)
					{
						int runeId = pouch.getIntValue(type);
						if (runeId > 0)
						{
							levels.runes.add(itemManager.getItemComposition(runeId).getName().toLowerCase());
						}
					}
				}
				onTask = requirements.isOnItsTask(boss, account);
				have = have(saved);
				done.addAll(doneThings());
				// what the fight itself asks of the gear comes on top of what the task asks
				Bag table = bag;
				wear = table == null ? new ArrayList<Bag.Wear>() : table.wear(boss, done);
				notes = table == null ? new ArrayList<String>() : table.notes(boss);
				List<String> wield = table == null ? new ArrayList<String>() : table.wield(boss);
				Gear.Limits ofTask = limits(taskId);
				if (!wear.isEmpty() || !wield.isEmpty())
				{
					ofTask = ofTask == null ? new Gear.Limits() : ofTask;
					for (Bag.Wear w : wear)
					{
						ofTask.mustWear.add(w.names);
					}
					if (ofTask.weapon.isEmpty())
					{
						ofTask.weapon.addAll(wield);
					}
				}
				limits = ofTask;
			}
			catch (RuntimeException e)
			{
				// whatever went wrong, the card must not stay on "Working it out..." for ever
				log.warn("could not read what is owned for {}", key, e);
				answer(key, generation, GearView.of(GearView.Status.NOTHING));
				return;
			}
			// the search itself needs nothing from the game: off the client thread, so it never stalls a tick
			final Runnable search = () ->
			{
				try
				{
					// a fight of several parts: the set is for the first, and pieces are carried for the others
					com.caplanner.gear.Fights.Plan plan = fights != null && fights.has(boss)
						? fights.plan(g, boss, owned, levels, onTask, limits, againstTheClock ? com.caplanner.gear.Fights.MOST_CARRIED_FOR_SPEED : 4) : null;
					Gear.Result r = plan != null ? plan.main : fights != null && fights.has(boss) ? null : g.best(boss, owned, levels, onTask, limits);
					if (r == null)
					{
						// the weapon the task names may be owned and merely have no figure here
						List<String> named = g.weaponsWithoutFigure(owned, limits);
						answer(key, generation, (named.isEmpty() ? GearView.of(GearView.Status.NOTHING) : GearView.noFigure(named))
							.with(new ArrayList<Bag.Line>(), notes));
						return;
					}
					Map<Integer, Integer> ids = new HashMap<>();
					Map<Integer, String> names = new HashMap<>();
					for (Map.Entry<Integer, Gear.Stat> e : r.items.entrySet())
					{
						ids.put(e.getKey(), e.getValue().itemId);
						names.put(e.getKey(), e.getValue().name);
					}
					Bag b = bag;
					List<GearView.Part> parts = new ArrayList<>();
					Set<Integer> held = new HashSet<>(ids.values());
					StringBuilder styles = new StringBuilder(r.style);
					if (plan != null)
					{
						for (com.caplanner.gear.Fights.Role role : plan.roles)
						{
							List<Bag.Line> carried = new ArrayList<>();
							for (Gear.Stat s : role.carried)
							{
								carried.add(Bag.Line.worn(s.itemId, s.name, true, ""));
								held.add(s.itemId);
							}
							parts.add(new GearView.Part(role.label, role.style, role.dps, carried, role.spell, role.note, role.nothing));
							if (!role.style.isEmpty())
							{
								styles.append(',').append(role.style);
							}
						}
					}
					GearView shape = GearView.of(GearView.Status.READY).withParts("", parts, new ArrayList<Bag.Line>());
					int left = Bag.SLOTS - shape.carriedSlots().size();
					// an inventory only for the fights that were gone through one by one; for the rest it is still to come
					boolean mapped = b != null && b.isMapped(boss);
					// where the fight was not gone through, the amounts of the wiki's inventory, said to be provisional
					// thralls are of the Arceuus spellbook: a set that casts from another has no use for the book
					Set<String> useless = new HashSet<>();
					if (r.spell != null)
					{
						useless.add("book of the dead");
					}
					// and what the task itself forbids is not carried
					Rules taskRules = rules;
					Rules.Rule ofThisTask = taskId < 0 || taskRules == null ? null : taskRules.of(taskId);
					boolean forbids = ofThisTask != null && !ofThisTask.without.isEmpty();
					if (forbids)
					{
						useless.addAll(ofThisTask.without);
					}
					Bag.Provisional provisional = b != null && !mapped && plan == null
						? b.provisional(boss, r.style, have, done, held, left, useless) : null;
					if (provisional != null)
					{
						Set<String> on = new HashSet<>();
						for (Gear.Stat s : r.items.values())
						{
							if (s.name != null)
							{
								on.add(s.name.toLowerCase());
							}
						}
						provisional = provisional.without(on);
					}
					List<Bag.Line> inventory = mapped ? b.of(boss, styles.toString(), have, done, held, left, useless)
						: provisional != null ? provisional.lines : new ArrayList<Bag.Line>();
					List<Bag.Line> worn = new ArrayList<>();
					for (int i = 0; i < wear.size(); i++)
					{
						Bag.Wear w = wear.get(i);
						Gear.Stat on = null;
						for (Gear.Stat s : r.items.values())
						{
							for (String part : w.names)
							{
								if (on == null && s.name != null && s.name.toLowerCase().contains(part))
								{
									on = s;
								}
							}
						}
						worn.add(on != null ? Bag.Line.worn(on.itemId, on.name, true, w.why) : Bag.Line.worn(w.itemId, w.name, false, w.why));
					}
					List<String> told = new ArrayList<>(notes);
					if (forbids)
					{
						told.add("This task forbids some of the usual supplies: they are left out of the inventory below.");
					}
					if (r.spell != null)
					{
						told.add("Counted casting " + r.spell + ": bring the runes for it.");
					}
					GearView view = GearView.ready(ids, names, r.style, r.dps, r.bossHitpoints, inventory).with(worn, told);
					if (plan != null)
					{
						if (!plan.mainNote.isEmpty())
						{
							told.add(0, plan.mainNote);
							view = view.with(worn, told);
						}
						view = view.withParts(plan.mainLabel, parts, inventory);
					}
					view.inventoryToCome = !mapped && provisional == null;
					view.provisional = provisional;
					answer(key, generation, view);
				}
				catch (RuntimeException e)
				{
					log.warn("could not pick gear for {}", key, e);
					answer(key, generation, GearView.of(GearView.Status.NOTHING));
				}
			};
			try
			{
				executor.submit(search);
			}
			catch (RuntimeException e)
			{
				// the client is closing and takes no more work
				answer(key, generation, GearView.of(GearView.Status.NOTHING));
			}
		});
	}

	/** What the player has done that makes something in the tables needless. Client thread. */
	private Set<String> doneThings()
	{
		Set<String> done = new HashSet<>();
		if (client.getVarbitValue(VarbitID.DESERT_DIARY_HARD_COMPLETE) == 1)
		{
			done.add("desert-hard");
		}
		if (client.getVarbitValue(VarbitID.KOUREND_DIARY_ELITE_COMPLETE) == 1)
		{
			done.add("kourend-elite");
		}
		return done;
	}

	@Subscribe
	public void onConfigChanged(net.runelite.client.events.ConfigChanged event)
	{
		if (CONFIG_GROUP.equals(event.getGroup()) && "language".equals(event.getKey()))
		{
			// another language: the panel is made again in it, and filled on the next tick
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					makePanel();
					lastShown = null;
					lastBank = NOT_SHOWN;
					lastBankText = null;
					dirty = true;
				}
			});
			return;
		}
		if (CONFIG_GROUP.equals(event.getGroup()) && !BANK_KEY.equals(event.getKey()))
		{
			// a setting of the plugin: the open card shows other buttons
			final CaPlannerPanel target = panel;
			SwingUtilities.invokeLater(() ->
			{
				if (target != null)
				{
					target.settingsChanged();
				}
			});
		}
	}

	private void answer(String key, int generation, GearView view)
	{
		final CaPlannerPanel target = panel;
		SwingUtilities.invokeLater(() ->
		{
			if (target != null)
			{
				target.gear(key, generation, view);
			}
		});
	}

	/** Everything wearable the player owns: the remembered bank, plus what is carried and worn now. Client thread. */
	private List<Gear.Stat> owned(String savedBank)
	{
		Set<Integer> ids = new HashSet<>(OwnedItems.decode(savedBank).ids());
		for (int container : new int[]{InventoryID.INV, InventoryID.WORN})
		{
			ItemContainer c = client.getItemContainer(container);
			if (c != null)
			{
				for (Item item : c.getItems())
				{
					if (item.getId() > 0 && item.getQuantity() > 0)
					{
						ids.add(item.getId());
					}
				}
			}
		}
		// arrows and bolts kept in Dizana's quiver are in no container
		if (client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT) > 0)
		{
			ids.add(client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO));
		}
		List<Gear.Stat> out = new ArrayList<>();
		Set<Integer> seen = new HashSet<>();
		for (int raw : ids)
		{
			int id = itemManager.canonicalize(raw);
			ItemStats stats = seen.add(id) ? itemManager.getItemStats(id) : null;
			ItemEquipmentStats e = stats == null ? null : stats.getEquipment();
			if (e == null)
			{
				continue;
			}
			Gear.Stat s = new Gear.Stat();
			s.itemId = id;
			s.slot = e.getSlot();
			s.twoHanded = e.isTwoHanded();
			s.name = itemManager.getItemComposition(id).getName();
			s.stab = e.getAstab();
			s.slash = e.getAslash();
			s.crush = e.getAcrush();
			s.magic = e.getAmagic();
			s.ranged = e.getArange();
			s.strength = e.getStr();
			s.rangedStrength = e.getRstr();
			s.magicDamage = e.getMdmg();
			s.speed = e.getAspeed();
			out.add(s);
		}
		return out;
	}

	/** The player's real levels and the attack prayers they have unlocked. Client thread. */
	private Gear.Levels levels()
	{
		Gear.Levels l = new Gear.Levels();
		l.attack = client.getRealSkillLevel(Skill.ATTACK);
		l.strength = client.getRealSkillLevel(Skill.STRENGTH);
		l.ranged = client.getRealSkillLevel(Skill.RANGED);
		l.magic = client.getRealSkillLevel(Skill.MAGIC);
		l.prayer = client.getRealSkillLevel(Skill.PRAYER);
		l.hitpoints = client.getRealSkillLevel(Skill.HITPOINTS);
		int defence = client.getRealSkillLevel(Skill.DEFENCE);
		boolean knightWaves = client.getVarbitValue(VarbitID.KR_KNIGHTWAVES_STATE) >= KNIGHT_WAVES_DONE;
		l.piety = knightWaves && l.prayer >= 70 && defence >= 70;
		l.chivalry = knightWaves && l.prayer >= 60 && defence >= 65;
		l.rigour = l.prayer >= 74 && client.getVarbitValue(VarbitID.PRAYER_RIGOUR_UNLOCKED) == 1;
		l.augury = l.prayer >= 77 && client.getVarbitValue(VarbitID.PRAYER_AUGURY_UNLOCKED) == 1;
		l.deadeye = l.prayer >= 62 && client.getVarbitValue(VarbitID.PRAYER_DEADEYE_UNLOCKED) == 1;
		l.vigour = l.prayer >= 63 && client.getVarbitValue(VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED) == 1;
		for (Skill skill : Skill.values())
		{
			l.all.put(skill.getName().toLowerCase(), client.getRealSkillLevel(skill));
		}
		l.ancients = net.runelite.api.Quest.DESERT_TREASURE_I.getState(client) == net.runelite.api.QuestState.FINISHED;
		return l;
	}

	/** The boss's icon from the hiscores, which the client already has; nothing when it is not there. */
	@Override
	public void bossIcon(String boss, JLabel into)
	{
		if (HISCORES.isEmpty())
		{
			for (HiscoreSkill s : HiscoreSkill.values())
			{
				HISCORES.put(s.getName(), s);
			}
		}
		HiscoreSkill skill = HISCORES.get(BossIcons.match(boss, HISCORES.keySet()));
		if (skill != null && skill.getSpriteId() > 0)
		{
			spriteManager.addSpriteTo(into, skill.getSpriteId(), 0);
		}
	}

	/** The sidebar icon: drawn here, so no image file is needed. */
	private static BufferedImage icon()
	{
		BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(214, 139, 39));
		g.fillRoundRect(1, 3, 22, 18, 6, 6);
		g.setColor(Color.BLACK);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		g.drawString("CA", 3, 17);
		g.dispose();
		return image;
	}
}
