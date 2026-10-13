package com.caplanner.ui;

import com.caplanner.core.Account;
import com.caplanner.core.Bag;
import com.caplanner.core.Ease;
import com.caplanner.core.GearView;
import com.caplanner.core.HowTo;
import com.caplanner.core.Planner;
import com.caplanner.core.Requirements;
import com.caplanner.core.Session;
import com.caplanner.core.Task;
import com.caplanner.core.Verdict;
import com.caplanner.gear.Gear;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * Development only: draws the panel as a player would see it, with sets picked by the real picker
 * from a file of owned items and with item pictures read from a folder, to show what the plugin
 * does. Usage: PromoShots out-folder tasks.json owned.tsv icons-folder
 * The icons folder holds "item id.png" files and a "bosses.tsv" of boss name, tab, item id; the
 * ids of pictures that were asked for and are not there are written to "missing.txt" in it.
 */
public final class PromoShots
{
	private static final Set<Integer> MISSING = new TreeSet<>();

	private PromoShots()
	{
	}

	public static void main(String[] args) throws Exception
	{
		File out = new File(args[0]);
		out.mkdirs();
		final List<Task> tasks = PanelPreview.load(new File(args[1]));
		final List<Gear.Stat> owned = owned(new File(args[2]));
		final File icons = new File(args[3]);
		final Map<String, Integer> bossIcons = new HashMap<>();
		File bossFile = new File(icons, "bosses.tsv");
		if (bossFile.exists())
		{
			try (BufferedReader in = reader(new FileInputStream(bossFile)))
			{
				String line;
				while ((line = in.readLine()) != null)
				{
					String[] c = line.split("\t");
					if (c.length == 2)
					{
						bossIcons.put(c[0], Integer.parseInt(c[1].trim()));
					}
				}
			}
		}
		final Map<Integer, Double> ease = Ease.read(reader(PromoShots.class.getResourceAsStream("/com/caplanner/completion.tsv")));
		// a made-up account some way in: what most players have done is done, three quests are not
		Requirements requirements = Requirements.read(reader(PromoShots.class.getResourceAsStream("/com/caplanner/requirements.tsv")));
		Account account = new Account();
		for (String skill : new String[]{"slayer", "agility", "strength", "hitpoints", "ranged", "farming", "fishing", "firemaking"})
		{
			account.levels.put(skill, 99);
		}
		account.levels.put("slayer", 90);
		for (String quest : requirements.questNames())
		{
			boolean left = quest.startsWith("Song of the Elves") || quest.startsWith("Dragon Slayer II") || quest.startsWith("Sins of the Father");
			account.quests.put(Requirements.key(quest), left ? Account.QuestProgress.NOT_STARTED : Account.QuestProgress.FINISHED);
		}
		account.slayerTask = "Hellhounds";
		account.done.add("wilderness-medium");
		Set<Integer> done = new HashSet<>();
		Map<Integer, Verdict> verdicts = new HashMap<>();
		for (Task t : tasks)
		{
			Verdict v = requirements.check(t, account);
			verdicts.put(t.id, v);
			if (v.can() && ease.getOrDefault(t.id, 0.0) >= 42 && !t.boss.equals("Vorkath") && !t.boss.equals("Sarachnis"))
			{
				done.add(t.id);
			}
		}
		final Planner.State state = new Planner.State(tasks, done, verdicts, ease);
		final Gear gear = new Gear();
		final com.caplanner.gear.Fights fights = com.caplanner.gear.Fights.read(reader(Gear.class.getResourceAsStream("fights.tsv")));
		final Gear.Levels levels = new Gear.Levels();
		levels.attack = levels.strength = levels.ranged = levels.magic = levels.prayer = levels.hitpoints = 99;
		levels.piety = levels.rigour = levels.augury = true;
		final Bag bag = Bag.read(reader(com.caplanner.CaPlannerPlugin.class.getResourceAsStream("bag.tsv")));
		final Map<String, Bag.Have> have = new HashMap<>();
		String[][] supplies = {{"27281", "1", "Divine rune pouch"}, {"25818", "1", "Book of the dead"},
			{"23685", "6", "Divine super combat potion(4)"}, {"23733", "6", "Divine ranging potion(4)"}, {"27641", "1", "Saturated heart"},
			{"30125", "5", "Prayer regeneration potion(4)"}, {"3024", "60", "Super restore(4)"}, {"3144", "400", "Cooked karambwan"},
			{"13441", "600", "Anglerfish"}, {"8013", "200", "Teleport to house"}, {"22209", "8", "Extended super antifire(4)"},
			{"12913", "8", "Anti-venom+(4)"}, {"952", "1", "Spade"}, {"4162", "1", "Rock hammer"}};
		for (String[] o : supplies)
		{
			have.put(o[2].toLowerCase(), new Bag.Have(Integer.parseInt(o[0]), Integer.parseInt(o[1]), o[2]));
		}
		if (args.length > 5 && "pt".equals(args[5]))
		{
			CaPlannerPanel.language(
				com.caplanner.core.Lang.read(reader(com.caplanner.CaPlannerPlugin.class.getResourceAsStream("lang/pt.tsv"))),
				HowTo.read(reader(com.caplanner.CaPlannerPlugin.class.getResourceAsStream("lang/pt-tasks.tsv"))));
		}
		final HowTo wiki = HowTo.read(reader(com.caplanner.CaPlannerPlugin.class.getResourceAsStream("howto-wiki.tsv")));
		final Session.Lengths lengths = Session.Lengths.read(reader(com.caplanner.CaPlannerPlugin.class.getResourceAsStream("sessions.tsv")));

		// page name, height, then what to open: "bosses", "boss:Name", "task:id", "unlock", "auto:SHORT", "auto"
		String[][] pages = {
			{"1-bosses", "760", "bosses"},
			{"2-gear", "1040", "boss:Vorkath"},
			{"7-parts", "1280", "boss:Dagannoth Rex"},
			{"3-task", "980", "task:" + args[4]},
			{"4-autopilot-ask", "420", "auto"},
			{"5-autopilot", "1100", "auto:SHORT"},
			{"6-unlock", "620", "unlock"},
		};
		for (final String[] page : pages)
		{
			final CaPlannerPanel[] panel = new CaPlannerPanel[1];
			final JFrame[] frame = new JFrame[1];
			SwingUtilities.invokeAndWait(() ->
			{
				panel[0] = new CaPlannerPanel(new CaPlannerPanel.Actions()
				{
					@Override
					public void bossIcon(String boss, JLabel into)
					{
						Integer id = bossIcons.get(boss);
						if (id != null)
						{
							picture(icons, id, into, 24);
						}
					}

					@Override
					public void itemIcon(int itemId, JLabel into)
					{
						picture(icons, itemId, into, 32);
					}

					@Override
					public void wantGear(String key, int generation, String boss, int taskId)
					{
						SwingUtilities.invokeLater(() ->
						{
							if (fights.has(boss))
							{
								com.caplanner.gear.Fights.Plan plan = fights.plan(gear, boss, owned, levels, false, null);
								if (plan == null)
								{
									panel[0].gear(key, generation, GearView.of(GearView.Status.NOTHING));
									return;
								}
								Map<Integer, Integer> ids = new HashMap<>();
								Map<Integer, String> names = new HashMap<>();
								for (Map.Entry<Integer, Gear.Stat> e : plan.main.items.entrySet())
								{
									ids.put(e.getKey(), e.getValue().itemId);
									names.put(e.getKey(), e.getValue().name);
								}
								List<GearView.Part> parts = new ArrayList<>();
								Set<Integer> held = new HashSet<>(ids.values());
								StringBuilder styles = new StringBuilder(plan.main.style);
								for (com.caplanner.gear.Fights.Role role : plan.roles)
								{
									List<Bag.Line> carried = new ArrayList<>();
									for (Gear.Stat c : role.carried)
									{
										carried.add(Bag.Line.worn(c.itemId, c.name, true, ""));
										held.add(c.itemId);
									}
									parts.add(new GearView.Part(role.label, role.style, role.dps, carried, role.spell, role.note, role.nothing));
									styles.append(',').append(role.style);
								}
								GearView shape = GearView.of(GearView.Status.READY).withParts("", parts, new ArrayList<Bag.Line>());
								List<Bag.Line> inventory = bag.of(boss, styles.toString(), have, new HashSet<String>(), held,
									Bag.SLOTS - shape.carriedSlots().size());
								List<String> told = new ArrayList<>();
								if (!plan.mainNote.isEmpty())
								{
									told.add(plan.mainNote);
								}
								panel[0].gear(key, generation, GearView.ready(ids, names, plan.main.style, plan.main.dps, plan.main.bossHitpoints,
									inventory).with(new ArrayList<Bag.Line>(), told).withParts(plan.mainLabel, parts, inventory));
								return;
							}
							if (!gear.knows(boss))
							{
								panel[0].gear(key, generation, GearView.of(GearView.Status.NO_BOSS)
									.withBag(bag.of(boss, null, have, new HashSet<String>(), new HashSet<Integer>())));
								return;
							}
							Gear.Limits limits = null;
							List<Bag.Wear> wear = bag.wear(boss, new HashSet<String>());
							if (!wear.isEmpty())
							{
								limits = new Gear.Limits();
								for (Bag.Wear w : wear)
								{
									limits.mustWear.add(w.names);
								}
							}
							Gear.Result r = gear.best(boss, owned, levels, false, limits);
							if (r == null)
							{
								panel[0].gear(key, generation, GearView.of(GearView.Status.NOTHING));
								return;
							}
							Map<Integer, Integer> ids = new HashMap<>();
							Map<Integer, String> names = new HashMap<>();
							for (Map.Entry<Integer, Gear.Stat> e : r.items.entrySet())
							{
								ids.put(e.getKey(), e.getValue().itemId);
								names.put(e.getKey(), e.getValue().name);
							}
							panel[0].gear(key, generation, GearView.ready(ids, names, r.style, r.dps, r.bossHitpoints,
								bag.of(boss, r.style, have, new HashSet<String>(), new HashSet<>(ids.values()))));
						});
					}

					@Override
					public int ruleKind(int taskId)
					{
						return 0;
					}

					@Override
					public void openLink(String url)
					{
					}

					@Override
					public boolean canSendReports()
					{
						return false;
					}

					@Override
					public void sendReport(String boss, String task, String words, String report, java.util.function.Consumer<Boolean> done)
					{
					}

					@Override
					public String accountLine()
					{
						return "";
					}

					@Override
					public List<String[]> tileLegend(String boss)
					{
						return new ArrayList<>();
					}

					@Override
					public boolean hasTiles(String boss)
					{
						return false;
					}

					@Override
					public boolean tilesMarked(String boss)
					{
						return false;
					}

					@Override
					public void toggleTiles(String boss)
					{
					}

					@Override
					public boolean canRoute()
					{
						return true;
					}

					@Override
					public boolean hasSetups()
					{
						return true;
					}

					@Override
					public boolean hasPlace(String boss)
					{
						return true;
					}

					@Override
					public void route(String boss)
					{
					}

					@Override
					public void showSetup(String name)
					{
					}

				});
				panel[0].guide(wiki, HowTo.none(), lengths);
				panel[0].render(state);
				panel[0].bankSeen(1184);
				String what = page[2];
				if (what.equals("unlock"))
				{
					panel[0].openUnlocks();
				}
				else if (what.startsWith("auto"))
				{
					panel[0].openAutopilot(what.contains(":") ? Session.Time.valueOf(what.substring(5)) : null);
				}
				else if (what.startsWith("boss:"))
				{
					panel[0].open(false, null, what.substring(5), -1);
				}
				else if (what.startsWith("task:"))
				{
					int id = Integer.parseInt(what.substring(5));
					String boss = null;
					for (Task t : tasks)
					{
						boss = t.id == id ? t.boss : boss;
					}
					panel[0].open(false, null, boss, id);
				}
				else
				{
					panel[0].open(false, null, null, -1);
				}
				frame[0] = new JFrame();
				frame[0].setUndecorated(true);
				frame[0].add(panel[0]);
				panel[0].setPreferredSize(new Dimension(242, Integer.parseInt(page[1])));
				frame[0].pack();
			});
			// the sets are answered a turn later, and drawing them takes another
			for (int i = 0; i < 4; i++)
			{
				SwingUtilities.invokeAndWait(() ->
				{
					frame[0].validate();
				});
			}
			final File target = new File(out, page[0] + ".png");
			SwingUtilities.invokeAndWait(() ->
			{
				BufferedImage image = new BufferedImage(panel[0].getWidth(), panel[0].getHeight(), BufferedImage.TYPE_INT_RGB);
				Graphics2D g = image.createGraphics();
				panel[0].printAll(g);
				g.dispose();
				try
				{
					ImageIO.write(image, "png", target);
				}
				catch (Exception e)
				{
					throw new IllegalStateException(e);
				}
				frame[0].dispose();
			});
			System.out.println("wrote " + target);
		}
		try (PrintWriter w = new PrintWriter(new File(icons, "missing.txt"), "UTF-8"))
		{
			for (int id : MISSING)
			{
				w.println(id);
			}
		}
		System.out.println(MISSING.size() + " pictures missing");
		System.exit(0);
	}

