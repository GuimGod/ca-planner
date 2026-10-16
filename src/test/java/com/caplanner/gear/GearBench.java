package com.caplanner.gear;

import com.caplanner.core.Bag;
import com.caplanner.core.GearCard;
import com.caplanner.core.GearView;
import com.caplanner.core.Rules;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Development only: works out the gear card of a list of bosses from a file of owned items, outside the
 * game, with the plugin's own code ({@link GearCard}): the set, the parts of the fight, what the fight
 * makes the player wear, the inventory gone through or the provisional one, the task's rule and the
 * pieces carried for a task against the clock.
 * Usage: gradlew bench -Powned=owned.tsv -Pbosses="Vardorvis;Corporeal Beast#123" [-Pbank=bank.tsv] [-Pjson=out.json]
 * The owned file has one item a line, tab-separated: id, slot index, two-handed (0/1), name, stab,
 * slash, crush, magic, ranged, strength, ranged strength, magic damage (percent), speed. The bank file
 * has "item id, name, quantity" lines (quantity 0 for a placeholder). "Boss#task id" works the card out
 * for that task: its rule, and more pieces carried when it is against the clock.
 */
public class GearBench
{
	private static final String[] SLOT = {"head", "cape", "neck", "weapon", "body", "shield", "", "legs", "", "hands", "feet", "", "ring", "ammo"};

