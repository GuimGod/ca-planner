package com.caplanner.ui;

import com.caplanner.core.Bag;
import com.caplanner.core.GearCard;
import com.caplanner.core.GearChoices;
import com.caplanner.core.GearView;
import com.caplanner.core.HowTo;
import com.caplanner.core.Lang;
import com.caplanner.core.Planner;
import com.caplanner.core.Rules;
import com.caplanner.core.Task;
import com.caplanner.core.TaskType;
import com.caplanner.core.Tier;
import com.caplanner.core.Verdict;
import com.caplanner.gear.Fights;
import com.caplanner.gear.Gear;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Development only: draws the gear card as the player changed it, and the window to choose an item, from a file of
 * owned items as the bench reads it, in English and in Portuguese.
 * Usage: gradlew editshots -Powned=owned.tsv -Pbank=bank.tsv -Picons=folder -Pout=folder
 */
public class EditShots
{
	private static Gear gear;
	private static Fights fights;
	private static Bag bag;
	private static Rules rules;
	private static List<Gear.Stat> owned;
	private static Map<String, Bag.Have> have;
	private static Map<Integer, GearCard.Item> items;

	public static void main(String[] args) throws Exception
	{
		owned = read(args[0]);
		have = bank(args[1], owned);
		final File icons = new File(args[2]);
		File out = new File(args[3]);
		out.mkdirs();
		gear = new Gear();
		try (InputStream in = Gear.class.getResourceAsStream("fights.tsv"))
		{
			fights = Fights.read(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		try (InputStream in = EditShots.class.getResourceAsStream("/com/caplanner/bag.tsv"))
		{
			bag = Bag.read(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		try (InputStream in = EditShots.class.getResourceAsStream("/com/caplanner/bag-wiki.tsv"))
		{
			bag.readWiki(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		try (InputStream in = EditShots.class.getResourceAsStream("/com/caplanner/rules.tsv"))
		{
			rules = Rules.read(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		items = new HashMap<>();
		for (Gear.Stat s : owned)
		{
			items.put(s.itemId, new GearCard.Item(s.itemId, s.name, false, s));
		}
		for (Bag.Have h : have.values())
		{
			items.putIfAbsent(h.itemId, new GearCard.Item(h.itemId, h.name, h.name.toLowerCase().endsWith(" rune"), null));
		}
		// the changes: the ring, a slot of the inventory, and the weapon of the blue form (Zulrah)
		GearChoices changes = GearChoices.none()
			.with("Vorkath", GearChoices.WORN, 12, 25975, 1, 28307)
			.with("Vorkath", GearChoices.BAG, 26, 385, 1, 13441);
		GearChoices moved = GearChoices.none().with("Vorkath", GearChoices.WORN, 12, 25975, 1, 28310);
		GearChoices rule = GearChoices.none().with("Doom of Mokhaiotl", GearChoices.WORN, 3, 22325, 1, 20997);
		for (String language : new String[]{"en", "pt"})
		{
			if ("pt".equals(language))
			{
				CaPlannerPanel.language(Lang.read(new InputStreamReader(EditShots.class.getResourceAsStream("/com/caplanner/lang/pt.tsv"),
					StandardCharsets.UTF_8)), HowTo.read(new InputStreamReader(
					EditShots.class.getResourceAsStream("/com/caplanner/lang/pt-tasks.tsv"), StandardCharsets.UTF_8)));
			}
			shot(new File(out, "1-vorkath-trocado-" + language + ".png"), "Vorkath", -1, changes, icons, true, 1500);
			shot(new File(out, "2-vorkath-recomendado-mudou-" + language + ".png"), "Vorkath", -1, moved, icons, false, 1000);
			shot(new File(out, "3-doom-task-uma-mao-" + language + ".png"), "Doom of Mokhaiotl", 618, rule, icons, false, 1000);
			chooser(new File(out, "4-janela-anel-" + language + ".png"), "Vorkath", changes, icons, "light");
		}
		System.out.println("wrote " + out);
		System.exit(0);
	}

	private static GearView card(String boss, int taskId, GearChoices changes)
	{
		GearCard.Input in = new GearCard.Input();
		in.gear = gear;
		in.fights = fights;
		in.bag = bag;
		in.rules = rules;
		in.boss = boss;
		in.taskId = taskId;
		in.taskLimits = GearCard.limitsOf(rules, taskId);
		in.owned = owned;
		Gear.Levels levels = new Gear.Levels();
		levels.attack = levels.strength = levels.ranged = levels.magic = levels.prayer = levels.hitpoints = 99;
		levels.piety = levels.rigour = levels.augury = true;
		levels.ancients = true;
		levels.runes = new HashSet<>(Arrays.asList("fire rune", "air rune", "water rune", "earth rune", "death rune", "blood rune"));
		in.levels = levels;
		in.have = have;
		in.done.addAll(Arrays.asList("desert-hard", "kourend-elite", "lumbridge-elite"));
		in.choices = changes.of(boss);
		in.items = items;
		return GearCard.build(in);
	}

	private static CaPlannerPanel.Actions actions(CaPlannerPanel[] panel, GearChoices changes, File icons)
	{
		return (CaPlannerPanel.Actions) java.lang.reflect.Proxy.newProxyInstance(EditShots.class.getClassLoader(),
			new Class<?>[]{CaPlannerPanel.Actions.class}, (proxy, method, a) ->
			{
				switch (method.getName())
				{
					case "itemIcon":
						picture(icons, (Integer) a[0], (JLabel) a[1]);
						return null;
					case "wantGear":
						final String key = (String) a[0];
						final int generation = (Integer) a[1];
						final GearView view = card((String) a[2], (Integer) a[3], changes);
						SwingUtilities.invokeLater(() -> panel[0].gear(key, generation, view));
						return null;
					case "ruleKind":
						Rules.Rule r = rules.of((Integer) a[0]);
						return r != null && r.shapesTheSet() ? 1 : 0;
					case "gearChanges":
						return changes.of((String) a[0]);
					case "findItems":
						@SuppressWarnings("unchecked")
						java.util.function.Consumer<List<CaPlannerPanel.Found>> answer = (java.util.function.Consumer<List<CaPlannerPanel.Found>>) a[2];
						List<CaPlannerPanel.Found> found = new ArrayList<>();
						for (Gear.Stat s : owned)
						{
							if (s.slot == (Integer) a[1] && s.name.toLowerCase().contains(((String) a[0]).toLowerCase()))
							{
								found.add(new CaPlannerPanel.Found(s.itemId, s.name, s.slot, false, true));
							}
						}
						found.add(new CaPlannerPanel.Found(25975, "Lightbearer", 12, false, true));
						found.add(new CaPlannerPanel.Found(1, "Lightbearer (example not owned)", 12, false, false));
						SwingUtilities.invokeLater(() -> answer.accept(found));
						return null;
					case "accountLine":
						return "";
					case "tileLegend":
					case "ways":
						return new ArrayList<String[]>();
					case "placeNote":
						return "";
					case "hasSetups":
					case "canRoute":
						return Boolean.TRUE;
					default:
						Class<?> rt = method.getReturnType();
						return rt == boolean.class ? Boolean.FALSE : rt == int.class ? 0 : null;
				}
			});
	}

	private static void shot(File file, String boss, int taskId, GearChoices changes, File icons, boolean editing, int height) throws Exception
	{
		final CaPlannerPanel[] panel = new CaPlannerPanel[1];
		final JFrame[] frame = new JFrame[1];
		List<Task> tasks = new ArrayList<>();
		tasks.add(new Task(taskId < 0 ? 1 : taskId, taskId == 618 ? "Duel of Mokhaiotl" : "A kill", "Kill " + boss + " once.", Tier.ELITE,
			TaskType.RESTRICTION, boss));
		final Planner.State state = new Planner.State(tasks, new HashSet<Integer>(), new HashMap<Integer, Verdict>(), new HashMap<Integer, Double>());
		SwingUtilities.invokeAndWait(() ->
		{
			panel[0] = new CaPlannerPanel(actions(panel, changes, icons));
			panel[0].render(state);
			panel[0].bankSeen(900);
			panel[0].editing(boss, editing);
			panel[0].open(false, null, boss, taskId);
			frame[0] = new JFrame();
			frame[0].setUndecorated(true);
			frame[0].add(panel[0]);
			panel[0].setPreferredSize(new Dimension(242, height));
			frame[0].pack();
		});
		for (int i = 0; i < 6; i++)
		{
			SwingUtilities.invokeAndWait(() -> frame[0].validate());
		}
		paint(panel[0], file);
		SwingUtilities.invokeAndWait(() -> frame[0].dispose());
	}

	private static void chooser(File file, String boss, GearChoices changes, File icons, String typed) throws Exception
	{
		final CaPlannerPanel[] panel = new CaPlannerPanel[1];
		final JFrame[] frame = new JFrame[1];
		final GearView view = card(boss, -1, changes);
		SwingUtilities.invokeAndWait(() ->
		{
			panel[0] = new CaPlannerPanel(actions(panel, changes, icons));
			JComponent content = panel[0].chooserContent(boss, boss, GearChoices.WORN, 12, 12, CaPlannerPanel.say("Ring"), view.nameBySlot.get(12),
				view.recommended == null ? null : view.recommended.nameBySlot.get(12), 28307, view, null, () ->
				{
				});
			frame[0] = new JFrame();
			frame[0].setUndecorated(true);
			frame[0].setContentPane(content);
			frame[0].pack();
			field(content).setText(typed);
		});
		Thread.sleep(800);
		for (int i = 0; i < 4; i++)
		{
			SwingUtilities.invokeAndWait(() ->
			{
				frame[0].pack();
				frame[0].validate();
			});
		}
		paint((JComponent) frame[0].getContentPane(), file);
		SwingUtilities.invokeAndWait(() -> frame[0].dispose());
	}

	private static JTextField field(Container c)
	{
		for (Component child : c.getComponents())
		{
			if (child instanceof JTextField)
			{
				return (JTextField) child;
			}
			if (child instanceof Container)
			{
				JTextField f = field((Container) child);
				if (f != null)
				{
					return f;
				}
			}
		}
		return null;
	}

	private static void paint(JComponent c, File file) throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			BufferedImage image = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics2D g = image.createGraphics();
			c.printAll(g);
			g.dispose();
			try
			{
				ImageIO.write(image, "png", file);
			}
			catch (Exception e)
			{
				throw new IllegalStateException(e);
			}
		});
	}

	private static void picture(File icons, int id, JLabel into)
	{
		File file = new File(icons, id + ".png");
		if (!file.exists())
		{
			into.setText(String.valueOf(id));
			return;
		}
		try
		{
			BufferedImage image = ImageIO.read(file);
			int size = 32;
			into.setIcon(new ImageIcon(image.getScaledInstance(size * image.getWidth() / Math.max(image.getWidth(), image.getHeight()),
				size * image.getHeight() / Math.max(image.getWidth(), image.getHeight()), Image.SCALE_SMOOTH)));
		}
		catch (Exception e)
		{
			into.setText(String.valueOf(id));
		}
	}

	private static List<Gear.Stat> read(String file) throws Exception
	{
		List<Gear.Stat> out = new ArrayList<>();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				String[] c = line.split("\t");
				if (c.length < 13 || !c[0].matches("\\d+"))
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
				out.add(s);
			}
		}
		return out;
	}

	private static Map<String, Bag.Have> bank(String file, List<Gear.Stat> worn) throws Exception
	{
		Map<String, Bag.Have> out = new HashMap<>();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				String[] c = line.split("\t");
				if (c.length >= 3 && c[0].matches("\\d+") && c[2].matches("\\d+"))
				{
					out.put(c[1].toLowerCase(), new Bag.Have(Integer.parseInt(c[0]), Integer.parseInt(c[2]), c[1]));
				}
			}
		}
		Set<String> names = new HashSet<>(out.keySet());
		for (Gear.Stat s : worn)
		{
			if (!names.contains(s.name.toLowerCase()))
			{
				out.put(s.name.toLowerCase(), new Bag.Have(s.itemId, 1, s.name));
			}
		}
		return out;
	}
}
