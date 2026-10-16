package com.caplanner.ui;

import com.caplanner.core.CardJson;
import com.caplanner.core.GearView;
import com.caplanner.core.HowTo;
import com.caplanner.core.Lang;
import com.caplanner.core.Planner;
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
import java.awt.Image;
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
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * Development only: draws the boss page of one gear card, as the bench wrote it to JSON (gradlew bench -Pjson=...),
 * with the item pictures of a folder of "id.png" files, so a card can be looked at outside the game.
 * Usage: gradlew cardshot -Pcards=cards.json -Pboss="Corporeal Beast" -Pout=card.png -Picons=folder [-Plang=pt] [-Pheight=1400]
 */
public class CardShot
{
	public static void main(String[] args) throws Exception
	{
		JsonArray cards = new Gson().fromJson(new String(Files.readAllBytes(new File(args[0]).toPath()), StandardCharsets.UTF_8), JsonArray.class);
		final String boss = args[1];
		JsonObject card = null;
		for (JsonElement e : cards)
		{
			String entry = e.getAsJsonObject().get("entry").getAsString();
			if (entry.equals(boss) || entry.startsWith(boss + "#"))
			{
				card = e.getAsJsonObject();
			}
		}
		if (card == null)
		{
			throw new IllegalArgumentException("no card for " + boss);
		}
		final GearView view = CardJson.view(card);
		final File out = new File(args[2]);
		final File icons = new File(args[3]);
		if (args.length > 4 && "pt".equals(args[4]))
		{
			CaPlannerPanel.language(Lang.read(new InputStreamReader(CardShot.class.getResourceAsStream("/com/caplanner/lang/pt.tsv"),
				StandardCharsets.UTF_8)), HowTo.read(new InputStreamReader(
				CardShot.class.getResourceAsStream("/com/caplanner/lang/pt-tasks.tsv"), StandardCharsets.UTF_8)));
		}
		final int height = args.length > 5 ? Integer.parseInt(args[5]) : 1400;
		List<Task> tasks = new ArrayList<>();
		tasks.add(new Task(1, "A kill", "Kill " + boss + " once.", Tier.EASY, TaskType.KILL_COUNT, boss));
		final Planner.State state = new Planner.State(tasks, new HashSet<Integer>(), new HashMap<Integer, Verdict>(), new HashMap<Integer, Double>());
		final CaPlannerPanel[] panel = new CaPlannerPanel[1];
		final JFrame[] frame = new JFrame[1];
		SwingUtilities.invokeAndWait(() ->
		{
			panel[0] = new CaPlannerPanel((CaPlannerPanel.Actions) java.lang.reflect.Proxy.newProxyInstance(CardShot.class.getClassLoader(),
				new Class<?>[]{CaPlannerPanel.Actions.class}, (proxy, method, a) ->
				{
					switch (method.getName())
					{
						case "itemIcon":
							picture(icons, (Integer) a[0], (JLabel) a[1], 32);
							return null;
						case "wantGear":
							final String key = (String) a[0];
							final int generation = (Integer) a[1];
							SwingUtilities.invokeLater(() -> panel[0].gear(key, generation, view));
							return null;
						case "ruleKind":
							return 0;
						case "accountLine":
							return "";
						case "tileLegend":
							return new ArrayList<String[]>();
						case "createSetup":
							return null;
						default:
							Class<?> r = method.getReturnType();
							return r == boolean.class ? Boolean.FALSE : r == int.class ? 0 : null;
					}
				}));
			panel[0].render(state);
			panel[0].bankSeen(900);
			panel[0].open(false, null, boss, -1);
			frame[0] = new JFrame();
			frame[0].setUndecorated(true);
			frame[0].add(panel[0]);
			panel[0].setPreferredSize(new Dimension(242, height));
			frame[0].pack();
		});
		for (int i = 0; i < 4; i++)
		{
			SwingUtilities.invokeAndWait(() -> frame[0].validate());
		}
		SwingUtilities.invokeAndWait(() ->
		{
			BufferedImage image = new BufferedImage(panel[0].getWidth(), panel[0].getHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics2D g = image.createGraphics();
			panel[0].printAll(g);
			g.dispose();
			try
			{
				ImageIO.write(image, "png", out);
			}
			catch (Exception e)
			{
				throw new IllegalStateException(e);
			}
			frame[0].dispose();
		});
		System.out.println("wrote " + out);
		System.exit(0);
	}

	private static void picture(File icons, int id, JLabel into, int size)
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
			into.setIcon(new ImageIcon(image.getScaledInstance(size * image.getWidth() / Math.max(image.getWidth(), image.getHeight()),
				size * image.getHeight() / Math.max(image.getWidth(), image.getHeight()), Image.SCALE_SMOOTH)));
		}
		catch (Exception e)
		{
			into.setText(String.valueOf(id));
		}
	}
}
