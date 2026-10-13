package com.caplanner.ui;

import com.caplanner.core.Account;
import com.caplanner.core.Ease;
import com.caplanner.core.Planner;
import com.caplanner.core.Requirements;
import com.caplanner.core.Task;
import com.caplanner.core.TaskType;
import com.caplanner.core.Tier;
import com.caplanner.core.Verdict;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Draws the panel to PNG files without the game client, so the layout can be looked at.
 * Usage: gradlew preview -Pout=some/folder -PcaTasks=path/to/tasks.json -PcaDone=1,2,3
 * The tasks file is a JSON list of {id, name, monster, task, tier, type}; without it a few
 * made-up tasks are used.
 */
public class PanelPreview
{
	private static final int HEIGHT = 720;

	public static void main(String[] args) throws Exception
	{
		File out = new File(args.length > 0 ? args[0] : "build/preview");
		out.mkdirs();
		List<Task> tasks = args.length > 1 && !args[1].isEmpty() ? load(new File(args[1])) : sample();
		Set<Integer> done = new HashSet<>();
		if (args.length > 2)
		{
			for (String id : args[2].split(","))
			{
				if (!id.trim().isEmpty())
				{
					done.add(Integer.parseInt(id.trim()));
				}
			}
		}
		// a made-up account: every level 99, every quest done, on a Slayer task of hellhounds
		Requirements requirements = Requirements.read(new InputStreamReader(
			PanelPreview.class.getResourceAsStream("/com/caplanner/requirements.tsv"), StandardCharsets.UTF_8));
		Account account = new Account();
		for (String skill : new String[]{"slayer", "agility", "strength", "hitpoints", "ranged", "farming", "fishing", "firemaking"})
		{
			account.levels.put(skill, 99);
		}
		for (String quest : requirements.questNames())
		{
			account.quests.put(Requirements.key(quest), Account.QuestProgress.FINISHED);
		}
		account.slayerTask = "Hellhounds";
		Map<Integer, Verdict> verdicts = new HashMap<>();
		for (Task t : tasks)
		{
			verdicts.put(t.id, requirements.check(t, account));
		}
		Map<Integer, Double> ease = Ease.read(new InputStreamReader(
			PanelPreview.class.getResourceAsStream("/com/caplanner/completion.tsv"), StandardCharsets.UTF_8));
		final Planner.State state = new Planner.State(tasks, done, verdicts, ease);
		Object[][] pages = {
			{"1-bosses", false, null, null, -1},
			{"2-search", false, "var", null, -1},
			{"3-boss", false, null, "Theatre of Blood", -1},
			{"7-gear", false, null, "Vardorvis", -1},
			{"8-unlock", null, null, null, -1},
			{"9-autopilot", null, null, null, -2},
			{"10-autopilot-ask", null, null, null, -3},
			{"4-task", false, null, "Alchemical Hydra", firstOf(tasks, "Alchemical Hydra")},
			{"5-all", true, null, null, -1},
			{"6-all-search", true, "earth", null, -1},
		};
		for (Object[] page : pages)
		{
			final CaPlannerPanel[] panel = new CaPlannerPanel[1];
			final JFrame[] frame = new JFrame[1];
			SwingUtilities.invokeAndWait(() ->
			{
				panel[0] = new CaPlannerPanel(new CaPlannerPanel.Actions()
				{
					@Override
					public void bossIcon(String boss, javax.swing.JLabel into)
					{
					}

					@Override
					public void itemIcon(int itemId, javax.swing.JLabel into)
					{
						into.setText(String.valueOf(itemId));
						into.setFont(into.getFont().deriveFont(8f));
						into.setForeground(java.awt.Color.LIGHT_GRAY);
					}

					@Override
					public void wantGear(String key, int generation, String boss, int taskId)
					{
					}

					@Override
					public int ruleKind(int taskId)
					{
						return taskId == 493 ? 2 : 0;
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
						return "Vardorvis".equals(boss);
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
				Map<Integer, Integer> ids = new HashMap<>();
				Map<Integer, String> names = new HashMap<>();
				int[][] set = {{0, 31097}, {1, 21295}, {2, 29801}, {3, 28338}, {4, 31100}, {7, 31103}, {9, 22981}, {10, 31097}, {12, 28316}, {13, 22947}};
				for (int[] s : set)
				{
					ids.put(s[0], s[1]);
					names.put(s[0], "item " + s[1]);
				}
				java.util.List<com.caplanner.core.Bag.Line> bag = new java.util.ArrayList<>();
				try
				{
					Map<String, com.caplanner.core.Bag.Have> have = new HashMap<>();
					String[][] owned = {{"27281", "1", "Divine rune pouch"}, {"25818", "1", "Book of the dead"}, {"13280", "1", "Max cape"},
						{"23685", "2", "Divine super combat potion(4)"}, {"30125", "3", "Prayer regeneration potion(4)"},
						{"3024", "40", "Super restore(4)"}, {"3144", "200", "Cooked karambwan"}, {"13441", "300", "Anglerfish"}};
					for (String[] o : owned)
					{
						have.put(o[2].toLowerCase(), new com.caplanner.core.Bag.Have(Integer.parseInt(o[0]), Integer.parseInt(o[1]), o[2]));
					}
					bag = com.caplanner.core.Bag.read(new java.io.InputStreamReader(
						com.caplanner.CaPlannerPlugin.class.getResourceAsStream("bag.tsv"), java.nio.charset.StandardCharsets.UTF_8))
						.of("Grotesque Guardians", "Melee", have);
				}
				catch (java.io.IOException e)
				{
					throw new IllegalStateException(e);
				}
				panel[0].gear("Vardorvis", 0, com.caplanner.core.GearView.ready(ids, names, "Melee", 8.4, 700, bag));
				panel[0].gear("Theatre of Blood", 0, com.caplanner.core.GearView.of(com.caplanner.core.GearView.Status.NO_BOSS));
				panel[0].gear("Alchemical Hydra", 0, com.caplanner.core.GearView.of(com.caplanner.core.GearView.Status.NO_BANK));
				panel[0].render(state);
				panel[0].bankSeen(839);
				try
				{
					panel[0].guide(
						com.caplanner.core.HowTo.read(new java.io.InputStreamReader(com.caplanner.CaPlannerPlugin.class
							.getResourceAsStream("howto-wiki.tsv"), java.nio.charset.StandardCharsets.UTF_8)),
						com.caplanner.core.HowTo.none(),
						com.caplanner.core.Session.Lengths.read(new java.io.InputStreamReader(com.caplanner.CaPlannerPlugin.class
							.getResourceAsStream("sessions.tsv"), java.nio.charset.StandardCharsets.UTF_8)));
				}
				catch (java.io.IOException e)
				{
					throw new IllegalStateException(e);
				}
				if (page[1] == null && (Integer) page[4] <= -2)
				{
					panel[0].openAutopilot((Integer) page[4] == -2 ? com.caplanner.core.Session.Time.SHORT : null);
				}
				else if (page[1] == null)
				{
					panel[0].openUnlocks();
				}
				else
				{
					panel[0].open((Boolean) page[1], (String) page[2], (String) page[3], (Integer) page[4]);
				}
				frame[0] = new JFrame();
				frame[0].setUndecorated(true);
				frame[0].add(panel[0]);
				panel[0].setPreferredSize(new Dimension(242, HEIGHT));
				frame[0].pack();
			});
			SwingUtilities.invokeAndWait(() ->
			{
			});
			File target = new File(out, page[0] + ".png");
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
		System.exit(0);
	}

	private static int firstOf(List<Task> tasks, String boss)
	{
		for (Task t : tasks)
		{
			if (t.boss.equals(boss))
			{
				return t.id;
			}
		}
		return -1;
	}

	static List<Task> load(File file) throws Exception
	{
		String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
		List<Task> tasks = new ArrayList<>();
		for (JsonElement e : new Gson().fromJson(json, JsonArray.class))
		{
			JsonObject o = e.getAsJsonObject();
			Tier tier = Tier.valueOf(o.get("tier").getAsString().toUpperCase());
			TaskType type = TaskType.OTHER;
			for (TaskType t : TaskType.values())
			{
				if (t.label().equalsIgnoreCase(o.get("type").getAsString()))
				{
					type = t;
				}
			}
			tasks.add(new Task(o.get("id").getAsInt(), o.get("name").getAsString(), o.get("task").getAsString(),
				tier, type, o.get("monster").getAsString()));
		}
		return tasks;
	}

	private static List<Task> sample()
	{
		List<Task> tasks = new ArrayList<>();
		tasks.add(new Task(0, "Noxious Foe", "Kill an Aberrant Spectre.", Tier.EASY, TaskType.KILL_COUNT, "Aberrant Spectre"));
		tasks.add(new Task(480, "Perfect Vardorvis", "Kill Vardorvis perfectly.", Tier.ELITE, TaskType.PERFECTION, "Vardorvis"));
		tasks.add(new Task(486, "Vardorvis Speed-Chaser", "Kill Vardorvis in less than 1:05.", Tier.MASTER, TaskType.SPEED, "Vardorvis"));
		tasks.add(new Task(574, "You're a wizard", "Kill the Hueycoatl using only earth spells.", Tier.MEDIUM, TaskType.RESTRICTION, "The Hueycoatl"));
		return tasks;
	}
}
