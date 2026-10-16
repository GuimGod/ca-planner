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
import com.caplanner.core.GearCard;
import com.caplanner.core.GearChoices;
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
import javax.swing.ImageIcon;
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
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

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
	/** The setting that, ticked, forgets the account's changes to the gear and unticks itself. */
	static final String FORGET_CHANGES = "forgetGearChanges";
	/** Quests change without an event the plugin listens to: look again this often. */
	private static final int LOOK_AGAIN_TICKS = 100;
	/** How soon to try again when the game's task tables come back empty. */
	private static final int RETRY_TICKS = 5;
	private static final int EMPTY_READS_TO_WARN = 20;
	private static final int NOT_SHOWN = Integer.MIN_VALUE;
	/** Hiscores name to its entry; filled on first use. */
	private static final Map<String, HiscoreSkill> HISCORES = new HashMap<>();
	/** The side of the place the panel keeps for a boss's icon. */
	private static final int BOSS_ICON = 26;

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

	@Inject
	private net.runelite.client.ui.overlay.OverlayManager overlayManager;

	@Inject
	private TilesOverlay tilesOverlay;

	private volatile com.caplanner.core.Tiles tiles;
	/** The boss whose tiles are drawn in the game, because the player asked; null for none. */
	private volatile String tilesBoss;

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
	/**
	 * The account's changes to the recommended gear and the text of the settings they were read from, together, so that
	 * the Swing thread and the client thread never see one without the other.
	 */
	private volatile Object[] choicesRead = {null, GearChoices.none()};
	/** Every item of the game, for the search of items; null until it is read. Client thread. */
	private com.caplanner.core.ItemSearch itemIndex;
	/** The items being read, a part a tick, and the item id they are read up to. Client thread. */
	private List<com.caplanner.core.ItemSearch.Entry> itemIndexSoFar;
	private int itemIndexedUpTo;
	private long itemIndexStarted;
	/** Items of the game read for the search in one tick. */
	private static final int INDEX_STEP = 4000;

	@Override
	protected void startUp()
	{
		requirements = readRequirements();
		ease = readEase();
		places = readPlaces();
		tiles = readTiles();
		tilesBoss = null;
		overlayManager.add(tilesOverlay);
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
		if (gear != null && config.updateData())
		{
			// a newer copy of the engine's tables, when Combat Check's repository has one: asked for once
			gear.fetchNewerTables(okHttpClient, r -> clientThread.invokeLater(r));
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
		overlayManager.remove(tilesOverlay);
		tilesBoss = null;
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

	private static com.caplanner.core.Tiles readTiles()
	{
		try (InputStream in = CaPlannerPlugin.class.getResourceAsStream("tiles.tsv"))
		{
			return in == null ? com.caplanner.core.Tiles.none()
				: com.caplanner.core.Tiles.read(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			throw new IllegalStateException(e);
		}
	}

	/** The tiles being drawn in the game now: those of the boss the player asked for; empty for none. */
	List<com.caplanner.core.Tiles.Tile> tilesShown()
	{
		com.caplanner.core.Tiles table = tiles;
		String boss = tilesBoss;
		return table == null || boss == null ? java.util.Collections.<com.caplanner.core.Tiles.Tile>emptyList() : table.of(boss);
	}

	@Override
	public List<String[]> tileLegend(String boss)
	{
		com.caplanner.core.Tiles table = tiles;
		return table == null ? new ArrayList<String[]>() : table.legend(boss);
	}

	@Override
	public boolean hasTiles(String boss)
	{
		com.caplanner.core.Tiles table = tiles;
		return table != null && !table.of(boss).isEmpty();
	}

	@Override
	public boolean tilesMarked(String boss)
	{
		return boss != null && boss.equals(tilesBoss);
	}

	/** Draws the tiles of a boss in the game, or stops drawing them: only ever because the player pressed the button. */
	@Override
	public void toggleTiles(String boss)
	{
		tilesBoss = boss != null && boss.equals(tilesBoss) ? null : boss;
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
			if (ids[i] > 0 && itemManager.getItemComposition(ids[i]).getPlaceholderTemplateId() != -1)
			{
				// a placeholder is the item, owned (it is canonicalized later), with none of it in stock: it counts for
				// the sets, and the inventory does not take a potion that is not there
				quantities[i] = 0;
			}
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
				if (t.boss.equals("Grotesque Guardians"))
				{
					account.done.add("gg-roof");
				}
				if (t.boss.equals("Abyssal Sire"))
				{
					account.done.add("abyss-access");
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
		mark(p == null ? null : p.of(boss));
	}

	@Override
	public String placeNote(String boss)
	{
		Places p = places;
		return p == null ? "" : p.note(boss);
	}

	@Override
	public List<String[]> ways(String boss)
	{
		Places p = places;
		List<String[]> out = new ArrayList<>();
		if (p != null)
		{
			for (Places.Way w : p.ways(boss))
			{
				out.add(new String[]{w.label, w.note});
			}
		}
		return out;
	}

	@Override
	public void routeWay(String boss, int index)
	{
		Places p = places;
		List<Places.Way> list = p == null ? null : p.ways(boss);
		mark(list == null || index < 0 || index >= list.size() ? null : list.get(index).tile());
	}

	/** Asks the route plugins for the way to a tile; nothing when there is none. */
	private void mark(int[] tile)
	{
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

	/**
	 * The account's changes to the recommended gear, from its own settings (the RuneScape profile): read again only
	 * when the text there changes. None before an account is logged in.
	 */
	private GearChoices choices()
	{
		String text = configManager.getRSProfileKey() == null ? null : configManager.getRSProfileConfiguration(CONFIG_GROUP, GearChoices.KEY);
		if (text == null || text.isEmpty())
		{
			return GearChoices.none();
		}
		Object[] read = choicesRead;
		if (!text.equals(read[0]))
		{
			// a text that cannot be read gives no changes, and the card is the recommendation
			read = new Object[]{text, GearChoices.decode(text)};
			choicesRead = read;
		}
		return (GearChoices) read[1];
	}

	/** Keeps the account's changes in its own settings, and has the cards of the boss worked out again. */
	private void keep(String boss, GearChoices now)
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}
		String text = now.encode();
		if (text.isEmpty())
		{
			configManager.unsetRSProfileConfiguration(CONFIG_GROUP, GearChoices.KEY);
		}
		else
		{
			configManager.setRSProfileConfiguration(CONFIG_GROUP, GearChoices.KEY, text);
		}
		final CaPlannerPanel target = panel;
		SwingUtilities.invokeLater(() ->
		{
			if (target != null)
			{
				if (boss == null)
				{
					target.forgetGear();
				}
				else
				{
					target.forgetGearOf(boss);
				}
			}
		});
	}

	@Override
	public List<GearChoices.Pin> gearChanges(String boss)
	{
		return choices().of(boss);
	}

	@Override
	public String changeGear(String boss, String card, String place, int slot, int itemId, int quantity, int recommended)
	{
		GearChoices before = choices();
		GearChoices now = before.with(boss, place, slot, itemId, quantity, recommended, card);
		if (now == before)
		{
			// the text would be longer than the settings can read back, or the boss has all the changes it can keep
			return "No more changes can be kept: put some slots back to the recommendation first.";
		}
		keep(boss, now);
		return null;
	}

	@Override
	public void keepRecommendation(String boss, String place, int slot)
	{
		keep(boss, choices().without(boss, place, slot));
	}

	@Override
	public void forgetGearChanges(String boss)
	{
		keep(boss, choices().withoutBoss(boss));
	}

	/**
	 * Finds items of the game by a part of their name, the ones the player owns first. The items are read from the client
	 * once, a part a tick on the client thread (they are many), and the search is answered when they are all read.
	 */
	@Override
	public void findItems(String typed, int slot, java.util.function.Consumer<List<CaPlannerPanel.Found>> answer)
	{
		final String q = typed == null ? "" : typed.trim().toLowerCase(java.util.Locale.ROOT);
		if (q.length() < 2)
		{
			SwingUtilities.invokeLater(() -> answer.accept(new ArrayList<CaPlannerPanel.Found>()));
			return;
		}
		clientThread.invokeLater(() ->
		{
			if (!readItemIndex())
			{
				// more of the items to read first: this runs again on the next tick
				return false;
			}
			String saved = client.getGameState() == GameState.LOGGED_IN
				? configManager.getRSProfileConfiguration(CONFIG_GROUP, BANK_KEY) : null;
			Map<String, Bag.Have> owned = saved == null ? new HashMap<String, Bag.Have>() : have(saved);
			final List<CaPlannerPanel.Found> found = new ArrayList<>();
			for (com.caplanner.core.ItemSearch.Hit hit : itemIndex.find(q, slot, owned, this::slotOf))
			{
				found.add(new CaPlannerPanel.Found(hit.itemId, hit.entry.name, hit.entry.slot, hit.entry.stackable, hit.owned));
			}
			SwingUtilities.invokeLater(() -> answer.accept(found));
			return true;
		});
	}

	/** The equipment slot of an item, or -1. Client thread. */
	private int slotOf(int id)
	{
		ItemStats stats = itemManager.getItemStats(id);
		return stats == null || stats.getEquipment() == null ? -1 : stats.getEquipment().getSlot();
	}

	/**
	 * Reads a part of the game's items into the index: true when it is all read. Notes, placeholders and the other
	 * forms of an item are left out, and of the items of one name and slot the first is kept. Client thread.
	 */
	private boolean readItemIndex()
	{
		if (itemIndex != null)
		{
			return true;
		}
		if (itemIndexSoFar == null)
		{
			itemIndexSoFar = new ArrayList<>();
			itemIndexedUpTo = 0;
			itemIndexStarted = System.nanoTime();
		}
		int count = client.getItemCount();
		int end = Math.min(count, itemIndexedUpTo + INDEX_STEP);
		for (int id = itemIndexedUpTo; id < end; id++)
		{
			try
			{
				net.runelite.api.ItemComposition c = itemManager.getItemComposition(id);
				String name = c == null ? null : c.getName();
				if (name == null || name.isEmpty() || "null".equals(name) || c.getNote() != -1 || c.getPlaceholderTemplateId() != -1
					|| itemManager.canonicalize(id) != id)
				{
					continue;
				}
				itemIndexSoFar.add(new com.caplanner.core.ItemSearch.Entry(id, name, slotOf(id), c.isStackable()));
			}
			catch (RuntimeException e)
			{
				// an item the cache cannot give is left out
				log.debug("item {} left out of the search", id, e);
			}
		}
		itemIndexedUpTo = end;
		if (end < count)
		{
			return false;
		}
		itemIndex = new com.caplanner.core.ItemSearch(itemIndexSoFar);
		itemIndexSoFar = null;
		log.debug("{} items read for the search in {} ms", itemIndex.size(), (System.nanoTime() - itemIndexStarted) / 1_000_000);
		return true;
	}

	/** Whether a setting of this plugin is data it keeps for itself (the bank, the changes to the gear), not a setting. */
	static boolean isOwnData(String key)
	{
		return BANK_KEY.equals(key) || GearChoices.KEY.equals(key);
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
		return GearCard.limitsOf(rules, taskId);
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
			final GearCard.Input input = new GearCard.Input();
			try
			{
				String saved = client.getGameState() == GameState.LOGGED_IN
					? configManager.getRSProfileConfiguration(CONFIG_GROUP, BANK_KEY) : null;
				if (!GearCard.knows(g, fights, boss))
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
				final List<Gear.Stat> owned = owned(saved);
				final Gear.Levels levels = levels();
				// the runes owned, for the spells that can be cast
				levels.runes = new HashSet<>();
				for (Map.Entry<String, Bag.Have> e : have(saved).entrySet())
				{
					if (e.getKey().endsWith(" rune") && e.getValue().quantity > 0)
					{
						levels.runes.add(e.getKey());
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
				input.gear = g;
				input.fights = fights;
				input.bag = bag;
				input.rules = rules;
				input.boss = boss;
				input.taskId = taskId;
				input.taskLimits = limits(taskId);
				input.owned = owned;
				input.levels = levels;
				input.onTask = requirements.isOnItsTask(boss, account);
				input.have = have(saved);
				input.done.addAll(doneThings());
				input.againstTheClock = againstTheClock;
				// the player's changes to the boss's gear, and the items they put in, as the game has them
				input.choices = choices().of(boss);
				input.cardKey = key;
				for (GearChoices.Pin pin : input.choices)
				{
					if (pin.itemId > 0 && !input.items.containsKey(pin.itemId))
					{
						GearCard.Item item = item(pin.itemId);
						if (item != null)
						{
							input.items.put(pin.itemId, item);
						}
					}
				}
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
					answer(key, generation, GearCard.build(input));
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
		if (client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) == 1)
		{
			// the fairy rings without a Dramen or Lunar staff
			done.add("lumbridge-elite");
		}
		return done;
	}

	@Subscribe
	public void onConfigChanged(net.runelite.client.events.ConfigChanged event)
	{
		if (CONFIG_GROUP.equals(event.getGroup()) && FORGET_CHANGES.equals(event.getKey()))
		{
			if (Boolean.parseBoolean(event.getNewValue()))
			{
				// the account's changes are forgotten, and the box unticks itself (which comes back here as false)
				keep(null, GearChoices.none());
				configManager.setConfiguration(CONFIG_GROUP, FORGET_CHANGES, false);
			}
			return;
		}
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
		if (CONFIG_GROUP.equals(event.getGroup()) && !isOwnData(event.getKey()))
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
			Gear.Stat s = seen.add(id) ? stat(id) : null;
			if (s != null)
			{
				out.add(s);
			}
		}
		return out;
	}

	/** An item that can be worn, with its bonuses; null for one that cannot. Client thread. */
	private Gear.Stat stat(int id)
	{
		ItemStats stats = itemManager.getItemStats(id);
		ItemEquipmentStats e = stats == null ? null : stats.getEquipment();
		if (e == null)
		{
			return null;
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
		return s;
	}

	/** An item the player put in a slot, as the game has it; null for an id the game does not know. Client thread. */
	private GearCard.Item item(int id)
	{
		net.runelite.api.ItemComposition c = itemManager.getItemComposition(id);
		String name = c == null ? null : c.getName();
		if (name == null || name.isEmpty() || "null".equals(name))
		{
			return null;
		}
		return new GearCard.Item(id, name, c.isStackable(), stat(id));
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

	/** The boss's icon from the hiscores, which the client already has; for a boss they do not have, an item of its own. */
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
			return;
		}
		int item = BossIcons.item(boss);
		if (item > 0)
		{
			// an item's picture is 36 by 32, larger than a boss's place: brought down to it
			AsyncBufferedImage image = itemManager.getImage(item);
			image.onLoaded(() -> SwingUtilities.invokeLater(() ->
				into.setIcon(new ImageIcon(ImageUtil.resizeImage(image, BOSS_ICON, BOSS_ICON * 32 / 36)))));
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
