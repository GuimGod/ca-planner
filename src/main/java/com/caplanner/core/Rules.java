/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What a task itself asks of the gear, beyond what its boss asks: a weapon it must be done
 * with, a style it allows, an item it forbids. Read from the bundled table, one task a line.
 * A task that is not in the table asks for nothing special.
 */
public final class Rules
{
	/** Words a table line may use for a family of items, and the names (in lower case) each stands for. */
	private static final Map<String, List<String>> FAMILIES = new HashMap<>();

	static
	{
		FAMILIES.put("@demonbane", Arrays.asList("arclight", "emberlight", "darklight", "silverlight", "burning claws",
			"bone claws", "scorching bow", "purging staff"));
		FAMILIES.put("@dragonbane", Arrays.asList("dragon hunter lance", "dragon hunter crossbow", "dragon hunter wand"));
		FAMILIES.put("@ratbane", Arrays.asList("bone mace", "bone staff", "bone shortbow"));
	}

	/** One task's rule. Names are parts of item names, in lower case. */
	public static final class Rule
	{
		/** The combat styles allowed ("melee", "ranged", "magic"); empty for any. */
		public final Set<String> styles = new HashSet<>();
		/** The weapon must be one of these; empty for any. */
		public final List<String> weapon = new ArrayList<>();
		/** Nothing with one of these in its name may be worn. */
		public final List<String> exclude = new ArrayList<>();
		/** The player must own one of these, though the set need not include it (a finishing blow). */
		public final List<String> have = new ArrayList<>();
		/** How to say what {@link #have} asks for, when nothing owned matches. */
		public String haveLabel;
		public boolean oneHanded;
		public boolean shield;
		/** "stab", "slash" or "crush" when the task asks for that kind of melee hit; else null. */
		public String meleeType;
		/** A word the ammunition's name must have ("mithril"); else null. */
		public String ammoWord;
		/** "air", "water", "earth" or "fire" when only spells of that element may be used; else null. */
		public String spellElement;
		/** The task has a rule the set cannot follow (a price limit, bare hands): the panel says so. */
		public boolean beyondTheSet;
		/** Supplies the task forbids, by a name of theirs in the inventory tables: they are left out of the inventory. */
		public final List<String> without = new ArrayList<>();

		/** Whether the rule changes which set is picked. */
		public boolean shapesTheSet()
		{
			return !styles.isEmpty() || !weapon.isEmpty() || !exclude.isEmpty() || oneHanded || shield
				|| meleeType != null || ammoWord != null || spellElement != null || !without.isEmpty();
		}
	}

	private final Map<Integer, Rule> byTask = new HashMap<>();

	/**
	 * Reads "task id, tab, rule" lines; "#" starts a comment. A rule is "key=value" parts joined
	 * by ";": style=melee,ranged · weapon=name|name · exclude=name|name · have=name|name ·
	 * type=stab · ammo=word · spell=earth · onehand · shield · note · without=supply|supply (left out of the
	 * inventory). A value may be a family ("@demonbane").
	 * A part that is not understood is left out; a line that is not is skipped.
	 */
	public static Rules read(Reader table) throws IOException
	{
		Rules out = new Rules();
		BufferedReader in = new BufferedReader(table);
		String line;
		while ((line = in.readLine()) != null)
		{
			if (line.trim().isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] cols = line.split("\t");
			if (cols.length < 2)
			{
				continue;
			}
			int id;
			try
			{
				id = Integer.parseInt(cols[0].trim());
			}
			catch (NumberFormatException e)
			{
				continue;
			}
			Rule rule = new Rule();
			for (String part : cols[1].split(";"))
			{
				String p = part.trim();
				int eq = p.indexOf('=');
				String key = (eq < 0 ? p : p.substring(0, eq)).trim().toLowerCase(Locale.ROOT);
				String value = eq < 0 ? "" : p.substring(eq + 1).trim().toLowerCase(Locale.ROOT);
				switch (key)
				{
					case "style":
						for (String style : value.split(","))
						{
							if (!style.trim().isEmpty())
							{
								rule.styles.add(style.trim());
							}
						}
						break;
					case "weapon":
						rule.weapon.addAll(names(value));
						break;
					case "exclude":
						rule.exclude.addAll(names(value));
						break;
					case "have":
						rule.have.addAll(names(value));
						rule.haveLabel = value.startsWith("@") ? "a " + value.substring(1) + " weapon" : value.replace("|", " or ");
						break;
					case "type":
						rule.meleeType = value;
						break;
					case "ammo":
						rule.ammoWord = value;
						break;
					case "spell":
						rule.spellElement = value;
						break;
					case "onehand":
						rule.oneHanded = true;
						break;
					case "shield":
						rule.shield = true;
						break;
					case "note":
						rule.beyondTheSet = true;
						break;
					case "without":
						rule.without.addAll(names(value));
						break;
					default:
						break;
				}
			}
			out.byTask.put(id, rule);
		}
		return out;
	}

	private static List<String> names(String value)
	{
		List<String> out = new ArrayList<>();
		for (String name : value.split("\\|"))
		{
			String n = name.trim();
			if (FAMILIES.containsKey(n))
			{
				out.addAll(FAMILIES.get(n));
			}
			else if (!n.isEmpty())
			{
				out.add(n);
			}
		}
		return out;
	}

	/** The task's rule, or null when it has none. */
	public Rule of(int taskId)
	{
		return byTask.get(taskId);
	}

	/**
	 * What the task needs that the player does not own, as a short line; null when nothing is
	 * missing or when what is owned is not known.
	 *
	 * @param ownedNames the names of everything wearable the player owns, or null when not known
	 */
	public String missing(int taskId, Collection<String> ownedNames)
	{
		Rule rule = byTask.get(taskId);
		if (rule == null || ownedNames == null)
		{
			return null;
		}
		if (!rule.have.isEmpty() && !ownsAny(rule.have, ownedNames))
		{
			return "Needs " + rule.haveLabel;
		}
		if (!rule.weapon.isEmpty() && !ownsAny(rule.weapon, ownedNames))
		{
			return "Needs " + String.join(" or ", rule.weapon.size() > 3 ? rule.weapon.subList(0, 3) : rule.weapon);
		}
		return null;
	}

	private static boolean ownsAny(List<String> wanted, Collection<String> ownedNames)
	{
		for (String owned : ownedNames)
		{
			String o = owned == null ? "" : owned.toLowerCase(Locale.ROOT);
			if (o.endsWith(" 0") || o.contains("(broken)") || o.contains("(uncharged)") || o.contains("(inactive)"))
			{
				// owned, and of no use as it is
				continue;
			}
			for (String w : wanted)
			{
				if (o.contains(w))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** Ids of every task with a rule. */
	public Set<Integer> taskIds()
	{
		return Collections.unmodifiableSet(byTask.keySet());
	}
}
