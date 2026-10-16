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
 * <p>The fights the OSRS Wiki calculator has rules of its own for are counted as it counts them: the Chambers
 * of Xeric's, the Tombs of Amascut's (Tumeken's shadow four times, Osmumten's fang's double roll the raid's
 * way, the Keris partisan of the sun below a quarter of the hitpoints, the defence roll by the raid level),
 * Verzik's first phase and the Hueycoatl's tail (each hit limited), the Nightmare's totems (magic twice), the
 * monsters every attack of a class lands at the max hit on, and the Corporeal Beast (half of each hit but
 * magic's and a corpbane stab's). The Royal Titans get no ranged figure: their ranged accuracy depends on
 * whether they stand in melee distance, which is not known here.
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
		/** The whole Void set worn, for melee, for ranged and for magic: 0 none, 1 Void, 2 Elite Void. */
		int voidMelee;
		int voidRanged;
		int voidMagic;
		/** The spell on autocast, by its number in the game's autocast setting; 0 for none or not known. */
		int spell;
		/** The element of the charged tome in the shield slot ("fire", "water", "earth"), or null. */
		String tome;
		/** Dharok's whole set is worn: the max hit grows with the hitpoints the player has lost. */
		boolean dharokSet;
		/** Ahrim's whole set with the Amulet of the damned: a quarter of the spells deal 30% more. */
		boolean ahrimsSet;
		/** The whole obsidian armour: with a TzHaar weapon, a tenth more accuracy and max hit. */
		boolean obsidianSet;
		/** A Berserker necklace: with a TzHaar weapon, a fifth more damage on each hit. */
		boolean berserkerNecklace;
		/** The ranged strength of the ammunition shot alone: the ogre bows take theirs from it and nothing else. */
		int ammoRangedStrength;
		/** A blowpipe whose darts are known: their strength is already in {@link #rangedStrength}. */
		boolean dartKnown;
		/** The name of the ammunition the weapon shoots (from the slot or Dizana's quiver), or null for none. */
		String ammo;
		/** The name of what is in the ammunition slot, whatever the weapon shoots, or null for nothing. */
		String ammoWorn;
		/** Efaritay's aid worn: a vampyre of tier 2 takes damage from any weapon. */
		boolean efaritay;
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
		/** The boosted Mining level: a pickaxe's damage on the Guardians of the Chambers of Xeric grows with it. */
		int mining = 99;
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
		 * reach), 'r' no ranged, 'g' no magic, 'p' nothing but melee with a pickaxe (the Guardians of the
		 * Chambers of Xeric), 'b' nothing but a demonbane weapon (the Doom of Mokhaiotl's shield), 'i' nothing but
		 * ranged with Ice arrows on (the Fire Warrior of Lesarkus), 'w' no magic but a water spell (Fareed).
		 */
		String immune = "";
		/** Zulrah: a hit above this is rolled again between 45 and 50. 0 for every other monster. */
		int cap;
		/**
		 * The Fragment of Seren: a hit above this becomes a random this, this + 1 or this + 2, never more than the hit
		 * (the calculator's linearMinTransformer(2, 22)). 0 for every other monster.
		 */
		int damageCap;
		/** The Salve amulet works on it. */
		boolean undead;
		/** The player is on a Slayer task of this monster: the Slayer helmet works on it. */
		boolean onTask;
		boolean demon;
		boolean dragon;
		boolean kalphite;
		/** Only a leaf-bladed weapon, broad ammunition or Magic Dart hurts it (Kurask, Turoth). */
		boolean leafy;
		/** A monster of the Chambers of Xeric: the Twisted bow scales with up to 350 magic instead of 250. */
		boolean xerician;
		/**
		 * A vampyre's tier, 0 for none: tier 3 takes damage only from the vampyrebane weapons, tier 2 also from the silver
		 * ones and with Efaritay's aid (the calculator's isImmune).
		 */
		int vampyre;
		/** What each hit that lands is divided by, rounded down, for melee, ranged and magic (1 for no division). */
		int meleeDivisor = 1;
		int rangedDivisor = 1;
		int magicDivisor = 1;
		/** A hit of 1 or more that is divided stays at least 1 (Tekton against magic, the Kraken against ranged). */
		boolean divisionKeepsOne;
		/** No division for a fire spell or a demonbane weapon (the Ice demon). */
		boolean divisionSparesFire;
		/** No division with a pickaxe in hand (the Slagilith). */
		boolean divisionSparesPickaxe;
		/** The ogres' rule: half of a Crumble Undead spell's hit instead, all of a Comp ogre bow's with brutal arrows. */
		boolean ogreDivision;
		/** Every attack lands (the Doom of Mokhaiotl shielded and burrowed, the Spawn of the Abyssal Sire). */
		boolean everyAttackLands;
		/** A hit that lands kills it, whatever the hit: every attack deals its hitpoints (Scurrius's rats, the Flowers). */
		boolean oneHit;
		/**
		 * Araxxor's Acidic, Mirrorback and Ruptura araxytes: every attack lands at the max hit with the Noxious halberd, with a
		 * crush style while crush is the highest attack bonus worn, and with a crossbow while ranged is (the calculator's for
		 * the Acidic and the Mirrorback, the OSRS Wiki's for the Ruptura too).
		 */
		boolean araxyteMax;
		/**
		 * The Abyssal Sire's Respiratory systems: every hit that lands is at least half the max hit (added to the least
		 * hit), and a demonbane weapon's hit that lands kills one (the calculator's, by the name).
		 */
		boolean leastHalfMax;
		boolean demonbaneKills;
		/** Its magic defence is rolled with its Defence level, not its Magic level (the Ice demon, Verzik Vitur). */
		boolean magicDefenceFromDefence;
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
		/**
		 * A monster of the Tombs of Amascut: Tumeken's shadow multiplies its bonuses by four instead of three, Osmumten's
		 * fang rolls its accuracy twice as the raid does it (1 - (1 - p)^2), and the Keris partisan of the sun is a
		 * quarter more accurate below a quarter of the monster's hitpoints.
		 */
		boolean toa;
		/** The raid level its defence roll is multiplied by, as (250 + it) / 250; 0 for none. */
		int toaInvocation;
		/** What each hit that lands is multiplied by, for melee, ranged and magic (the Nightmare's totems take magic twice). */
		int meleeMultiplier = 1;
		int rangedMultiplier = 1;
		int magicMultiplier = 1;
		/** Every attack of the class lands at the max hit (the calculator's ALWAYS_MAX_HIT_MONSTERS). */
		boolean alwaysMaxMelee;
		boolean alwaysMaxRanged;
		boolean alwaysMaxMagic;
		/**
		 * The Hueycoatl's tail: each hit that lands is limited to a random 0 to 4, or 0 to 9 with a crush style when crush
		 * is the highest attack bonus worn (then a hit left at nothing, a miss too, deals 1) or with an earth spell.
		 */
		boolean hueycoatlTail;
		/** Verzik's first phase: each hit that lands is limited to a random 0 to 10 for melee, 0 to 3 for ranged and magic. */
		boolean verzikP1;
		/**
		 * The Royal Titans' elementals: a spell lands with the magic attack bonus / 100 + 0.3 (45% more with the Void
		 * mage set), at most always, and then at its max hit.
		 */
		boolean titanElemental;
		/**
		 * The Wardens of the Tombs of Amascut's second phase: every attack lands; ranged and magic deal 15% to 60% of their
		 * max hit by what is left of the attack roll, and the Twisted bow's accuracy counts twice there.
		 */
		boolean wardenP2;
		/** No ranged figure: the Royal Titans' ranged accuracy depends on whether they stand in melee distance. */
		boolean noRangedFigure;
		/**
		 * The Tormented Demon's fire shield: each hit of anything but a demonbane weapon or an abyssal weapon in melee is
		 * four fifths, at least 1 (the calculator's, outside its phase "Unshielded").
		 */
		boolean fireShield;
		/**
		 * The share of the attacks that meet it without defence, every one of them landing; the others roll against its
		 * defence. The Tormented Demon's ({@link Weapons#TORMENTED_DEMON_DEFENCELESS}) until the plugin reads it in game;
		 * 0 for every other monster.
		 */
		double defencelessShare;
		/**
		 * The ticks a Scythe of vitur's attack takes on it, on average, where the fight makes it slower than the weapon's
		 * 5: 16/3 on Xarpus and Verzik's second phase ({@link Weapons#scytheSpeed}); 0 for the weapon's own speed.
		 */
		double scytheSpeed;
	}

	/**
	 * The highest of the attack bonuses worn, as the calculator picks it (getHighestOffensiveStyle): "slash", "stab",
	 * "crush", "ranged" or "magic", a tie going to the earlier of them in that order.
	 */
	static String highestBonus(int stab, int slash, int crush, int ranged, int magic)
	{
		String best = "slash";
		int most = slash;
		if (stab > most)
		{
			best = "stab";
			most = stab;
		}
		if (crush > most)
		{
			best = "crush";
			most = crush;
		}
		if (ranged > most)
		{
			best = "ranged";
			most = ranged;
		}
		return magic > most ? "magic" : best;
	}

	/**
	 * Araxxor's araxytes take the max hit of a melee attack from the Noxious halberd, and from a crush style while crush is
	 * the highest attack bonus worn ({@link #highestBonus}; the calculator's getHitChance and getAttackerDist).
	 */
	static boolean araxyteMaxMelee(String lowerName, String type, String highest)
	{
		return lowerName.startsWith("noxious halberd") || StyleCalc.CRUSH.equals(type) && "crush".equals(highest);
	}

	/** An attack's chance to land with the share of attacks that meet the monster without defence (and land). */
	static double withDefenceless(Target t, double chance)
	{
		return t.defencelessShare <= 0 ? chance : t.defencelessShare + (1 - t.defencelessShare) * chance;
	}

	/** The Tormented Demon's shield: four fifths of each hit, at least 1, on the monster's side (after a 0 is raised to 1). */
	static final Scale FIRE_SHIELD = new Scale(4, 5, 1, false);

	/** An abyssal weapon (the calculator's isUsingAbyssal, in melee): the whip, the tentacle, the dagger, the bludgeon. */
	static boolean abyssalWeapon(String lowerName)
	{
		return lowerName.contains("abyssal whip") || lowerName.contains("abyssal tentacle")
			|| lowerName.contains("abyssal dagger") || lowerName.contains("abyssal bludgeon");
	}

	static final int RUBY = 1;
	static final int DIAMOND = 2;

	/**
	 * Magic Dart and Iban Blast by their number in the game's autocast setting (OSRS Wiki, Varbit 276). Named only:
	 * neither gets a figure here ({@link #spellBase} gives 0 for both).
	 */
	static final int MAGIC_DART = 18;
	static final int IBAN_BLAST = 47;
	/** Crumble Undead by its number in the game's autocast setting (OSRS Wiki, Varbit 276): no figure either. */
	static final int CRUMBLE_UNDEAD = 17;

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
		return withBolt(bolt, zaryte, p, t, max, normal, NO_SCALE);
	}

	/** The same, with the effect's hits scaled as {@link #hitScale} says (the Great Olm's claws take a third). */
	static double withBolt(int bolt, boolean zaryte, Player p, Target t, long max, double normal, Scale scale)
	{
		return withBolt(bolt, zaryte, p, t, max, normal, scale, scale, 0);
	}

	/**
	 * The same, with the diamond bolt's effect scaled as the monster's side of {@code diamondScale} says and the ruby
	 * bolt's as the monster's side of {@code rubyScale} (the effects come after the player's own scaling: {@link
	 * Scale#monsterPart}): on the Corporeal Beast the first is halved and the second is not. Each effect is then taken as
	 * the monster's own rules take any hit ({@link #takes}: its flat armour, Zulrah's cap, and with a {@code limit} the
	 * limit of Verzik's first phase or the Hueycoatl's tail), as the calculator does.
	 */
	static double withBolt(int bolt, boolean zaryte, Player p, Target t, long max, double normal, Scale diamondScale,
		Scale rubyScale, int limit)
	{
		double diary = p.kandarinDiary ? 1.1 : 1.0;
		if (bolt == DIAMOND)
		{
			double chance = 0.10 * diary;
			long top = max * (zaryte ? 126 : 115) / 100;
			// the effect rolls from 0 to its top, a 0 raised to 1 like any hit that lands (the calculator's order)
			double effect = landed(0, top, t, diamondScale.monsterPart(), limit, false);
			return chance * effect + (1 - chance) * normal;
		}
		int health = t.hitpointsNow > 0 ? t.hitpointsNow : t.hitpoints;
		if (bolt != RUBY || health <= 0 || p.hitpoints < 10)
		{
			return normal;
		}
		double chance = 0.06 * diary;
		// the Gemstone Crab's health never runs out: the effect stops lower on it
		long hit = rubyScale.monsters(rubyHit(zaryte, t));
		return chance * takes(hit, t, limit, false) + (1 - chance) * normal;
	}

	/** Extra accuracy and damage of a demonbane weapon on this monster, as a multiplier; 1 when it is not a demon. */
	static double demonbane(Target t, int percent)
	{
		// the weapon's percentage is scaled by the monster's share and rounded down: 5% on Duke Sucellus (70) is 3%
		return t.demon ? 1 + (percent * t.demonbane / 100) / 100.0 : 1;
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
		q.mining = p.mining;
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
		if (Weapons.cannotAttackUncharged(l.weaponName))
		{
			// a powered staff without charges, a revenant bow without ether: it does not attack (OSRS Wiki)
			return false;
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
		if (cls == null || l.speed <= 0 || Weapons.cannotAttackUncharged(l.weaponName))
		{
			return -1;
		}
		String immune = t.immune == null ? "" : t.immune;
		if (immune.contains("p") && !(StyleCalc.MELEE.equals(cls) && PICKAXE.equals(l.category)))
		{
			return 0;
		}
		String n = l.weaponName == null ? "" : l.weaponName.toLowerCase(Locale.ROOT);
		// the monsters only some weapons hurt (the calculator's isImmune): the leafy ones, the vampyres of tiers 2 and
		// 3, the Doom of Mokhaiotl's shield, the Fire Warrior of Lesarkus
		if (t.leafy && !leafBladed(l, cls) || vampyreImmune(l, t, cls)
			|| immune.contains("b") && !demonbaneWeapon(n, cls) && !(StyleCalc.MAGIC.equals(cls) && unknownSpell(l, n))
			|| immune.contains("i") && !(StyleCalc.RANGED.equals(cls) && "Ice arrows".equalsIgnoreCase(l.ammoWorn)))
		{
			return 0;
		}
		Scale scale = hitScale(l, p, t, cls);
		if (StyleCalc.RANGED.equals(cls))
		{
			if (t.noRangedFigure)
			{
				return -1;
			}
			return immune.contains("r") ? 0 : ranged(l, p, t, scale);
		}
		if (StyleCalc.MAGIC.equals(cls))
		{
			// Fareed takes magic only from a water spell: a powered staff casts none
			boolean water = staffBase(n, 99) == 0 && "water".equals(SetEffects.autocastElement(l.spell));
			return immune.contains("g") || immune.contains("w") && !water ? 0 : magic(l, p, t, lenient, scale);
		}
		if (immune.contains("M") || (immune.contains("f") && !"Polearm".equals(l.category)))
		{
			return 0;
		}
		return melee(l, p, t, scale);
	}

	/**
	 * A staff on a spell this class has no figure for (a demonbane spell among them): left to {@link #magic}, which gives
	 * it none, rather than counted as harmless.
	 */
	private static boolean unknownSpell(Loadout l, String lowerName)
	{
		return staffBase(lowerName, 99) == 0 && spellBase(l.spell, 99) == 0;
	}

	/**
	 * What hurts a leafy monster (the calculator's isWearingLeafBladedWeapon): a leaf-bladed battleaxe, sword or spear
	 * in melee, Magic Dart, or broad arrows or bolts shot by the weapon.
	 */
	static boolean leafBladed(Loadout l, String cls)
	{
		String n = l.weaponName == null ? "" : l.weaponName.toLowerCase(Locale.ROOT);
		if (StyleCalc.MELEE.equals(cls))
		{
			return n.contains("leaf-bladed");
		}
		if (StyleCalc.MAGIC.equals(cls))
		{
			return staffBase(n, 99) == 0 && l.spell == MAGIC_DART;
		}
		String ammo = l.ammo == null ? "" : l.ammo.toLowerCase(Locale.ROOT);
		return ammo.equals("broad arrows") || ammo.equals("broad bolts") || ammo.equals("amethyst broad bolts")
			|| ammo.equals("seeking broad arrows");
	}

	/**
	 * True when nothing of this weapon hurts the vampyre (the calculator's isImmune and wearingVampyrebane): tier 3
	 * takes damage only from the Ivandis and Blisterwood flails, the Blisterwood sickle, the Hallowed flail, the Sunspear
	 * and the Blisterwood stake; tier 2 also from the Rod of Ivandis, any silver weapon or silver bolts, and from any
	 * weapon with Efaritay's aid on.
	 */
	static boolean vampyreImmune(Loadout l, Target t, String cls)
	{
		if (t.vampyre < 2)
		{
			return false;
		}
		String n = l.weaponName == null ? "" : l.weaponName.toLowerCase(Locale.ROOT);
		if (vampyrebane(t, cls, n))
		{
			return false;
		}
		if (t.vampyre == 3)
		{
			return true;
		}
		return !l.efaritay && !silverWeapon(l, n, cls);
	}

	/**
	 * A vampyrebane weapon for the vampyre's tier (the calculator's wearingVampyrebane): the Blisterwood stake shot, the
	 * Ivandis and Blisterwood flails, the Blisterwood sickle, the Hallowed flail and the Sunspear in melee (in any style on
	 * tier 2), and the Rod of Ivandis on tier 2.
	 */
	static boolean vampyrebane(Target t, String cls, String lowerName)
	{
		boolean melee = StyleCalc.MELEE.equals(cls);
		return StyleCalc.RANGED.equals(cls) && lowerName.equals("blisterwood stake") || (melee || t.vampyre == 2)
			&& (lowerName.equals("ivandis flail") || lowerName.equals("blisterwood sickle") || lowerName.equals("blisterwood flail")
			|| lowerName.equals("hallowed flail") || lowerName.equals("sunspear") || t.vampyre == 2 && lowerName.startsWith("rod of ivandis"));
	}

	/** A silver weapon (the calculator's isWearingSilverWeapon): silver bolts or a Blisterwood stake, or a silver melee weapon. */
	private static boolean silverWeapon(Loadout l, String lowerName, String cls)
	{
		if (StyleCalc.RANGED.equals(cls))
		{
			String ammo = l.ammo == null ? "" : l.ammo.toLowerCase(Locale.ROOT);
			return ammo.startsWith("silver bolts") || lowerName.equals("blisterwood stake");
		}
		if (!StyleCalc.MELEE.equals(cls))
		{
			return false;
		}
		for (String s : new String[]{"blessed axe", "ivandis flail", "blisterwood flail", "hallowed flail", "silver sickle",
			"emerald sickle", "ruby sickle", "blisterwood sickle", "silverlight", "darklight", "arclight", "rod of ivandis",
			"wolfbane"})
		{
			if (lowerName.equals(s) || lowerName.startsWith(s + " ") || lowerName.startsWith("enchanted " + s))
			{
				return true;
			}
		}
		return false;
	}

	/** The weapon category of the pickaxes in the bundled table. */
	static final String PICKAXE = "Pickaxe";

	/**
	 * How each hit that lands is scaled against a monster, in the calculator's order: first the player's own multipliers
	 * ({@link #before}, each rounded down: a vampyrebane weapon's, Efaritay's aid's), then the numerator and divisor
	 * with their floor, on the player's side of the raise of a hit of 0 that landed to 1 or on the monster's, and last,
	 * on the monster's side, the most a hit deals ({@link #most}).
	 */
	static final class Scale
	{
		final int numerator;
		final int divisor;
		/** The least a hit of 1 or more becomes; 0 for no floor. */
		final int floor;
		/** The numerator and divisor are the player's hit's (before the raise to 1), not the monster's (after it). */
		final boolean playerSide;
		/** The player's own multipliers, numerator and divisor in turn, each rounded down, before anything else. */
		final int[] before;
		/** The most a hit deals once scaled, on the monster's side (a silver weapon on a vampyre of tier 2); 0 for none. */
		final int most;

		Scale(int numerator, int divisor, int floor, boolean playerSide)
		{
			this(numerator, divisor, floor, playerSide, new int[0], 0);
		}

		Scale(int numerator, int divisor, int floor, boolean playerSide, int[] before, int most)
		{
			this.numerator = numerator;
			this.divisor = divisor;
			this.floor = floor;
			this.playerSide = playerSide;
			this.before = before;
			this.most = most;
		}

		/**
		 * The same with one more of the player's own multipliers, before the others (the Berserker necklace's on a TzHaar
		 * weapon, Ahrim's set's 30%: the calculator applies them before a vampyre's).
		 */
		Scale first(int numerator, int divisor)
		{
			int[] b = new int[before.length + 2];
			b[0] = numerator;
			b[1] = divisor;
			System.arraycopy(before, 0, b, 2, before.length);
			return new Scale(this.numerator, this.divisor, floor, playerSide, b, most);
		}

		/** True when it changes no hit. */
		boolean plain()
		{
			return numerator == divisor && floor == 0 && before.length == 0 && most == 0;
		}

		/** The numerator and divisor on one hit: rounded down, and kept at the floor when it was at least that. */
		long main(long hit)
		{
			long d = hit * numerator / divisor;
			if (floor != 0)
			{
				d = hit >= floor ? Math.max(floor, d) : Math.max(hit, d);
			}
			return d;
		}

		/** A hit rolled that landed: the player's multipliers, the raise of a 0 to 1, the monster's side. */
		long landed(long rolled)
		{
			long h = rolled;
			for (int i = 0; i + 1 < before.length; i += 2)
			{
				h = h * before[i] / before[i + 1];
			}
			h = playerSide ? Math.max(1, main(h)) : main(Math.max(1, h));
			return most > 0 ? Math.min(most, h) : h;
		}

		/** A hit made once the others are raised to 1 (a ruby bolt's effect, the Twinflame staff's second hit): the monster's side. */
		long monsters(long hit)
		{
			long h = playerSide ? hit : main(hit);
			return most > 0 ? Math.min(most, h) : h;
		}

		/** The monster's side alone, for a hit made after the player's own scaling and before the raise (a diamond bolt's effect). */
		Scale monsterPart()
		{
			return playerSide ? new Scale(1, 1, 0, false, new int[0], most)
				: before.length == 0 ? this : new Scale(numerator, divisor, floor, false, new int[0], most);
		}
	}

	/** A hit left as it is. */
	static final Scale NO_SCALE = new Scale(1, 1, 0, false);

	/**
	 * How each hit that lands is scaled against this monster, as numerator, divisor and floor (the OSRS Wiki
	 * calculator's rules): a pickaxe on a Guardian of the Chambers of Xeric deals (50 + Mining level + the pickaxe's
	 * bonus) / 150 of its hit; Tekton, the Glowing crystal, the Great Olm and the Ice demon take a part of some classes'
	 * hits, the Ice demon of all of them but a fire spell's or a demonbane weapon's; the Kraken and the Cave kraken a
	 * seventh of a ranged hit, at least 1; the Slagilith a third of any hit but a pickaxe's; the ogres (Zogre, Skogre,
	 * Slash Bash) a quarter, half of Crumble Undead's, all of a Comp ogre bow's with brutal arrows. The Nightmare's
	 * totems take twice the hits of magic. The Tormented Demon's fire shield takes a fifth off each hit, at least 1, of
	 * anything but a demonbane weapon or an abyssal weapon in melee. A vampyre has rules of its own ({@link
	 * #vampyreScale}).
	 */
	static Scale hitScale(Loadout l, Player p, Target t, String cls)
	{
		String n = l.weaponName == null ? "" : l.weaponName.toLowerCase(Locale.ROOT);
		if (t.immune != null && t.immune.contains("p"))
		{
			return StyleCalc.MELEE.equals(cls) && PICKAXE.equals(l.category)
				? new Scale(50 + p.mining + pickaxeBonus(n), 150, 0, true) : NO_SCALE;
		}
		if (t.fireShield)
		{
			// the demon has no other rule for the hit
			boolean spared = demonbaneWeapon(n, cls) || StyleCalc.MELEE.equals(cls) && abyssalWeapon(n);
			return spared ? NO_SCALE : FIRE_SHIELD;
		}
		if (t.vampyre > 0)
		{
			// no vampyre has another rule for the hit
			return vampyreScale(l, t, cls, n);
		}
		int divisor = StyleCalc.MELEE.equals(cls) ? t.meleeDivisor : StyleCalc.RANGED.equals(cls) ? t.rangedDivisor
			: t.magicDivisor;
		int multiplier = Math.max(1, StyleCalc.MELEE.equals(cls) ? t.meleeMultiplier : StyleCalc.RANGED.equals(cls)
			? t.rangedMultiplier : t.magicMultiplier);
		if (divisor > 1 && t.divisionSparesFire)
		{
			boolean fire = StyleCalc.MAGIC.equals(cls) && staffBase(n, 99) == 0
				&& "fire".equals(SetEffects.autocastElement(l.spell));
			if (fire || demonbaneWeapon(n, cls))
			{
				divisor = 1;
			}
		}
		if (divisor > 1 && t.divisionSparesPickaxe && PICKAXE.equals(l.category))
		{
			divisor = 1;
		}
		if (divisor > 1 && t.ogreDivision)
		{
			// the ogres: a Crumble Undead spell is halved instead, a Comp ogre bow's brutal arrows not at all
			if (StyleCalc.MAGIC.equals(cls) && staffBase(n, 99) == 0 && l.spell == CRUMBLE_UNDEAD)
			{
				divisor = 2;
			}
			else if (StyleCalc.RANGED.equals(cls) && n.equals("comp ogre bow") && l.ammoWorn != null
				&& l.ammoWorn.toLowerCase(Locale.ROOT).contains(" brutal"))
			{
				divisor = 1;
			}
		}
		if (divisor <= 1 && multiplier <= 1)
		{
			return NO_SCALE;
		}
		return new Scale(multiplier, Math.max(1, divisor), divisor > 1 && t.divisionKeepsOne ? 1 : 0, false);
	}

	/**
	 * What a vampyre's rules do to each hit (the calculator's getAttackerDist and applyNpcTransforms): the Hallowed flail
	 * deals a quarter more and the Sunspear a half more on every tier, a silver weapon a tenth more on tier 1, and
	 * Efaritay's aid a tenth more before each of those, every one rounded down before the raise to 1; on tier 2 a weapon
	 * that is not vampyrebane deals half with Efaritay's aid, and otherwise a silver weapon (the flail is one) deals 10
	 * at most (the calculator's limit). Which weapons reach a vampyre at all: {@link #vampyreImmune}.
	 */
	static Scale vampyreScale(Loadout l, Target t, String cls, String lowerName)
	{
		boolean melee = StyleCalc.MELEE.equals(cls);
		boolean silver = silverWeapon(l, lowerName, cls);
		int[] own = melee && lowerName.equals("hallowed flail") ? new int[]{125, 100}
			: melee && lowerName.equals("sunspear") ? new int[]{150, 100}
			: silver && t.vampyre == 1 ? new int[]{11, 10} : new int[0];
		int[] before = own.length > 0 && l.efaritay ? new int[]{11, 10, own[0], own[1]} : own;
		if (t.vampyre == 2 && !vampyrebane(t, cls, lowerName) && l.efaritay)
		{
			return new Scale(1, 2, 0, false, before, 0);
		}
		int most = t.vampyre == 2 && silver ? 10 : 0;
		return before.length == 0 && most == 0 ? NO_SCALE : new Scale(1, 1, 0, false, before, most);
	}

	/**
	 * What a vampyre's rules add to the accuracy of a melee attack (the calculator's getPlayerMaxMeleeAttackRoll): a
	 * quarter for the Hallowed flail and the Sunspear, and 15% for a silver weapon with Efaritay's aid. 1 elsewhere.
	 */
	static double vampyreAccuracy(Loadout l, Target t, String lowerName)
	{
		if (t.vampyre == 0)
		{
			return 1;
		}
		double a = lowerName.equals("hallowed flail") || lowerName.equals("sunspear") ? 1.25 : 1;
		return l.efaritay && silverWeapon(l, lowerName, StyleCalc.MELEE) ? a * 23 / 20 : a;
	}

	/** The Corporeal Beast's half of every hit, taken after a hit of 0 that landed is raised to 1, as the calculator does. */
	static final Scale CORP_HALF = new Scale(1, 2, 0, false);

	/**
	 * The Corporeal Beast takes half of each hit from anything but magic and a corpbane weapon on stab (Osmumten's
	 * fang, a halberd or a spear), ranged included (the calculator's {@code isWearingCorpbaneWeapon}).
	 */
	static boolean corpHalves(Target t, String cls, boolean corpbaneStab)
	{
		return "Corporeal Beast".equals(t.name) && !StyleCalc.MAGIC.equals(cls) && !corpbaneStab;
	}

	/**
	 * The mean of what a hit of {@code damage} deals once it is limited to a random 0 to {@code limit} (Verzik's first
	 * phase, the Hueycoatl's tail: the calculator's linearMinTransformer); with {@code zeroToOne} a hit left at nothing
	 * deals 1 (the tail's rule for crush). A {@code limit} of 0 leaves the hit as it is.
	 */
	static double limitedMean(long damage, int limit, boolean zeroToOne)
	{
		if (limit <= 0)
		{
			return damage;
		}
		long d = Math.max(0, damage);
		if (zeroToOne && d == 0)
		{
			return 1;
		}
		// the sum of min(d, i) for i from 0 to the limit
		double sum = d >= limit ? limit * (limit + 1) / 2.0 : d * (d + 1) / 2.0 + d * (double) (limit - d);
		// the draw of 0 leaves nothing, which becomes 1
		return (sum + (zeroToOne ? 1 : 0)) / (limit + 1);
	}

	/**
	 * The limit of each hit for the class, 0 for none (the calculator's linearMinTransformer): on Verzik's first phase 10
	 * for melee and 3 otherwise (the Dawnbringer, which has no figure here, is not limited); on the Hueycoatl's tail 4,
	 * or 9 for crush while crush is the highest attack bonus worn, or for an earth spell.
	 */
	static int hitLimit(Target t, String cls, boolean crushRule, boolean earthSpell)
	{
		if (t.verzikP1)
		{
			return StyleCalc.MELEE.equals(cls) ? 10 : 3;
		}
		return !t.hueycoatlTail ? 0 : crushRule || earthSpell ? 9 : 4;
	}

	/**
	 * What a pickaxe adds to the damage on the Guardians: the Mining level it needs to be wielded, and 61 for
	 * the dragon pickaxe and every pickaxe above it (the calculator's table).
	 */
	static int pickaxeBonus(String lowerName)
	{
		switch (lowerName)
		{
			case "bronze pickaxe":
			case "iron pickaxe":
				return 1;
			case "steel pickaxe":
				return 6;
			case "black pickaxe":
				return 11;
			case "mithril pickaxe":
				return 21;
			case "adamant pickaxe":
				return 31;
			case "rune pickaxe":
			case "gilded pickaxe":
				return 41;
			default:
				return 61;
		}
	}

	/** A demonbane weapon of the class, as the calculator counts one (the demonbane spells are not modelled here). */
	static boolean demonbaneWeapon(String lowerName, String cls)
	{
		if (StyleCalc.RANGED.equals(cls))
		{
			return lowerName.contains("scorching bow");
		}
		if (!StyleCalc.MELEE.equals(cls))
		{
			return false;
		}
		for (String s : new String[]{"silverlight", "darklight", "arclight", "emberlight", "bone claws", "burning claws"})
		{
			if (lowerName.contains(s))
			{
				return true;
			}
		}
		return false;
	}

	/** Mean damage of a hit rolled evenly from min to max that landed, after the monster's flat armour and cap. */
	static double landed(long min, long max, Target t)
	{
		return landed(min, max, t, NO_SCALE);
	}

	/** The same, with each hit first scaled as {@link #hitScale} says. */
	static double landed(long min, long max, Target t, Scale scale)
	{
		return landed(min, max, t, scale, 0, false);
	}

	/**
	 * The same, each hit then limited as on Verzik's first phase or the Hueycoatl's tail ({@link #limitedMean}); a
	 * {@code limit} of 0 for none.
	 */
	static double landed(long min, long max, Target t, Scale scale, int limit, boolean zeroToOne)
	{
		boolean plain = scale.plain();
		if (plain && limit <= 0 && !hasHitRules(t))
		{
			return StyleCalc.averageBetween(min, max);
		}
		if (max < min)
		{
			return 0;
		}
		double sum = 0;
		for (long rolled = min; rolled <= max; rolled++)
		{
			// the calculator raises a hit of 0 that landed to 1: after the player's own scaling, before the monster's
			long h = plain ? Math.max(1, rolled) : scale.landed(rolled);
			sum += takes(h, t, limit, zeroToOne);
		}
		return sum / (max - min + 1);
	}

	/** True when the monster's own rules change a hit once it is scaled: its flat armour, Zulrah's or Seren's cap. */
	private static boolean hasHitRules(Target t)
	{
		return t.flatArmour != 0 || t.cap > 0 || t.damageCap > 0;
	}

	/**
	 * What a hit deals once the monster's own rules have taken it, in the calculator's order (applyNpcTransforms):
	 * Zulrah's cap, the Fragment of Seren's, the limit of Verzik's first phase or of the Hueycoatl's tail
	 * ({@code limit}, 0 for none; neither fight has a cap or flat armour), and the flat armour last, which takes a hit
	 * down to 0 and no further (a negative one adds to it). No monster has two of the caps. The hit has been scaled
	 * already (its raise to 1 included, for a hit the player made).
	 */
	static double takes(long hit, Target t, int limit, boolean zeroToOne)
	{
		if (limit > 0)
		{
			return limitedMean(hit, limit, zeroToOne);
		}
		double d = t.cap > 0 && hit > t.cap ? (t.cap - 5 + t.cap) / 2.0 : hit;
		if (t.damageCap > 0 && hit > t.damageCap)
		{
			// a random cap, cap + 1 or cap + 2, never more than the hit
			d = (t.damageCap + Math.min(hit, t.damageCap + 1) + Math.min(hit, t.damageCap + 2)) / 3.0;
		}
		return Math.max(0, d - t.flatArmour);
	}

	/**
	 * What a hit of exactly {@code hit} deals once it has landed: raised to 1, scaled as {@link #hitScale} says and taken
	 * down by the monster's flat armour. For the monsters every attack of a class lands at the max hit on.
	 */
	static double atMax(long hit, Target t, Scale scale)
	{
		return landed(hit, hit, t, scale);
	}

	/** How much more {@code b} deals than {@code a}, in percent; NaN when either is unknown. */
	static double gain(double a, double b)
	{
		return a <= 0 || b < 0 ? Double.NaN : (b / a - 1) * 100;
	}

	private static double melee(Loadout l, Player p, Target t, Scale perHit)
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
		String lower = l.weaponName == null ? "" : l.weaponName.toLowerCase(Locale.ROOT);
		if (lower.startsWith("keris partisan of amascut") && !t.toa)
		{
			// the calculator takes 50 stab and 22 strength off the keris outside the Tombs of Amascut (Equipment.ts)
			in.stab -= 50;
			in.strength -= 22;
		}
		in.inquisitor = l.inquisitor;
		in.fang = Weapons.isFang(l.weaponName);
		in.corpbane = Weapons.isCorpbane(l.weaponName);
		in.defenceLevel = t.defenceLevel;
		in.defenceStab = t.stab;
		in.defenceSlash = t.slash;
		in.defenceCrush = t.crush;
		in.corporealBeast = "Corporeal Beast".equals(t.name);
		in.alwaysHit = t.wardenP2 || t.everyAttackLands;
		// the Tormented Demon: a share of the attacks meets it without defence and lands
		in.defencelessShare = t.defencelessShare;
		int[] bonus = headOrNeck(l, t, StyleCalc.MELEE);
		in.bonusNumerator = bonus[0];
		in.bonusDenominator = bonus[1];
		String n = l.weaponName == null ? "" : l.weaponName.toLowerCase(Locale.ROOT);
		// weapons with a rule of their own (OSRS Wiki): what they add depends on what the monster is
		double keris = 1;
		if (n.contains("emberlight") || n.contains("arclight") && !n.contains("(inactive)"))
		{
			in.weaponAccuracy = in.weaponDamage = demonbane(t, 70);
		}
		else if (n.contains("silverlight") || n.contains("darklight") || n.contains("arclight"))
		{
			// the Arclight without charges "functions identically to Darklight" (OSRS Wiki, Arclight; the calculator
			// keeps its 70%, the wiki decides)
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
			// in whole numbers, as the calculator rounds them: 115/100 of a max hit of 100 is 115, not 114
			in.damageNumerator = n.contains("amascut") ? 115 : 133;
			in.damageDenominator = 100;
			in.accuracyNumerator = n.contains("breaching") ? 133 : 1;
			in.accuracyDenominator = n.contains("breaching") ? 100 : 1;
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
		else if ((n.contains("viggora") || n.contains("ursine")) && t.inWilderness && !Weapons.uncharged(l.weaponName))
		{
			// only with revenant ether in it (the calculator's isRevWeaponBuffApplicable: version Charged)
			in.weaponAccuracy = in.weaponDamage = 1.5;
		}
		// a vampyre: the Hallowed flail and the Sunspear a quarter more accurate, a silver weapon 15% with Efaritay's aid
		in.weaponAccuracy *= vampyreAccuracy(l, t, n);
		if (n.contains("keris partisan of the sun") && sunKerisRule(t))
		{
			// in the Tombs of Amascut, below a quarter of the monster's hitpoints: a quarter more accuracy, after the
			// keris's own rule on a kalphite (OSRS Wiki, Keris partisan of the sun; the calculator's order)
			in.weaponAccuracy *= 1.25;
		}
		if (l.voidMelee > 0)
		{
			// the set raises the effective Attack and Strength levels by a tenth
			in.voidMelee = true;
		}
		// a TzHaar weapon: the obsidian armour adds a tenth of the accuracy and max hit, the Berserker necklace a fifth
		// to each hit (the calculator's)
		boolean tzhaar = Weapons.isTzhaarWeapon(l.weaponName);
		in.obsidian = tzhaar && l.obsidianSet;
		boolean berserker = tzhaar && l.berserkerNecklace;
		// the Tombs of Amascut: the raid level's defence roll and the fang's double roll the raid's way
		in.toa = t.toa;
		in.defenceRollInvocation = t.toaInvocation;
		// the Scythe of vitur hits a large monster up to three times: full, half and a quarter, each rolled on its own
		int hits = !n.contains("scythe of vitur") ? 1 : t.size >= 3 ? 3 : t.size == 2 ? 2 : 1;
		// the Hueycoatl's tail: crush is limited to 9 only while crush is the highest attack bonus worn
		boolean crushHighest = l.crush > l.slash && l.crush > l.stab;
		boolean twoHits = twoHitWeapon(n);
		// Araxxor's araxytes: the Noxious halberd, and a crush style while crush is the highest attack bonus worn
		String highest = highestBonus(l.stab, l.slash, l.crush, l.ranged, l.magic);
		double best = -1;
		for (StyleData.Style s : l.styles)
		{
			if (!StyleCalc.melee(s.type))
			{
				continue;
			}
			double[] cm = StyleCalc.chanceAndMax(in, s.type, s.stance);
			long max = (long) cm[1];
			// Osmumten's fang deals 15% to 85% of its max hit with every style (its double roll is for stab only)
			long min = in.fang ? max * 3 / 20 : 0;
			long top = max - min;
			// the Respiratory systems: half the max hit added to the least hit of every hit (the calculator's order)
			long least = t.leastHalfMax ? min + top / 2 : min;
			// the Corporeal Beast's half: every hit but a corpbane weapon's stab, each rounded down on its own
			Scale scale = corpHalves(t, StyleCalc.MELEE, in.corpbane && StyleCalc.STAB.equals(s.type)) ? CORP_HALF : perHit;
			boolean crushRule = StyleCalc.CRUSH.equals(s.type) && crushHighest;
			int limit = hitLimit(t, StyleCalc.MELEE, crushRule, false);
			// on the tail a crush hit left at nothing deals 1
			boolean tailCrush = t.hueycoatlTail && crushRule;
			if (t.oneHit)
			{
				// a hit kills it: every attack that can deal anything deals its hitpoints, then the monster's own rules take
				// it (the calculator returns before every other rule of the weapon)
				best = Math.max(best, top > 0 ? monsterSide(t.hitpoints, t, scale, 0) : 0);
				continue;
			}
			if (t.demonbaneKills && demonbaneWeapon(n, StyleCalc.MELEE))
			{
				// a demonbane weapon's hit that lands kills it
				best = Math.max(best, cm[0] * monsterSide(t.hitpoints, t, scale, 0));
				continue;
			}
			if (t.araxyteMax && araxyteMaxMelee(n, s.type, highest))
			{
				// every attack lands at the max hit, a single hit whatever the weapon (the calculator returns before the
				// Scythe's other hits, the two-hit weapons' split and Dharok's set)
				best = Math.max(best, atMax(top, t, scale));
				continue;
			}
			if (berserker)
			{
				// each hit a fifth more, rounded down, before a hit of 0 becomes 1
				scale = scale.first(6, 5);
			}
			if (t.alwaysMaxMelee)
			{
				// every attack lands at the max hit of all its hits together, then the monster's own rules take it
				long all = top;
				if (l.dharokSet)
				{
					all = all * (10000 + (long) Math.max(0, p.hitpointsMax - p.hitpoints) * p.hitpointsMax) / 10000;
				}
				for (int h = 1; h < hits; h++)
				{
					all += max >> h;
				}
				best = Math.max(best, atMax(all, t, scale));
				continue;
			}
			double average = twoHits
				// Torag's hammers, the Sulphur blades, the Glacial temotli, the Earthbound tecpatl: two hits, up to half
				// the max each, rolled on their own
				? landed(least, Math.max(least, max / 2), t, scale, limit, tailCrush)
					+ landed(least, Math.max(least, max - max / 2), t, scale, limit, tailCrush)
				: landed(least, top, t, scale, limit, tailCrush);
			if (l.dharokSet)
			{
				// Dharok's set: 1% more for each hitpoint lost, scaled by the Hitpoints level over 100. Each hit
				// is scaled and rounded down on its own, as in the game, not the max hit alone
				long dharok = 10000 + (long) Math.max(0, p.hitpointsMax - p.hitpoints) * p.hitpointsMax;
				double sum = 0;
				for (long h = least; h <= top; h++)
				{
					long scaled = h * dharok / 10000;
					sum += landed(scaled, scaled, t, scale, limit, tailCrush);
				}
				average = sum / (top - least + 1);
			}
			for (int h = 1; h < hits; h++)
			{
				average += landed(least, Math.max(least, max >> h), t, scale, limit, tailCrush);
			}
			// on the tail with the crush rule a miss is a hit of nothing, which deals 1, for each of the hits
			double misses = tailCrush ? (twoHits ? 2 : hits) * (1 - cm[0]) : 0;
			best = Math.max(best, cm[0] * average * keris + misses);
		}
		// the Scythe on Xarpus and Verzik's second phase: one attack every 16/3 ticks, as the fight is played
		double speed = t.scytheSpeed > 0 && n.contains("scythe of vitur") ? t.scytheSpeed : l.speed;
		return best < 0 ? -1 : best / speed;
	}

	/**
	 * The melee weapons that hit twice, each hit up to half the max and rolled on its own (the calculator's
	 * isWearingTwoHitWeapon): Torag's hammers, the Sulphur blades, the Glacial temotli and the Earthbound tecpatl.
	 */
	static boolean twoHitWeapon(String lowerName)
	{
		return lowerName.contains("torag's hammers") || lowerName.contains("sulphur blades")
			|| lowerName.contains("glacial temotli") || lowerName.contains("earthbound tecpatl");
	}

	/**
	 * The staves that add a tenth to the accuracy and to the damage of the standard spellbook's spells (the
	 * calculator's isWearingSmokeStaff): the Smoke battlestaff, the Mystic smoke staff and the Twinflame staff.
	 */
	static boolean smokeStaff(String lowerName)
	{
		return lowerName.contains("smoke battlestaff") || lowerName.contains("mystic smoke staff")
			|| lowerName.contains("twinflame staff");
	}

	/** A spell of the standard spellbook by its autocast number: the strikes, bolts, blasts, waves and surges. */
	static boolean standardSpell(int spell)
	{
		return spell >= 1 && spell <= 16 || spell >= 48 && spell <= 51;
	}

	/** The Keris partisan of the sun's rule: a monster of the Tombs of Amascut below a quarter of its hitpoints. */
	static boolean sunKerisRule(Target t)
	{
		return t.toa && t.hitpointsNow > 0 && t.hitpointsNow < t.hitpoints / 4;
	}

	private static double ranged(Loadout l, Player p, Target t, Scale perHit)
	{
		String c = l.category;
		int defence = "Bow".equals(c) ? t.standard : "Thrown".equals(c) ? t.light : t.heavy;
		// in the Tombs of Amascut the roll grows with the raid level, after the drains (the calculator's order)
		long defenceRoll = ToaScaling.defenceRoll((long) (t.defenceLevel + 9) * (defence + 64), t.toaInvocation);
		boolean tbow = l.weaponName != null && l.weaponName.toLowerCase(Locale.ROOT).contains("twisted bow");
		int tbowMagic = Math.min(t.xerician ? 350 : 250, t.tbowMagic);
		int[] bonus = headOrNeck(l, t, StyleCalc.RANGED);
		String n = l.weaponName == null ? "" : l.weaponName.toLowerCase(Locale.ROOT);
		// the Crystal bow and the Bow of Faerdhinen, not the Gauntlet's bow
		boolean crystalBow = SetEffects.isCrystalBow(l.weaponName);
		boolean hunter = n.contains("dragon hunter crossbow") && t.dragon;
		boolean scorching = n.contains("scorching bow") && t.demon;
		boolean revenant = (n.contains("craw's bow") || n.contains("webweaver")) && t.inWilderness && !Weapons.uncharged(l.weaponName);
		boolean tonalztics = n.contains("tonalztics");
		// two hits, each rolled on its own: the charged Tonalztics of Ralos (uncharged it hits once) and the Dark bow's two
		// arrows (the calculator's)
		int shots = tonalztics && !Weapons.uncharged(l.weaponName) || n.startsWith("dark bow") ? 2 : 1;
		// the ogre bows: their max hit is the ranged level and 10 with the ammunition's strength alone, nothing else
		boolean ogreBow = n.equals("ogre bow") || n.equals("comp ogre bow");
		// with an imbued Slayer helmet on a task these weapons add to its bonus instead of multiplying after it
		boolean withHelmet = bonus[0] == 23;
		int helmet = withHelmet ? 23 + (hunter ? 5 : 0) + (scorching ? 6 : 0) + (revenant ? 10 : 0) : 0;
		double best = -1;
		for (int accurate = 0; accurate <= 1; accurate++)
		{
			long effective = (long) p.ranged * p.prayerRanged / 100 + 8 + (accurate == 1 ? 3 : 0);
			long effectiveStrength = (long) p.ranged * p.prayerRangedStrength / 100 + 8 + (accurate == 1 ? 3 : 0);
			if (l.voidRanged > 0)
			{
				// the set raises the effective levels, rounded down, before the gear's bonus: Elite Void an eighth of the strength
				effective = effective * 11 / 10;
				effectiveStrength = l.voidRanged == 2 ? effectiveStrength * 9 / 8 : effectiveStrength * 11 / 10;
			}
			long roll = effective * (l.ranged + 64);
			long max = (effectiveStrength * (l.rangedStrength + 64) + 320) / 640;
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
				max = max * 3 / 4; // each hit rolled up to three quarters of the max
			}
			if (tbow)
			{
				roll = tbowScale(roll, tbowMagic, true);
				max = tbowScale(max, tbowMagic, false);
				if (t.wardenP2)
				{
					// the calculator applies the bow's accuracy twice on the Wardens of the second phase (game update of
					// 2023-06-21)
					roll = tbowScale(roll, tbowMagic, true);
				}
			}
			if (ogreBow)
			{
				// the calculator's: the boosted level and 10, no prayer, no stance, no helmet, the ammunition's strength alone
				max = (((long) p.ranged + 10) * (l.ammoRangedStrength + 64) + 320) / 640;
			}
			int speed = accurate == 1 ? l.speed : Math.max(1, l.speed - 1); // Rapid attacks one tick sooner
			if (t.oneHit)
			{
				// a hit kills it: every shot deals its hitpoints, no bolt's effect or second hit counted (the calculator's)
				best = Math.max(best, (max > 0 ? monsterSide(t.hitpoints, t, perHit, 0) : 0) / speed);
				continue;
			}
			if (t.araxyteMax && "Crossbow".equals(c) && "ranged".equals(highestBonus(l.stab, l.slash, l.crush, l.ranged, l.magic)))
			{
				// Araxxor's araxytes: a crossbow, while ranged is the highest attack bonus worn, lands every shot at its max
				// hit, with no bolt's effect (the calculator returns before them)
				best = Math.max(best, atMax(max, t, perHit) / speed);
				continue;
			}
			boolean bolts = l.bolt != 0 && "Crossbow".equals(c);
			boolean zaryte = n.contains("zaryte crossbow");
			if (t.alwaysMaxRanged)
			{
				// every attack lands at the highest hit it can deal, a bolt's effect included
				long top = shots * max;
				if (bolts && l.bolt == DIAMOND)
				{
					top = Math.max(top, max * (zaryte ? 126 : 115) / 100);
				}
				else if (bolts && l.bolt == RUBY && p.hitpoints >= 10)
				{
					top = Math.max(top, rubyHit(zaryte, t));
				}
				best = Math.max(best, atMax(top, t, perHit) / speed);
				continue;
			}
			// the Corporeal Beast halves the shot and a diamond bolt's effect, not a ruby bolt's (the calculator's order)
			Scale scale = corpHalves(t, StyleCalc.RANGED, false) ? CORP_HALF : perHit;
			int limit = hitLimit(t, StyleCalc.RANGED, false, false);
			// the Wardens of the second phase: every shot lands, between a part of the max and that part plus a fifth
			// the Respiratory systems: every hit at least half the max
			long[] range = t.wardenP2 ? wardenP2Range(max, roll, defenceRoll) : new long[]{t.leastHalfMax ? max / 2 : 0, max};
			double chance = t.wardenP2 || t.everyAttackLands ? 1 : withDefenceless(t, StyleCalc.chance(roll, defenceRoll));
			if (t.demonbaneKills && demonbaneWeapon(n, StyleCalc.RANGED))
			{
				// a demonbane weapon's shot that lands kills it, no bolt's effect or second hit counted (the calculator's)
				best = Math.max(best, chance * monsterSide(t.hitpoints, t, scale, 0) / speed);
				continue;
			}
			double shot = chance * landed(range[0], range[1], t, scale, limit, false) * shots;
			if (bolts)
			{
				shot = withBolt(l.bolt, zaryte, p, t, range[1], shot, scale, perHit, limit);
			}
			best = Math.max(best, shot / speed);
		}
		return best;
	}

	/**
	 * The damage range on the Wardens of the second phase (the calculator's applyP2WardensDamageModifier): what is left of
	 * the attack roll over a third of the defence roll, from 0 to 42,000, takes the modifier from 15 to 40 (rounded down
	 * and kept there); a hit deals from modifier% to modifier + 20% of the max hit, each rounded down.
	 */
	static long[] wardenP2Range(long max, long attackRoll, long defenceRoll)
	{
		long left = Math.max(attackRoll - defenceRoll / 3, 0);
		long modifier = Math.max(Math.min(15 + 25 * left / 42000, 40), 15);
		return new long[]{max * modifier / 100, max * (modifier + 20) / 100};
	}

	/** A ruby bolt's effect: 20% of the monster's health now (22% with a Zaryte crossbow), at most 100 (110). */
	static long rubyHit(boolean zaryte, Target t)
	{
		int health = t.hitpointsNow > 0 ? t.hitpointsNow : t.hitpoints;
		boolean crab = "Gemstone Crab".equalsIgnoreCase(t.name);
		long cap = crab ? (zaryte ? 66 : 60) : (zaryte ? 110 : 100);
		return Math.min(cap, (long) Math.max(0, health) * (zaryte ? 22 : 20) / 100);
	}

	private static double magic(Loadout l, Player p, Target t, boolean lenient, Scale perHit)
	{
		String n = l.weaponName == null ? "" : l.weaponName.toLowerCase(Locale.ROOT);
		long base = staffBase(n, p.magic);
		boolean powered = base > 0;
		// an elemental spell on autocast: its own max hit, the monster's weakness to its element, the tome of the element
		String element = powered ? null : SetEffects.autocastElement(l.spell);
		if (!powered)
		{
			if (spellBase(l.spell, p.magic) > 0)
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
		if (n.contains("tumeken's shadow"))
		{
			// three times the magic attack and damage bonuses, four times inside the Tombs of Amascut (damage up to 100%)
			int factor = t.toa ? 4 : 3;
			bonus *= factor;
			damage = Math.min(100, damage * factor);
		}
		if (l.voidMagic == 2)
		{
			// the Elite Void mage set: 5% more magic damage (the calculator's, on top of the gear's)
			damage += 5;
		}
		long effective = (long) p.magic * p.prayerMagic / 100 + 9 + (powered ? 2 : 0); // a powered staff on Accurate
		if (l.voidMagic > 0)
		{
			// the Void mage set: the effective Magic level 45% higher, rounded down (the calculator's)
			effective = effective * 29 / 20;
		}
		int[] headOrNeck = headOrNeck(l, t, StyleCalc.MAGIC);
		// a smoke staff (the Twinflame staff among them) adds a tenth to the accuracy and the damage of the standard
		// spellbook's spells, as the Salve amulet adds its own
		int smoke = !powered && smokeStaff(n) && standardSpell(l.spell) ? 10 : 0;
		// the Dragon hunter wand against a dragon: 75% more accuracy, before the Slayer helmet, and 40% more damage, after
		// it (the calculator's order)
		boolean dragonHunterWand = n.startsWith("dragon hunter wand") && t.dragon;
		long roll = effective * (bonus + 64) * (100 + headOrNeck[2] + smoke) / 100;
		if (dragonHunterWand)
		{
			roll = roll * 7 / 4;
		}
		roll = roll * headOrNeck[0] / headOrNeck[1];
		long max = (long) Math.floor(base * (100 + damage + headOrNeck[2] + smoke + p.prayerMagicDamage) / 100.0)
			* headOrNeck[0] / headOrNeck[1];
		if (dragonHunterWand)
		{
			max = max * 7 / 5;
		}
		if (element != null && "water".equals(element) && element.equals(l.tome))
		{
			roll = roll * 6 / 5;
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
		long defenceRoll = ToaScaling.defenceRoll(
			(long) ((t.magicDefenceFromDefence ? t.defenceLevel : t.magicLevel) + 9) * (t.magic + 64), t.toaInvocation);
		// a spell is cast every 5 ticks; the Harmonised nightmare staff casts the standard spellbook's every 4, the
		// Twinflame staff anything every 6
		boolean twinflameStaff = !powered && n.contains("twinflame staff");
		int speed = powered ? l.speed : twinflameStaff ? 6 : element != null && n.contains("harmonised nightmare staff") ? 4 : 5;
		// the Twinflame staff's bolts, blasts and waves hit twice: the second hit is four tenths of the first
		boolean twinflame = twinflameStaff && l.spell >= 5 && l.spell <= 16;
		boolean sanguinesti = n.contains("sanguinesti staff");
		Target noArmour = new Target(); // flat armour does not apply to magic; Zulrah's and Seren's caps do
		noArmour.cap = t.cap;
		noArmour.damageCap = t.damageCap;
		int limit = hitLimit(t, StyleCalc.MAGIC, false, "earth".equals(element));
		// Ahrim's set with the Amulet of the damned: a quarter of the spells deal 30% more, rounded down, before a hit of 0
		// becomes 1 (the calculator's)
		boolean ahrims = l.ahrimsSet && n.startsWith("ahrim's staff");
		Scale ahrimsHit = perHit.first(13, 10);
		if (t.oneHit)
		{
			// a hit kills it: every cast deals its hitpoints
			return (max > 0 ? monsterSide(t.hitpoints, noArmour, perHit, 0) : 0) / speed;
		}
		if (t.alwaysMaxMagic)
		{
			// every cast that lands is at its highest hit: the staff's 8 more, the second hit of the Twinflame staff
			// included. On the Royal Titans' elementals a cast lands with the magic attack bonus / 100 + 0.3 (the
			// calculator's accuracy for them; its damage figure takes every cast as landing, which the bonus reaches at 70)
			long top = sanguinesti ? max + 8 : twinflame ? Math.max(1, max) + Math.max(1, max) * 4 / 10 : max;
			double lands = 1;
			if (t.titanElemental)
			{
				lands = Math.min(1.0, Math.max(0, bonus) / 100.0 + 0.3);
				lands = l.voidMagic > 0 ? Math.min(1.0, lands * 1.45) : lands;
			}
			double atTop = ahrims ? 0.75 * atMax(top, noArmour, perHit) + 0.25 * atMax(top, noArmour, ahrimsHit)
				: atMax(top, noArmour, perHit);
			return lands * atTop / speed;
		}
		// the Wardens of the second phase: every cast lands, between a part of the max and that part plus a fifth
		// the Respiratory systems: every hit at least half the max
		long[] range = t.wardenP2 ? wardenP2Range(max, roll, defenceRoll) : new long[]{t.leastHalfMax ? max / 2 : 0, max};
		long low = range[0];
		long high = range[1];
		double average = landed(low, high, noArmour, perHit, limit, false);
		if (ahrims)
		{
			average = 0.75 * average + 0.25 * landed(low, high, noArmour, ahrimsHit, limit, false);
		}
		if (sanguinesti)
		{
			// the calculator's rule for the staff: one hit in five deals 8 more, added before a hit of 0 becomes 1
			// and before Zulrah's cap
			double sum = 0;
			for (long h = low; h <= high; h++)
			{
				sum += 0.8 * landed(h, h, noArmour, perHit, limit, false) + 0.2 * landed(h + 8, h + 8, noArmour, perHit, limit, false);
			}
			average = high < low ? 0 : sum / (high - low + 1);
		}
		else if (twinflame)
		{
			// the second hit is made from the first once a hit of 0 has become 1, and is not raised itself
			double sum = 0;
			for (long h = low; h <= high; h++)
			{
				long first = Math.max(1, h);
				sum += landed(first, first, noArmour, perHit, limit, false) + monsterSide(first * 4 / 10, noArmour, perHit, limit);
			}
			average = high < low ? 0 : sum / (high - low + 1);
		}
		return (t.wardenP2 || t.everyAttackLands ? 1 : withDefenceless(t, StyleCalc.chance(roll, defenceRoll))) * average / speed;
	}

	/** A hit as the monster's own rules take it, with no raise of a 0 to 1: its scaling, then {@link #takes}. */
	static double monsterSide(long hit, Target t, Scale scale, int limit)
	{
		return takes(scale.monsters(hit), t, limit, false);
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
		if (lowerName.startsWith("crystal staff (") || lowerName.startsWith("corrupted staff ("))
		{
			// the Gauntlet's staves: a max hit of their own by their tier (the calculator's)
			return lowerName.endsWith("(basic)") ? 23 : lowerName.endsWith("(attuned)") ? 31
				: lowerName.endsWith("(perfected)") ? 39 : 0;
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