	private static void picture(File icons, int id, JLabel into, int size)
	{
		File file = new File(icons, id + ".png");
		if (!file.exists())
		{
			MISSING.add(id);
			return;
		}
		try
		{
			BufferedImage image = ImageIO.read(file);
			into.setIcon(new ImageIcon(image.getScaledInstance(size * image.getWidth() / Math.max(image.getWidth(), image.getHeight()),
				size * image.getHeight() / Math.max(image.getWidth(), image.getHeight()), Image.SCALE_SMOOTH)));
		}
		catch (Exception e)
		{
			MISSING.add(id);
		}
	}

	private static List<Gear.Stat> owned(File file) throws Exception
	{
		List<Gear.Stat> owned = new ArrayList<>();
		try (BufferedReader in = reader(new FileInputStream(file)))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				String[] c = line.split("\t");
				if (c.length < 13)
				{
					continue;
				}
				Gear.Stat s = new Gear.Stat();
				s.itemId = Integer.parseInt(c[0]);
				s.slot = Integer.parseInt(c[1]);
				s.twoHanded = "1".equals(c[2]);
				s.name = c[3];
				s.stab = Integer.parseInt(c[4]);
				s.slash = Integer.parseInt(c[5]);
				s.crush = Integer.parseInt(c[6]);
				s.magic = Integer.parseInt(c[7]);
				s.ranged = Integer.parseInt(c[8]);
				s.strength = Integer.parseInt(c[9]);
				s.rangedStrength = Integer.parseInt(c[10]);
				s.magicDamage = Double.parseDouble(c[11]);
				s.speed = Integer.parseInt(c[12]);
				owned.add(s);
			}
		}
		return owned;
	}

	private static BufferedReader reader(java.io.InputStream in)
	{
		return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
	}
}