	public static void main(String[] args) throws Exception
	{
		List<Gear.Stat> owned = read(args[0]);
		Gear.Levels levels = new Gear.Levels();
		levels.attack = levels.strength = levels.ranged = levels.magic = levels.prayer = levels.hitpoints = 99;
		levels.piety = levels.rigour = levels.augury = true;
		levels.ancients = true;
		Gear gear = new Gear();
		System.out.println(owned.size() + " wearable items");
		Map<String, Bag.Have> bank = new HashMap<>();
		if (args.length > 2 && !args[2].isEmpty())
		{
			try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(args[2]), StandardCharsets.UTF_8)))
			{
				String line;
				while ((line = in.readLine()) != null)
				{
					String[] c = line.split("\t");
					if (c.length >= 3 && c[0].matches("\\d+") && c[2].matches("\\d+"))
					{
						Bag.Have before = bank.get(c[1].toLowerCase());
						int q = Integer.parseInt(c[2]) + (before == null ? 0 : before.quantity);
						bank.put(c[1].toLowerCase(), new Bag.Have(Integer.parseInt(c[0]), q, c[1]));
					}
				}
			}
			levels.runes = new HashSet<>();
			for (Map.Entry<String, Bag.Have> e : bank.entrySet())
			{
				if (e.getKey().endsWith(" rune") && e.getValue().quantity > 0)
				{
					levels.runes.add(e.getKey());
				}
			}
		}
		// what is worn counts as owned for the inventory, as in the game
		for (Gear.Stat s : owned)
		{
			if (s.name != null && !bank.containsKey(s.name.toLowerCase()))
			{
				bank.put(s.name.toLowerCase(), new Bag.Have(s.itemId, 1, s.name));
			}
		}
		Bag bag;
		try (InputStream in = GearBench.class.getResourceAsStream("/com/caplanner/bag.tsv"))
		{
			bag = Bag.read(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		try (InputStream in = GearBench.class.getResourceAsStream("/com/caplanner/bag-wiki.tsv"))
		{
			bag.readWiki(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		Rules rules;
		try (InputStream in = GearBench.class.getResourceAsStream("/com/caplanner/rules.tsv"))
		{
			rules = Rules.read(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		Fights fights;
		try (InputStream in = Gear.class.getResourceAsStream("fights.tsv"))
		{
			fights = Fights.read(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		Set<Integer> speed = new HashSet<>();
		try (InputStream in = GearBench.class.getResourceAsStream("/com/caplanner/speed.tsv"))
		{
			BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			while ((line = r.readLine()) != null)
			{
				String id = line.split("\t")[0].trim();
				if (id.matches("\\d+"))
				{
					speed.add(Integer.parseInt(id));
				}
			}
		}
		Set<String> done = new HashSet<>();
		done.add("desert-hard");
		done.add("kourend-elite");
		done.add("lumbridge-elite");
		// every piece there is, for the damage of a set given by its item ids (one that is not owned)
		Map<Integer, Gear.Stat> every = new HashMap<>();
		if (args.length > 4 && !args[4].isEmpty())
		{
			for (Gear.Stat s : read(args[4]))
			{
				every.put(s.itemId, s);
			}
		}
		for (Gear.Stat s : owned)
		{
			every.put(s.itemId, s);
		}
		// the player's changes, "boss, place, slot, item id, quantity" lines: the cards are shown as changed
		com.caplanner.core.GearChoices changes = com.caplanner.core.GearChoices.none();
		if (args.length > 5 && !args[5].isEmpty())
		{
			try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(args[5]), StandardCharsets.UTF_8)))
			{
				String line;
				while ((line = in.readLine()) != null)
				{
					String[] c = line.split("\t");
					if (c.length >= 5 && c[2].matches("\\d+") && c[3].matches("\\d+"))
					{
						changes = changes.with(c[0], c[1], Integer.parseInt(c[2]), Integer.parseInt(c[3]), Integer.parseInt(c[4].trim()),
							c.length > 5 ? Integer.parseInt(c[5].trim()) : com.caplanner.core.GearChoices.UNKNOWN);
					}
				}
			}
		}
		Map<Integer, GearCard.Item> items = new HashMap<>();
		for (Gear.Stat s : every.values())
		{
			items.put(s.itemId, new GearCard.Item(s.itemId, s.name, false, s));
		}
		for (Bag.Have h : bank.values())
		{
			if (!items.containsKey(h.itemId))
			{
				String n = h.name.toLowerCase();
				// what stacks, as far as a bench without the game can tell
				boolean stacks = n.endsWith(" rune") || n.contains("bolts") || n.contains("dart") || n.contains("arrow") || n.contains("teleport");
				items.put(h.itemId, new GearCard.Item(h.itemId, h.name, stacks, null));
			}
		}
		List<Map<String, Object>> cards = new ArrayList<>();
		for (String entry : args[1].split(";"))
		{
			if (entry.trim().isEmpty())
			{
				continue;
			}
			// "Boss#task@id,id,...": the damage of that set too, worn as it is
			String given = entry.contains("@") ? entry.substring(entry.indexOf('@') + 1) : "";
			String head = entry.contains("@") ? entry.substring(0, entry.indexOf('@')) : entry;
			// "Boss!": as if on a Slayer task of it
			boolean onTask = head.trim().endsWith("!");
			head = onTask ? head.trim().substring(0, head.trim().length() - 1) : head;
			String name = head.contains("#") ? head.substring(0, head.indexOf('#')).trim() : head.trim();
			int taskId = head.contains("#") ? Integer.parseInt(head.substring(head.indexOf('#') + 1).trim()) : -1;
			GearCard.Input input = new GearCard.Input();
			input.gear = gear;
			input.fights = fights;
			input.bag = bag;
			input.rules = rules;
			input.boss = name;
			input.taskId = taskId;
			input.taskLimits = GearCard.limitsOf(rules, taskId);
			input.owned = owned;
			input.levels = levels;
			input.onTask = onTask;
			input.have = bank;
			input.done = done;
			input.againstTheClock = speed.contains(taskId);
			input.choices = changes.of(name);
			input.items = items;
			long start = System.nanoTime();
			GearView v = GearCard.knows(gear, fights, name) ? GearCard.build(input) : GearView.of(GearView.Status.NO_BOSS);
			long ms = (System.nanoTime() - start) / 1_000_000;
			Map<String, Object> card = json(head, v, ms);
			if (!given.isEmpty())
			{
				int npc = fights.has(name) ? fights.mainNpc(name) : 0;
				npc = npc == 0 ? gear.npcOf(name) : npc;
				Map<Integer, Gear.Stat> worn = new HashMap<>();
				List<Integer> unknown = new ArrayList<>();
				for (String id : given.split(","))
				{
					if (!id.trim().matches("[0-9]+"))
					{
						continue;
					}
					Gear.Stat s = every.get(Integer.parseInt(id.trim()));
					if (s == null)
					{
						unknown.add(Integer.parseInt(id.trim()));
					}
					else
					{
						worn.put(s.slot, s);
					}
				}
				card.put("npc", npc);
				double givenDps = npc == 0 ? 0 : gear.dpsAt(npc, worn, levels, onTask);
				if (npc != 0 && givenDps == 0)
				{
					// a weapon that shoots, given with a blessing (the bolts or arrows are in the quiver): the best ammunition owned
					Gear.Stat kept = worn.get(13);
					for (Gear.Stat ammo : owned)
					{
						if (ammo.slot == 13)
						{
							worn.put(13, ammo);
							double d = gear.dpsAt(npc, worn, levels, onTask);
							if (d > givenDps)
							{
								givenDps = d;
								card.put("givenAmmo", ammo.name);
							}
						}
					}
					if (!card.containsKey("givenAmmo"))
					{
						worn.put(13, kept);
					}
				}
				card.put("givenDps", givenDps);
				card.put("givenUnknown", unknown);
				Map<Integer, Gear.Stat> cardSet = new HashMap<>();
				for (Map.Entry<Integer, Integer> e : v.itemIdBySlot.entrySet())
				{
					Gear.Stat s = every.get(e.getValue());
					if (s != null)
					{
						cardSet.put(e.getKey(), s);
					}
				}
				card.put("cardDpsAtNpc", npc == 0 ? 0 : gear.dpsAt(npc, cardSet, levels, onTask));
			}
			cards.add(card);
			print(head, v, ms);
		}
		if (args.length > 3 && !args[3].isEmpty())
		{
			try (Writer out = new OutputStreamWriter(new java.io.FileOutputStream(args[3]), StandardCharsets.UTF_8))
			{
				new GsonBuilder().setPrettyPrinting().create().toJson(cards, out);
			}
		}
		System.exit(0);
	}

	/** Items of a file of "id, slot, two-handed, name, stab, slash, crush, magic, ranged, strength, ranged strength, magic damage, speed" lines. */
	private static List<Gear.Stat> read(String file) throws java.io.IOException
	{
		List<Gear.Stat> owned = new ArrayList<>();
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
				owned.add(s);
			}
		}
		return owned;
	}

	private static Map<String, Object> line(Bag.Line l)
	{
		Map<String, Object> o = new LinkedHashMap<>();
		o.put("id", l.itemId);
		o.put("name", l.name);
		o.put("quantity", l.quantity);
		o.put("slots", l.slots());
		o.put("owned", l.owned);
		o.put("needed", l.needed);
		o.put("why", l.why);
		return o;
	}

	private static List<Map<String, Object>> lines(List<Bag.Line> ls)
	{
		List<Map<String, Object>> out = new ArrayList<>();
		for (Bag.Line l : ls)
		{
			out.add(l == null ? null : line(l));
		}
		return out;
	}

	private static Map<String, Object> json(String entry, GearView v, long ms)
	{
		Map<String, Object> o = new LinkedHashMap<>();
		o.put("entry", entry);
		o.put("status", v.status.name());
		o.put("ms", ms);
		Map<String, Object> set = new LinkedHashMap<>();
		for (Map.Entry<Integer, Integer> e : v.itemIdBySlot.entrySet())
		{
			Map<String, Object> piece = new LinkedHashMap<>();
			piece.put("id", e.getValue());
			piece.put("name", v.nameBySlot.get(e.getKey()));
			set.put(SLOT[e.getKey()], piece);
		}
		o.put("set", set);
		o.put("style", v.style);
		o.put("dps", v.dps);
		o.put("hp", v.bossHitpoints);
		o.put("wornFor", v.wornFor);
		List<Map<String, Object>> parts = new ArrayList<>();
		for (GearView.Part p : v.parts)
		{
			Map<String, Object> part = new LinkedHashMap<>();
			part.put("label", p.label);
			part.put("style", p.style);
			part.put("dps", p.dps);
			part.put("spell", p.spell);
			part.put("note", p.note);
			part.put("nothing", p.nothing);
			part.put("best", p.best);
			part.put("sharedWeapon", p.sharedWeapon);
			part.put("sharedWith", p.sharedWith);
			part.put("carried", lines(p.carried));
			parts.add(part);
		}
		o.put("parts", parts);
		o.put("worn", lines(v.worn));
		o.put("notes", v.notes);
		o.put("weapons", v.weapons);
		o.put("bag", lines(v.bag));
		List<Map<String, Object>> slots = new ArrayList<>();
		for (int[] slot : v.inventory())
		{
			Map<String, Object> s = new LinkedHashMap<>();
			s.put("id", slot[0]);
			s.put("quantity", slot[1]);
			slots.add(s);
		}
		o.put("inventory", slots);
		int wanted = v.carriedSlots().size();
		for (Bag.Line l : v.bag)
		{
			wanted += l.slots();
		}
		// before the cut at 28: what would not fit
		o.put("slotsWanted", wanted);
		o.put("inventoryToCome", v.inventoryToCome);
		if (v.provisional != null)
		{
			o.put("provisionalLacking", v.provisional.lacking);
			o.put("provisionalSwitches", v.provisional.switches);
		}
		return o;
	}

	private static void print(String entry, GearView v, long ms)
	{
		if (v.status != GearView.Status.READY)
		{
			System.out.println(entry + ": " + v.status + (v.weapons.isEmpty() ? "" : " " + v.weapons) + " (" + ms + " ms)");
			return;
		}
		StringBuilder set = new StringBuilder();
		for (Map.Entry<Integer, String> e : v.nameBySlot.entrySet())
		{
			set.append(set.length() == 0 ? "" : " | ").append(SLOT[e.getKey()]).append(": ").append(e.getValue());
		}
		System.out.println(String.format("%s: %s %.2f dps, hp %d (%d ms)%n   %s", entry, v.style, v.dps, v.bossHitpoints, ms, set));
		if (!v.wornFor.isEmpty())
		{
			System.out.println("   worn for: " + v.wornFor);
		}
		for (GearView.Part p : v.parts)
		{
			StringBuilder carried = new StringBuilder();
			for (Bag.Line c : p.carried)
			{
				carried.append(carried.length() == 0 ? "" : ", ").append(c.name);
			}
			System.out.println(String.format("   + %s: %s %.2f dps%s -> %s", p.label, p.nothing ? "nothing owned" : p.style, p.dps,
				p.spell == null ? "" : " (casting " + p.spell + ")",
				carried.length() > 0 ? carried : p.sharedWeapon != null ? "(" + p.sharedWeapon + ", carried for " + p.sharedWith + ")" : "(the set worn does it)"));
		}
		for (Bag.Line w : v.worn)
		{
			System.out.println("   wear: " + w.name + (w.owned ? "" : " (NOT OWNED)"));
		}
		StringBuilder inv = new StringBuilder();
		int slots = 0;
		for (Bag.Line l : v.bag)
		{
			inv.append(inv.length() == 0 ? "" : " | ").append(l.quantity).append(" ").append(l.name).append(l.owned ? "" : " (NOT OWNED)");
			slots += l.slots();
		}
		System.out.println("   " + (v.provisional != null ? "provisional " : v.inventoryToCome ? "inventory to come " : "")
			+ "inventory (" + v.inventory().size() + " slots, " + slots + " of supplies): " + inv);
		if (v.provisional != null)
		{
			System.out.println("   lacking: " + v.provisional.lacking + " · switches: " + v.provisional.switches);
		}
		for (String n : v.notes)
		{
			System.out.println("   note: " + n);
		}
		if (v.edited())
		{
			StringBuilder cells = new StringBuilder();
			for (int[] slot : v.inventory())
			{
				cells.append(cells.length() == 0 ? "" : " ").append(slot[0]).append(slot[1] > 1 ? "x" + slot[1] : "");
			}
			System.out.println("   inventory slot by slot: " + cells);
			System.out.println(String.format("   EDITED: %.2f dps (recommended %.2f)%s", v.dps, v.recommended.dps,
				v.notOwned.isEmpty() ? "" : " · not owned in slots " + v.notOwned));
			for (com.caplanner.core.GearView.Change c : v.changes)
			{
				if (!c.line.isEmpty())
				{
					System.out.println("   change: " + c.kind + " · " + c.line);
				}
			}
		}
	}
}
