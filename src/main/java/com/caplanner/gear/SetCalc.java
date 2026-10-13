/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Expected damage per game tick of a whole set of gear (weapon and armour) against a monster,
 * for melee, ranged and the staves that have their own attack. It exists to compare two sets
 * the player is carrying: the one worn and the one in the inventory.
 *
 * <p>Only the standard accuracy and max hit formulas are here (OSRS Wiki), with the handful of
 * weapon rules listed below, the Slayer helmet on a task and the Salve amulet against the undead
 * (the two do not stack: the amulet wins), and the weapons whose strength depends on what the
 * monster is: demonbane, dragon hunter, keris, leaf-bladed, the revenant weapons in the Wilderness,
 * the crystal bows with crystal armour, the Colossal blade, the Tonalztics and the Void sets.
 * Anything this class does not know is left out: such a weapon is reported by
 * {@link #plain(Loadout)} so that no figure is shown.
 *
 * <p>Ruby and diamond bolts (e) are counted with the monster's current health; the other enchanted bolts
 * are not. Also not counted: the extra accuracy and strength Dizana's quiver gives the arrows it holds
 * (about 6% on a Twisted bow), and anything a monster does during the fight.
 */
final class SetCalc
{
	/** The bonuses of everything worn, and the weapon. */
	static final class Loadout
	{
		int stab;
		int slash;
		int crush;
		int magic;
		int ranged;
		int strength;
		int rangedStrength;
		/** Magic damage, in percent. */
		double magicDamage;
		int inquisitor;
		/** Virtus pieces worn: each adds to what a spell of the Ancient Magicks deals. */
		int virtus;
		String weaponName = "";
		/** The weapon's category in the bundled table ("Bow", "Powered Staff", "Scythe"...), or null. */
		String category;
		/** Ticks between attacks, as on the item. */
		int speed = 4;
		List<StyleData.Style> styles = Collections.emptyList();
		/** Enchanted bolts other than ruby and diamond: their effect is not counted, so a crossbow with them gets no figure. */
		boolean boltEffect;
		/** Ruby or diamond bolts (e) in the ammunition slot: {@link #RUBY}, {@link #DIAMOND}, or 0. */
		int bolt;
		/** Slayer helmet or Black mask worn: {@link SetEffects#MASK}, {@link SetEffects#MASK_IMBUED}, or 0. */
		int mask;
		/** Salve amulet worn: one of the {@code SetEffects.SALVE} kinds, or 0. */
		int salve;
		/** Crystal armour worn, weighted as the game does (helm 1, legs 2, body 3): counts with a crystal bow. */
		int crystal;
		/** The whole Void set worn, for melee and for ranged: 0 none, 1 Void, 2 Elite Void. */
		int voidMelee;
		int voidRanged;
		/** The spell on autocast, by its number in the game's autocast setting; 0 for none or not known. */
		int spell;
		/** The element of the charged tome in the shield slot ("fire", "water", "earth"), or null. */
		String tome;
		/** Dharok's whole set is worn: the max hit grows with the hitpoints the player has lost. */
		boolean dharokSet;
		/** A blowpipe whose darts are known: their strength is already in {@link #rangedStrength}. */
		boolean dartKnown;
	}

	/** Boosted levels and prayer multipliers (100 for none). */
	static final class Player
	{
		int attack;
		int strength;
		int ranged;
		int magic;
		int prayerAttack = 100;
		int prayerStrength = 100;
		int prayerRanged = 100;
		int prayerRangedStrength = 100;
		int prayerMagic = 100;
		/** Magic damage a prayer adds, in percent (4 for Augury). */
		double prayerMagicDamage;
		/** The Kandarin hard diary: enchanted bolt effects go off a tenth more often. */
		boolean kandarinDiary;
		/** The player's own hitpoints: the ruby effect does nothing under 10. */
		int hitpoints = 99;
		/** The player's Hitpoints level, for Dharok's set. */
		int hitpointsMax = 99;
		/** The Prayer level and the prayer scrolls read, to know the best attack prayer of each class. */
		int prayerLevel = 99;
		boolean piety = true;
		boolean chivalry = true;
		boolean rigour = true;
		boolean deadeye = true;
		boolean augury = true;
		boolean vigour = true;
	}

	static final class Target
	{
		String name = "";
		int defenceLevel;
		int stab;
		int slash;
		int crush;
		int magicLevel;
		int magic;
		int light;
		int standard;
		int heavy;
		int size = 1;
		/** The higher of the monster's Magic level and magic attack bonus: what the Twisted bow scales with. */
		int tbowMagic;
		/** Taken off every melee and ranged hit that lands (negative adds to it). */
		int flatArmour;
		/**
		 * What cannot hurt it: 'M' no melee at all, 'f' melee only with a polearm (it flies or stays out of
		 * reach), 'r' no ranged, 'g' no magic.
		 */
		String immune = "";
		/** Zulrah: a hit above this is rolled again between 45 and 50. 0 for every other monster. */
		int cap;
		/** The Salve amulet works on it. */
		boolean undead;
		/** The player is on a Slayer task of this monster: the Slayer helmet works on it. */
		boolean onTask;
		boolean demon;
		boolean dragon;
		boolean kalphite;
		boolean leafy;
		/** How much of a demonbane weapon's bonus a demon takes, in percent. */
		int demonbane = 100;
		/** The fight is in the Wilderness, where the revenant weapons are stronger. */
		boolean inWilderness;
		/** Its hitpoints, and how many it has left now (0 when not known: then it is taken at full health). */
		int hitpoints;
		int hitpointsNow;
		/** The element it is weak to and by how much, in percent; null and 0 for none. */
		String weakness;
		int weaknessSeverity;
	}

	static final int RUBY = 1;
	static final int DIAMOND = 2;

	/** {@link #RUBY} or {@link #DIAMOND} for enchanted bolts of either tier, 0 for anything else. */
	static int boltKind(String itemName)
	{
		if (itemName == null || !itemName.endsWith("(e)"))
		{
			return 0;
		}
		if (itemName.startsWith("Ruby bolts") || itemName.startsWith("Ruby dragon bolts"))
		{
			return RUBY;
		}
		return itemName.startsWith("Diamond bolts") || itemName.startsWith("Diamond dragon bolts") ? DIAMOND : 0;
	}

	/**
	 * Expected damage of one crossbow shot with ruby or diamond bolts (e): the effect on its share of the
	 * shots, the ordinary hit on the rest (OSRS Wiki). Ruby: 6% of the shots deal 20% of the monster's
	 * current health, at most 100 (22% and 110 with a Zaryte crossbow). Diamond: 10% of the shots cannot
	 * miss and roll up to 115% of the max hit (126%). The Kandarin hard diary adds a tenth to either chance.
	 */
	static double withBolt(int bolt, boolean zaryte, Player p, Target t, long max, double normal)
	{
		double diary = p.kandarinDiary ? 1.1 : 1.0;
		if (bolt == DIAMOND)
		{
			double chance = 0.10 * diary;
			return chance * (max * (zaryte ? 126 : 115) / 100) / 2.0 + (1 - chance) * normal;
		}
		int health = t.hitpointsNow > 0 ? t.hitpointsNow : t.hitpoints;
		if (bolt != RUBY || health <= 0 || p.hitpoints < 10)
		{
			return normal;
		}
		double chance = 0.06 * diary;
		// the Gemstone Crab's health never runs out: the effect stops lower on it
		boolean crab = "Gemstone Crab".equalsIgnoreCase(t.name);
		long cap = crab ? (zaryte ? 66 : 60) : (zaryte ? 110 : 100);
		return chance * Math.min(cap, (long) health * (zaryte ? 22 : 20) / 100) + (1 - chance) * normal;
	}

	/** Extra accuracy and damage of a demonbane weapon on this monster, as a multiplier; 1 when it is not a demon. */
	static double demonbane(Target t, int percent)
	{
		return t.demon ? 1 + percent * t.demonbane / 10000.0 : 1;
	}

	/**
	 * What the Slayer helmet or the Salve amulet does for a combat class, as numerator, denominator and the
	 * percentage the amulet adds to magic (the helmet multiplies magic; the amulet adds to it). The OSRS Wiki
	 * calculator's rules: the amulet first, against the undead; otherwise the helmet, on a task.
	 */
	static int[] headOrNeck(Loadout l, Target t, String combatClass)
	{
		boolean melee = StyleCalc.MELEE.equals(combatClass);
		boolean magic = StyleCalc.MAGIC.equals(combatClass);
		if (t.undead && l.salve != 0)
		{
			boolean enchanted = l.salve == SetEffects.SALVE_E || l.salve == SetEffects.SALVE_EI;
			boolean imbued = l.salve == SetEffects.SALVE_I || l.salve == SetEffects.SALVE_EI;
			if (melee || imbued)
			{
				if (magic)
				{
					return new int[]{1, 1, enchanted ? 20 : 15};
				}
				return enchanted ? new int[]{6, 5, 0} : new int[]{7, 6, 0};
			}
			// an amulet that is not imbued does nothing for ranged or magic, and the helmet does not replace it
			// in the calculator only when the amulet's own rule applied: here it did not, so the helmet may
		}
		if (t.onTask && l.mask != 0)
		{
			if (melee)
			{
				return new int[]{7, 6, 0};
			}
			if (l.mask == SetEffects.MASK_IMBUED)
			{
				return new int[]{23, 20, 0};
			}
		}
		return new int[]{1, 1, 0};
	}

	/** Weapons with a rule of their own that is not modelled here: no figure is shown for them. */
	private static final String[] NOT_PLAIN = {
		"purging staff", "thammaron", "accursed", "soulreaper", "verac",
		"dual macuahuitl", "eclipse atlatl", "hunter's spear", "salamander", "swamp lizard", "chinchompa",
		"barronite", "blisterwood", "ivandis", "wilderness", "gadderhammer",
		"holy water", "ballista", "bone staff", "dawnbringer", "starter",
	};

	private SetCalc()
	{
	}

	/**
	 * True for enchanted bolts whose effect adds no damage of its own (sapphire and topaz drain a stat, jade knocks
	 * down, emerald poisons): a crossbow with them is counted as with plain bolts.
	 */
	static boolean harmlessBolts(String name)
	{
		String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
		return n.endsWith("(e)") && (n.startsWith("sapphire") || n.startsWith("jade") || n.startsWith("topaz") || n.startsWith("emerald"));
	}

	/**
	 * The player as they would be with a weapon of another combat class in hand. While an attack prayer is on,
	 * the other weapon is counted with the best attack prayer the player has for its own class: with Rigour on
	 * and a bow in hand, a sword in the inventory is compared with Piety, not with no prayer at all. With no
	 * attack prayer on, nothing is added to either.
	 */
	static Player withPrayerOf(Player p, String combatClass)
	{
		boolean anyOn = p.prayerAttack > 100 || p.prayerStrength > 100 || p.prayerRanged > 100 || p.prayerMagic > 100;
		boolean ownOn = StyleCalc.MELEE.equals(combatClass) ? p.prayerAttack > 100 || p.prayerStrength > 100
			: StyleCalc.RANGED.equals(combatClass) ? p.prayerRanged > 100 : p.prayerMagic > 100;
		if (!anyOn || ownOn)
		{
			return p;
		}
		Player q = new Player();
		q.attack = p.attack;
		q.strength = p.strength;
		q.ranged = p.ranged;
		q.magic = p.magic;
		q.prayerAttack = p.prayerAttack;
		q.prayerStrength = p.prayerStrength;
		q.prayerRanged = p.prayerRanged;
		q.prayerRangedStrength = p.prayerRangedStrength;
		q.prayerMagic = p.prayerMagic;
		q.prayerMagicDamage = p.prayerMagicDamage;
		q.kandarinDiary = p.kandarinDiary;
		q.hitpoints = p.hitpoints;
		q.hitpointsMax = p.hitpointsMax;
		q.prayerLevel = p.prayerLevel;
		q.piety = p.piety;
		q.chivalry = p.chivalry;
		q.rigour = p.rigour;
		q.deadeye = p.deadeye;
		q.augury = p.augury;
		q.vigour = p.vigour;
		int level = p.prayerLevel;
		if (StyleCalc.MELEE.equals(combatClass))
		{
			// Piety, Chivalry, or the two best single prayers of the level
			q.prayerAttack = p.piety ? 120 : p.chivalry ? 115 : level >= 34 ? 115 : level >= 16 ? 110 : level >= 7 ? 105 : 100;
			q.prayerStrength = p.piety ? 123 : p.chivalry ? 118 : level >= 31 ? 115 : level >= 13 ? 110 : level >= 4 ? 105 : 100;
		}
		else if (StyleCalc.RANGED.equals(combatClass))
		{
			boolean top = level >= 74 && p.rigour;
			int other = level >= 62 && p.deadeye ? 118 : level >= 44 ? 115 : level >= 26 ? 110 : level >= 8 ? 105 : 100;
			q.prayerRanged = top ? 120 : other;
			q.prayerRangedStrength = top ? 123 : other;
		}
		else
		{
			boolean top = level >= 77 && p.augury;
			boolean second = level >= 63 && p.vigour;
			q.prayerMagic = top ? 125 : second ? 118 : level >= 45 ? 115 : level >= 27 ? 110 : level >= 9 ? 105 : 100;
			q.prayerMagicDamage = top ? 4 : second ? 3 : level >= 45 ? 2 : level >= 27 ? 1 : 0;
		}
		return q;
	}

	static String combatClass(Loadout l)
	{
		String c = l.category == null ? "" : l.category;
		if (c.equals("Bow") || c.equals("Crossbow") || c.equals("Thrown") || c.equals("Chinchompas"))
		{
			return StyleCalc.RANGED;
		}
		if (c.equals("Bladed Staff"))
		{
			// a staff of the dead: with no spell set, it is the blade that is counted
			return l.spell > 0 ? StyleCalc.MAGIC : StyleCalc.MELEE;
		}
		if (c.endsWith("Staff"))
		{
			return StyleCalc.MAGIC;
		}
		return c.equals("Salamander") ? null : StyleCalc.MELEE;
	}

	/** True when this class knows everything that matters about the weapon. */
	static boolean plain(Loadout l)
	{
		String n = l.weaponName == null ? "" : l.weaponName.toLowerCase(Locale.ROOT);
		for (String s : NOT_PLAIN)
		{
			if (n.contains(s))
			{
				return false;
			}
		}
		if (n.contains("blowpipe") && !l.dartKnown)
		{
			// the darts inside are most of its damage, and the game only tells which they are when it is checked
			return false;
		}
		String cls = combatClass(l);
		if (cls == null)
		{
			return false;
		}
		if (StyleCalc.MAGIC.equals(cls))
		{
			// a staff that casts spells has a figure only with an elemental spell on autocast
			return staffBase(n, 99) > 0 || spellBase(l.spell, 99) > 0;
		}
		return !(StyleCalc.RANGED.equals(cls) && l.boltEffect);
	}

	/**
	 * Expected damage per tick, or -1 when it cannot be worked out.
	 *
	 * @param lenient also give a figure for a staff on autocast, with a nominal spell: good enough to compare the
	 *                same weapon with and without a few pieces of armour, never to compare two weapons
	 */
	static double perTick(Loadout l, Player p, Target t, boolean lenient)
	{
		String cls = combatClass(l);
		if (cls == null || l.speed <= 0)
		{
			return -1;
		}
		String immune = t.immune == null ? "" : t.immune;
		if (StyleCalc.RANGED.equals(cls))
		{
			return immune.contains("r") ? 0 : throughFireShield(ranged(l, p, t), l, t);
		}
		if (StyleCalc.MAGIC.equals(cls))
		{
			return immune.contains("g") ? 0 : throughFireShield(magic(l, p, t, lenient), l, t);
		}
		if (immune.contains("M") || (immune.contains("f") && !"Polearm".equals(l.category)))
		{
			return 0;
		}
		return throughFireShield(melee(l, p, t), l, t);
	}

	/**
	 * A Tormented Demon's fire shield takes a fifth off what any weapon deals that is neither a
	 * demonbane nor an abyssal one (OSRS Wiki). The shield is up for all but the hit after it drops,
	 * so the whole figure is counted with it.
	 */
	private static double throughFireShield(double figure, Loadout l, Target t)
	{
		if (figure <= 0 || !"Tormented Demon".equals(t.name))
		{
			return figure;
		}
		String n = l.weaponName == null ? "" : l.weaponName.toLowerCase(Locale.ROOT);
		for (String spared : new String[]{"arclight", "emberlight", "darklight", "silverlight", "burning claws", "bone claws",
			"scorching bow", "purging staff", "abyssal"})
		{
			if (n.contains(spared))
			{
				return figure;
			}
		}
		return figure * 4 / 5;
	}

	/** Mean damage of a hit rolled evenly from min to max that landed, after the monster's flat armour and cap. */
	static double landed(long min, long max, Target t)
	{
		if (t.flatArmour == 0 && t.cap == 0)
		{
			return StyleCalc.averageBetween(min, max);
		}
		if (max < min)
		{
			return 0;
		}
		double sum = 0;
		for (long h = min; h <= max; h++)
		{
			double d = Math.max(1, Math.max(1, h) - t.flatArmour);
			sum += t.cap > 0 && d > t.cap ? (t.cap - 5 + t.cap) / 2.0 : d;
		}
		return sum / (max - min + 1);
	}

	/** How much more {@code b} deals than {@code a}, in percent; NaN when either is unknown. */
	static double gain(double a, double b)
	{
		return a <= 0 || b < 0 ? Double.NaN : (b / a - 1) * 100;
	}

	private static double melee(Loadout l, Player p, Target t)
	{
		StyleCalc.Input in = new StyleCalc.Input();
		in.attackLevel = p.attack;
		in.strengthLevel = p.strength;
		in.prayerAttack = p.prayerAttack;
		in.prayerStrength = p.prayerStrength;
		in.stab = l.stab;
		in.slash = l.slash;
		in.crush = l.crush;
		in.strength = l.strength;
		in.inquisitor = l.inquisitor;
		in.fang = Weapons.isFang(l.weaponName);
		in.corpbane = Weapons.isCorpbane(l.weaponName);
		in.defenceLevel = t.defenceLevel;
		in.defenceStab = t.stab;
		in.defenceSlash = t.slash;
		in.defenceCrush = t.crush;
		in.corporealBeast = "Corporeal Beast".equals(t.name);
		in.alwaysHit = Weapons.alwaysHit(t.name);
		int[] bonus = headOrNeck(l, t, StyleCalc.MELEE);
		in.bonusNumerator = bonus[0];
		in.bonusDenominator = bonus[1];
		String n = l.weaponName == null ? "" : l.weaponName.toLowerCase(Locale.ROOT);
		// weapons with a rule of their own (OSRS Wiki): what they add depends on what the monster is
		double keris = 1;
		if (n.contains("emberlight") || n.contains("arclight"))
		{
			in.weaponAccuracy = in.weaponDamage = demonbane(t, 70);
		}
		else if (n.contains("silverlight") || n.contains("darklight"))
		{
			in.weaponAccuracy = in.weaponDamage = demonbane(t, 60);
		}
		else if (n.contains("burning claws") || n.contains("bone claws"))
		{
			in.weaponAccuracy = in.weaponDamage = demonbane(t, 5);
		}
		else if (n.contains("dragon hunter lance") && t.dragon)
		{
			in.weaponAccuracy = in.weaponDamage = 1.2;
		}
		else if (n.contains("keris") && t.kalphite)
		{
			in.weaponDamage = n.contains("amascut") ? 1.15 : 1.33;
			in.weaponAccuracy = n.contains("breaching") ? 1.33 : 1;
			keris = 53 / 51.0; // one hit in 51 deals three times as much
		}
		else if (n.contains("leaf-bladed battleaxe") && t.leafy)
		{
			in.weaponDamage = 47 / 40.0;
		}
		else if (n.contains("colossal blade"))
		{
			in.maxAdd = Math.min(10, 2 * t.size);
		}
		else if ((n.contains("viggora") || n.contains("ursine")) && t.inWilderness)
		{
			in.weaponAccuracy = in.weaponDamage = 1.5;
		}
		if (l.voidMelee > 0)
		{
			// the set raises the effective Attack and Strength levels by a tenth
			in.weaponAccuracy *= 1.1;
			in.weaponDamage *= 1.1;
		}
		// the Scythe of vitur hits a large monster up to three times: full, half and a quarter, each rolled on its own
		int hits = !n.contains("scythe of vitur") ? 1 : t.size >= 3 ? 3 : t.size == 2 ? 2 : 1;
		double best = -1;
		for (StyleData.Style s : l.styles)
		{
			if (!StyleCalc.melee(s.type))
			{
				continue;
			}
			double[] cm = StyleCalc.chanceAndMax(in, s.type, s.stance);
			long max = (long) cm[1];
			if (l.dharokSet)
			{
				// Dharok's set: 1% more for each hitpoint lost, scaled by the Hitpoints level over 100
				max = max * (10000 + (long) Math.max(0, p.hitpointsMax - p.hitpoints) * p.hitpointsMax) / 10000;
			}
			boolean fangStab = in.fang && StyleCalc.STAB.equals(s.type);
			double average = fangStab ? landed(max * 3 / 20, max - max * 3 / 20, t) : landed(0, max, t);
			for (int h = 1; h < hits; h++)
			{
				average += landed(0, max >> h, t);
			}
			if (in.corporealBeast && !(in.corpbane && StyleCalc.STAB.equals(s.type)))
			{
				average /= 2;
			}
			best = Math.max(best, cm[0] * average * keris);
		}
		return best < 0 ? -1 : best / l.speed;
	}

	private static double ranged(Loadout l, Player p, Target t)
	{
		String c = l.category;
		int defence = "Bow".equals(c) ? t.standard : "Thrown".equals(c) ? t.light : t.heavy;
		long defenceRoll = (long) (t.defenceLevel + 9) * (defence + 64);
		boolean tbow = l.weaponName != null && l.weaponName.toLowerCase(Locale.ROOT).contains("twisted bow");
		int tbowMagic = Math.min(250, t.tbowMagic);
		int[] bonus = headOrNeck(l, t, StyleCalc.RANGED);
		String n = l.weaponName == null ? "" : l.weaponName.toLowerCase(Locale.ROOT);
		boolean crystalBow = n.contains("crystal bow") || n.contains("bow of faerdhinen");
		boolean hunter = n.contains("dragon hunter crossbow") && t.dragon;
		boolean scorching = n.contains("scorching bow") && t.demon;
		boolean revenant = (n.contains("craw's bow") || n.contains("webweaver")) && t.inWilderness;
		boolean tonalztics = n.contains("tonalztics");
		// with an imbued Slayer helmet on a task these weapons add to its bonus instead of multiplying after it
		boolean withHelmet = bonus[0] == 23;
		int helmet = withHelmet ? 23 + (hunter ? 5 : 0) + (scorching ? 6 : 0) + (revenant ? 10 : 0) : 0;
		double best = -1;
		for (int accurate = 0; accurate <= 1; accurate++)
		{
			long effective = (long) p.ranged * p.prayerRanged / 100 + 8 + (accurate == 1 ? 3 : 0);
			long effectiveStrength = (long) p.ranged * p.prayerRangedStrength / 100 + 8 + (accurate == 1 ? 3 : 0);
			long roll = effective * (l.ranged + 64);
			long max = (effectiveStrength * (l.rangedStrength + 64) + 320) / 640;
			if (l.voidRanged > 0)
			{
				roll = roll * 11 / 10;
				max = l.voidRanged == 2 ? max * 9 / 8 : max * 11 / 10;
			}
			if (crystalBow)
			{
				roll = roll * (20 + l.crystal) / 20;
				max = max * (40 + l.crystal) / 40;
			}
			roll = roll * bonus[0] / bonus[1];
			max = withHelmet ? max * helmet / 20 : max * bonus[0] / bonus[1];
			if (hunter)
			{
				roll = roll * 13 / 10;
				max = withHelmet ? max : max * 5 / 4;
			}
			if (scorching)
			{
				roll = (long) (roll * demonbane(t, 30));
				max = withHelmet ? max : (long) (max * demonbane(t, 30));
			}
			if (revenant)
			{
				roll = roll * 3 / 2;
				max = withHelmet ? max : max * 3 / 2;
			}
			if (tonalztics)
			{
				max = max * 3 / 4; // two hits, each rolled on its own up to three quarters of the max
			}
			if (tbow)
			{
				roll = tbowScale(roll, tbowMagic, true);
				max = tbowScale(max, tbowMagic, false);
			}
			int speed = accurate == 1 ? l.speed : Math.max(1, l.speed - 1); // Rapid attacks one tick sooner
			// the dark bow looses two arrows at once, as the tonalztics throws twice
			// without charges the tonalztics throws once
			double shot = StyleCalc.chance(roll, defenceRoll) * landed(0, max, t)
				* ((tonalztics && !n.contains("(uncharged)")) || n.contains("dark bow") ? 2 : 1);
			if ("Corporeal Beast".equals(t.name))
			{
				// it takes half from everything but magic and a stab of a spear, a halberd or the fang
				shot /= 2;
			}
			if (l.bolt != 0 && "Crossbow".equals(c))
			{
				shot = withBolt(l.bolt, n.contains("zaryte crossbow"), p, t, max, shot);
			}
			best = Math.max(best, shot / speed);
		}
		return best;
	}

	private static double magic(Loadout l, Player p, Target t, boolean lenient)
	{
		String n = l.weaponName == null ? "" : l.weaponName.toLowerCase(Locale.ROOT);
		long base = staffBase(n, p.magic);
		boolean powered = base > 0;
		// an elemental spell on autocast: its own max hit, the monster's weakness to its element, the tome of the element
		String element = powered ? null : SetEffects.autocastElement(l.spell);
		// spells that only one staff casts: with any other they are no choice
		if (!powered && l.spell == Spells.MAGIC_DART && !n.contains("slayer's staff"))
		{
			return -1;
		}
		if (!powered && l.spell == Spells.IBAN_BLAST && !n.startsWith("iban's staff"))
		{
			return -1;
		}
		if (!powered)
		{
			if (l.spell == Spells.MAGIC_DART && n.contains("(e)") && t.onTask)
			{
				// the enchanted staff's Magic Dart on a monster of the Slayer task (OSRS Wiki)
				base = p.magic / 6 + 13;
			}
			else if (spellBase(l.spell, p.magic) > 0)
			{
				// an elemental spell of the standard spellbook, or one of the Ancient Magicks
				base = spellBase(l.spell, p.magic);
			}
			else if (!lenient)
			{
				return -1;
			}
			else
			{
				base = 30; // a nominal spell
			}
		}
		boolean weak = element != null && element.equals(t.weakness) && t.weaknessSeverity > 0;
		int bonus = l.magic;
		double damage = l.magicDamage;
		if (!powered && l.spell >= 31 && l.spell <= 46)
		{
			// each Virtus piece gives 3% more with a combat spell of the Ancient Magicks (OSRS Wiki)
			damage += SetEffects.VIRTUS_ANCIENT_BONUS * l.virtus;
		}
		if (n.contains("tumeken's shadow"))
		{
			bonus *= 3;
			damage = Math.min(100, damage * 3);
		}
		long effective = (long) p.magic * p.prayerMagic / 100 + 9 + (powered ? 2 : 0); // a powered staff on Accurate
		int[] headOrNeck = headOrNeck(l, t, StyleCalc.MAGIC);
		long roll = effective * (bonus + 64) * (100 + headOrNeck[2]) / 100 * headOrNeck[0] / headOrNeck[1];
		long max = (long) Math.floor(base * (100 + damage + headOrNeck[2] + p.prayerMagicDamage) / 100.0)
			* headOrNeck[0] / headOrNeck[1];
		boolean standard = !powered && element != null;
		if (standard && (n.contains("smoke battlestaff") || n.contains("mystic smoke staff") || n.contains("twinflame staff")))
		{
			// a tenth more accuracy and damage with the standard spellbook (OSRS Wiki)
			roll = roll * 11 / 10;
			max = max * 11 / 10;
		}
		if (n.contains("dragon hunter wand") && t.dragon)
		{
			// against dragons: 75% more accuracy, 40% more damage (OSRS Wiki)
			roll = roll * 7 / 4;
			max = max * 7 / 5;
		}
		if (element != null && "water".equals(element) && element.equals(l.tome))
		{
			// a tenth more accuracy against a monster; the fifth is against players (OSRS Wiki)
			roll = roll * 11 / 10;
		}
		if (weak)
		{
			roll += effective * (bonus + 64) * t.weaknessSeverity / 100;
			max += base * t.weaknessSeverity / 100;
		}
		if (element != null && element.equals(l.tome))
		{
			max = max * 11 / 10;
		}
		long defenceRoll = (long) (t.magicLevel + 9) * (t.magic + 64);
		// a spell is cast every 5 ticks; the Harmonised nightmare staff casts the standard spellbook's every 4
		boolean twinflame = !powered && n.contains("twinflame staff");
		// the twinflame staff casts a tick slower than any other; the harmonised staff a tick sooner
		int speed = powered ? l.speed : twinflame ? 6 : element != null && n.contains("harmonised nightmare staff") ? 4 : 5;
		Target noArmour = new Target(); // flat armour does not apply to magic; Zulrah's cap does
		noArmour.cap = t.cap;
		double average = landed(0, max, noArmour);
		if (twinflame && l.spell >= 5 && l.spell <= 16)
		{
			// with Bolt, Blast and Wave spells it looses a second one that deals 40% of what the first dealt, rounded down
			double second = 0;
			for (long d = 0; d <= max; d++)
			{
				second += d * 2 / 5;
			}
			average += second / (max + 1);
		}
		if (n.contains("sanguinesti staff"))
		{
			average += 0.2 * 8; // the calculator's rule for the staff: one hit in five deals 8 more
		}
		return StyleCalc.chance(roll, defenceRoll) * average / speed;
	}

	/** The max hit of a staff with its own attack before magic damage, or 0 for a staff that casts spells. */
	/**
	 * The max hit of an elemental spell of the standard spellbook, by its autocast number and the boosted Magic
	 * level: every spell of a tier hits as hard as the strongest one the level allows (OSRS Wiki). 0 for anything else.
	 */
	static int spellBase(int spell, int magicLevel)
	{
		if (spell >= 31 && spell <= 46)
		{
			// the Ancient Magicks: rush, burst, blitz and barrage of smoke, shadow, blood and ice, in the order
			// of the game's autocast setting. Their max hits do not depend on the Magic level
			int[][] ancient = {{13, 14, 15, 16}, {17, 18, 21, 22}, {23, 24, 25, 26}, {27, 28, 29, 30}};
			return ancient[(spell - 31) / 4][(spell - 31) % 4];
		}
		if (spell == Spells.MAGIC_DART)
		{
			return magicLevel / 10 + 10;
		}
		if (spell == Spells.IBAN_BLAST)
		{
			return 25;
		}
		int tier = spell >= 1 && spell <= 16 ? (spell - 1) / 4 : spell >= 48 && spell <= 51 ? 4 : -1;
		if (tier < 0)
		{
			return 0;
		}
		// the level of the water, earth and fire spell of each tier, and the wind spell's max hit
		int[][] levels = {{5, 9, 13}, {23, 29, 35}, {47, 53, 59}, {65, 70, 75}, {85, 90, 95}};
		int[] wind = {2, 9, 13, 17, 21};
		int steps = 0;
		for (int level : levels[tier])
		{
			steps += magicLevel >= level ? 1 : 0;
		}
		return wind[tier] + steps * (tier == 0 ? 2 : 1);
	}

	static long staffBase(String lowerName, int magicLevel)
	{
		if (lowerName.contains("tumeken's shadow"))
		{
			return Math.max(1, magicLevel / 3 + 1);
		}
		if (lowerName.contains("sanguinesti staff"))
		{
			return Math.max(1, magicLevel / 3);
		}
		if (lowerName.contains("trident of the swamp"))
		{
			return Math.max(1, magicLevel / 3 - 2);
		}
		if (lowerName.contains("trident of the seas"))
		{
			return Math.max(1, magicLevel / 3 - 5);
		}
		if (lowerName.contains("eye of ayak"))
		{
			return Math.max(1, magicLevel / 3 - 6);
		}
		if (lowerName.contains("warped sceptre"))
		{
			return Math.max(1, (8L * magicLevel + 96) / 37);
		}
		return 0;
	}

	/** The Twisted bow's accuracy and damage scaling with the target's magic. */
	static long tbowScale(long current, int magic, boolean accuracy)
	{
		int factor = accuracy ? 10 : 14;
		int base = accuracy ? 140 : 250;
		long t2 = (3L * magic - factor) / 100;
		long d = 3L * magic / 10 - 10L * factor;
		long t3 = d * d / 100;
		long bonus = Math.max(0, Math.min(base, base + t2 - t3));
		return current * bonus / 100;
	}
}
