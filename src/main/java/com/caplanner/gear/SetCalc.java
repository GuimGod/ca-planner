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
 * are not. Dizana's quiver charged, the blessed one and Dizana's max cape add 10 ranged accuracy and 1 ranged strength
 * to the arrows and bolts the weapon shoots ({@link #QUIVER_ACCURACY}; not to javelins, as the OSRS Wiki says and the
 * calculator does not). Not counted: anything a monster does during the fight.
 */
final class SetCalc
{
	/**
	 * What Dizana's quiver adds to the arrows and bolts the weapon shoots: the calculator's calculateEquipmentBonusesFromGear
	 * (its cape "Dizana's max cape", "Blessed Dizana's quiver" or the charged "Dizana's quiver", and ammunition of the
	 * weapon's list) and the OSRS Wiki (Blessed Dizana's quiver: "an additional +1 Ranged Strength and +10 Ranged accuracy
	 * is applied to stored arrows and bolts"; Dizana's max cape: "retains the perks of the blessed Dizana's quiver").
	 */
	static final int QUIVER_ACCURACY = 10;
	static final int QUIVER_STRENGTH = 1;

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
		/** The Tome of Fire is charged with searing pages: a fire spell's least hit is a tenth of its max before the tome. */
		boolean searingPages;
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
		/**
		 * Dizana's quiver charged, the Blessed Dizana's quiver or Dizana's max cape worn ({@link SetEffects#dizanaQuiver}):
		 * with arrows or bolts shot ({@link #ammo}), {@link #QUIVER_ACCURACY} and {@link #QUIVER_STRENGTH} more.
		 */
		boolean dizanaQuiver;
		/** Efaritay's aid worn: a vampyre of tier 2 takes damage from any weapon. */
		boolean efaritay;
		/** The Confliction gauntlets worn: with a weapon of one hand, magic rolls its accuracy their way. */
		boolean conflictionGauntlets;
		/**
		 * The element of the amulet worn that adds 2 to the base max hit of a spell of its element ("fire" for the Amulet
		 * of fire), "all" for the Elemental amulet, null for none (the calculator's hasMatchingElementalAmulet).
		 */
		String elementalAmulet;
		/** The weapon takes both hands (the game's item data). */
		boolean twoHanded;
		/** The Blood moon set with the Dual macuahuitl: its attacks come sooner, which this class does not count. */
		boolean bloodMoonSet;
		/**
		 * The stab, slash, crush and ranged defence bonuses of everything worn, added up: Dinh's bulwark's strength
		 * ({@link #bulwarkStrength}).
		 */
		int defence;
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
		/** Zeal unlocked and usable ({@link ArchiveItems#ZEAL_ATTACK}): it takes Piety's place. */
		boolean zeal;
		boolean chivalry = true;
		boolean rigour = true;
		boolean deadeye = true;
		boolean augury = true;
		boolean vigour = true;
		/** The boosted Mining level: a pickaxe's damage on the Guardians of the Chambers of Xeric grows with it. */
		int mining = 99;
		/** The elements ("fire", "water", "earth", "air") whose fragment of the Fractured Archive the player broke. */
		final java.util.Set<String> fragments = new java.util.HashSet<>();
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
		/** Taken off every magic hit that lands: Bork's, by the Smorks' health ({@link BorkArmour}). */
		int magicResistance;
		/**
		 * What cannot hurt it: 'M' no melee at all, 'f' melee only with a polearm (it flies or stays out of
		 * reach), 'r' no ranged, 'g' no magic, 'p' nothing but melee with a pickaxe (the Guardians of the
		 * Chambers of Xeric), 'b' nothing but a demonbane weapon (the Doom of Mokhaiotl's shield), 'i' nothing but
		 * ranged with Ice arrows on (the Fire Warrior of Lesarkus), 'w' no magic but a water spell (Fareed), 'a' no Ancient
		 * Magicks (the Archive Guardian).
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
		// the Diamond ascension bolts (e): the diamond's effect, half as often ({@link #withBolt})
		return itemName.startsWith("Diamond bolts") || itemName.startsWith("Diamond dragon bolts")
			|| itemName.startsWith("Diamond ascension bolts") ? DIAMOND : 0;
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
		return withBolt(bolt, zaryte, false, p, t, max, normal, diamondScale, rubyScale, limit);
	}

	/**
	 * The same, the effect going off half as often with {@code halved}: the Ascension crossbows (the Fractured Archive's
	 * first rewards proposal, its infographic of the crossbows: the bolts' effect chance halved).
	 * The effect itself is taken as the diamond's, Armour Piercing (115% of the max hit, not missing): the Rewards Poll Blog
	 * of 2026-07-30 calls them "the armour-piercing Diamond bolts"; the wiki's page of the Diamond ascension bolts (e) does
	 * not give the numbers. The Kandarin diary's tenth is taken on the halved chance, as on any bolts.
	 */
	static double withBolt(int bolt, boolean zaryte, boolean halved, Player p, Target t, long max, double normal,
		Scale diamondScale, Scale rubyScale, int limit)
	{
		double diary = (p.kandarinDiary ? 1.1 : 1.0) * (halved ? 0.5 : 1.0);
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

	/**
	 * Weapons with a rule of their own that is not modelled here: no figure is shown for them. Among them the rat bone
	 * weapons, which attack nothing but rats (OSRS Wiki, Bone mace: "cannot be used against any other type of
	 * monster"; the table does not say which monsters are rats); the Dark bow (bh), which attacks only in Bounty
	 * Hunter's areas and a few minigames (OSRS Wiki, Dark bow (bh)); and the Drygore blowpipe of the leagues, with two
	 * accuracy rolls the calculator does not have (OSRS Wiki, Drygore blowpipe).
	 */
	private static final String[] NOT_PLAIN = {
		"purging staff", "thammaron", "accursed", "soulreaper", "verac",
		"eclipse atlatl", "hunter's spear", "salamander", "swamp lizard", "chinchompa",
		"barronite", "blisterwood", "ivandis", "wilderness", "gadderhammer",
		"holy water", "ballista", "bone staff", "dawnbringer", "starter",
		"bone mace", "bone shortbow", "dark bow (bh)", "drygore blowpipe",
	};

	private SetCalc()
	{
	}

	/** Dinh's bulwark, and its dress the Dinh's blazing bulwark. */
	static boolean isBulwark(String weaponName)
	{
		String n = weaponName == null ? "" : weaponName.toLowerCase(Locale.ROOT);
		return n.startsWith("dinh's bulwark") || n.startsWith("dinh's blazing bulwark");
	}

	/**
	 * The strength Dinh's bulwark adds in its Pummel style, the one that attacks (its Block style has none): the stab,
	 * slash, crush and ranged defence bonuses worn, less 800, over 12, less 38, rounded toward 0 and never below 0
	 * (OSRS Wiki, Dinh's bulwark, "Strength buff"; the calculator's Equipment.ts, the same sum). 0 for any other weapon.
	 */
	static int bulwarkStrength(Loadout l)
	{
		return isBulwark(l.weaponName) ? Math.max(0, (l.defence - 800) / 12 - 38) : 0;
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
		return withBestPrayer(p, combatClass);
	}

	/**
	 * The player with the best attack prayer they have for a combat class turned on, whatever is on now: Zeal, Piety or
	 * Chivalry, Rigour or the best ranged prayer of the level, Augury or the best magic one. The other classes'
	 * prayers stay as they are (they do nothing to this class's figure).
	 */
	static Player withBestPrayer(Player p, String combatClass)
	{
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
		q.zeal = p.zeal;
		q.chivalry = p.chivalry;
		q.rigour = p.rigour;
		q.deadeye = p.deadeye;
		q.augury = p.augury;
		q.vigour = p.vigour;
		q.mining = p.mining;
		int level = p.prayerLevel;
		if (StyleCalc.MELEE.equals(combatClass))
		{
			// Zeal, Piety, Chivalry, or the two best single prayers of the level
			q.prayerAttack = p.zeal ? ArchiveItems.ZEAL_ATTACK : p.piety ? 120 : p.chivalry ? 115
				: level >= 34 ? 115 : level >= 16 ? 110 : level >= 7 ? 105 : 100;
			q.prayerStrength = p.zeal ? ArchiveItems.ZEAL_STRENGTH : p.piety ? 123 : p.chivalry ? 118
				: level >= 31 ? 115 : level >= 13 ? 110 : level >= 4 ? 105 : 100;
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
		if (ArchiveItems.noFigure(n))
		{
			// the Obligator without charges: the OSRS Wiki does not say whether it attacks. Zorya's tome: its max hit by the
			// Magic level is not published (ArchiveItems.isTome). No figure for either
			return false;
		}
		if (n.startsWith("dual macuahuitl") && l.bloodMoonSet)
		{
			// with the Blood moon set an attack can come a tick sooner (the calculator's weapon delay), not counted here
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
		if (cls == null || l.speed <= 0 || Weapons.cannotAttackUncharged(l.weaponName)
			|| ArchiveItems.noFigure(l.weaponName == null ? "" : l.weaponName.toLowerCase(Locale.ROOT)))
		{
			// no figure, not even a lenient one: the Obligator uncharged, Zorya's tome (ArchiveItems.noFigure)
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
			// Fareed takes magic only from a water spell: a powered staff casts none; the Archive Guardian takes no
			// Ancient Magicks (OSRS Wiki, Archive Guardian: "It is immune to Ancient Magicks")
			boolean casts = staffBase(n, 99) == 0;
			boolean water = casts && "water".equals(SetEffects.autocastElement(l.spell));
			boolean ancient = casts && l.spell >= 31 && l.spell <= 46;
			return immune.contains("g") || immune.contains("w") && !water || immune.contains("a") && ancient ? 0
				: magic(l, p, t, lenient, scale);
		}
		if (immune.contains("M") || (immune.contains("f") && !"Polearm".equals(l.category)))
		{
			return 0;
		}
		return melee(l, p, t, scale);
	}

	/**
	 * The figure of one style of the weapon alone, per tick ({@link #perTick} with that style only): the attack style
	 * notice of a weapon whose styles differ in more than the damage type (the Obligator's Thrust and Smash, in speed and
	 * in the Thrust's rolls), so that the notice and the figures agree on every monster's rules.
	 */
	static double styleFigure(Loadout l, Player p, Target t, StyleData.Style style)
	{
		List<StyleData.Style> all = l.styles;
		l.styles = Collections.singletonList(style);
		try
		{
			return perTick(l, p, t, false);
		}
		finally
		{
			l.styles = all;
		}
	}

	/**
	 * {@link #styleFigure} with the other monsters the Obligator's Thrust reaches too ({@link ArchiveItems#obligatorReach}),
	 * each by its own rules: the Thrust's figure is the sum on all of them, the Smash's the one fought's alone ("only
	 * being able to hit one target", OSRS Wiki, Obligator).
	 */
	static double styleFigure(Loadout l, Player p, Target t, List<Target> alsoHit, StyleData.Style style)
	{
		double figure = styleFigure(l, p, t, style);
		if (!ArchiveItems.smash(style.name))
		{
			for (Target other : alsoHit)
			{
				figure += styleFigure(l, p, other, style);
			}
		}
		return figure;
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
		/** The least a hit that landed is raised to after the player's own multipliers: 1, 3 with Seeking arrows shot. */
		final int least;

		Scale(int numerator, int divisor, int floor, boolean playerSide)
		{
			this(numerator, divisor, floor, playerSide, new int[0], 0);
		}

		Scale(int numerator, int divisor, int floor, boolean playerSide, int[] before, int most)
		{
			this(numerator, divisor, floor, playerSide, before, most, 1);
		}

		Scale(int numerator, int divisor, int floor, boolean playerSide, int[] before, int most, int least)
		{
			this.numerator = numerator;
			this.divisor = divisor;
			this.floor = floor;
			this.playerSide = playerSide;
			this.before = before;
			this.most = most;
			this.least = least;
		}

		/**
		 * The same with a hit that landed raised to at least {@code least} instead of 1: Seeking arrows, whose hits that
		 * land deal at least 3 (the calculator raises them after the player's own multipliers, before the raise to 1 and
		 * the monster's side).
		 */
		Scale raisedTo(int least)
		{
			return new Scale(numerator, divisor, floor, playerSide, before, most, Math.max(this.least, least));
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
			return new Scale(this.numerator, this.divisor, floor, playerSide, b, most, least);
		}

		/** True when it changes no hit. */
		boolean plain()
		{
			return numerator == divisor && floor == 0 && before.length == 0 && most == 0 && least == 1;
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

		/** A hit rolled that landed: the player's multipliers, the raise of a 0 to 1 (or to {@link #least}), the monster's side. */
		long landed(long rolled)
		{
			long h = rolled;
			for (int i = 0; i + 1 < before.length; i += 2)
			{
				h = h * before[i] / before[i + 1];
			}
			h = playerSide ? Math.max(least, main(h)) : main(Math.max(least, h));
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
			return playerSide ? new Scale(1, 1, 0, false, new int[0], most, least)
				: before.length == 0 ? this : new Scale(numerator, divisor, floor, false, new int[0], most, least);
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

	/**
	 * Mean damage of a hit rolled evenly from min to max that landed and was tripled, as the keris's hit in 51, after
	 * the monster's flat armour and cap: the calculator triples the hit (raised to 1 and scaled) before them.
	 */
	static double tripled(long min, long max, Target t, Scale scale)
	{
		if (max < min)
		{
			return 0;
		}
		double sum = 0;
		for (long rolled = min; rolled <= max; rolled++)
		{
			long h = scale.plain() ? Math.max(1, rolled) : scale.landed(rolled);
			sum += takes(3 * h, t, 0, false);
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

	/**
	 * How much more the gear worn deals to the target with the best attack prayer the player has for the weapon's
	 * class than with the prayers on now, in percent; NaN when there is no figure. Against a target whose defence is
	 * low for the class the accuracy is near the top already and the prayer adds little (Augury's +25% accuracy and
	 * +4% damage can come to a couple of percent); against a high one it adds much more.
	 */
	static double prayerGain(Loadout l, Player p, Target t)
	{
		String cls = combatClass(l);
		if (cls == null)
		{
			return Double.NaN;
		}
		return gain(perTick(l, p, t, true), perTick(l, withBestPrayer(p, cls), t, true));
	}

	/**
	 * Dinh's bulwark's special attack, Shield Bash, on one monster (OSRS Wiki, Dinh's bulwark, "Special attack"): an
	 * attack of the Pummel style, crush, rolled with 20% more accuracy against the monster's crush defence, at the
	 * bulwark's usual max hit. Returns {mean damage of one hit, max hit}, after the monster's own rules (flat armour),
	 * or null when the weapon is not a bulwark or the monster takes nothing from it. The monster attacked takes two of
	 * these hits and every other monster hit one ({@link #BULWARK_SPEC_TARGETS}); the calculator has no figure for this
	 * special attack (its UNIMPLEMENTED_SPECS lists it).
	 */
	static double[] bulwarkSpec(Loadout l, Player p, Target t)
	{
		if (!isBulwark(l.weaponName) || perTick(l, p, t, false) <= 0)
		{
			return null;
		}
		StyleCalc.Input in = new StyleCalc.Input();
		in.attackLevel = p.attack;
		in.strengthLevel = p.strength;
		in.prayerAttack = p.prayerAttack;
		in.prayerStrength = p.prayerStrength;
		in.stab = l.stab;
		in.slash = l.slash;
		in.crush = l.crush;
		in.strength = l.strength + bulwarkStrength(l);
		in.defenceLevel = t.defenceLevel;
		in.defenceStab = t.stab;
		in.defenceSlash = t.slash;
		in.defenceCrush = t.crush;
		in.alwaysHit = t.wardenP2 || t.everyAttackLands;
		in.defencelessShare = t.defencelessShare;
		in.toa = t.toa;
		in.defenceRollInvocation = t.toaInvocation;
		in.voidMelee = l.voidMelee > 0;
		in.inquisitor = l.inquisitor;
		int[] bonus = headOrNeck(l, t, StyleCalc.MELEE);
		in.bonusNumerator = bonus[0];
		in.bonusDenominator = bonus[1];
		// "20% increased accuracy": the attack roll times 6/5, rounded down
		in.accuracyNumerator = 6;
		in.accuracyDenominator = 5;
		double[] cm = StyleCalc.chanceAndMax(in, StyleCalc.CRUSH, "Accurate");
		long max = (long) cm[1];
		return new double[]{cm[0] * landed(0, max, t), takes(Math.max(1, max), t, 0, false)};
	}

	/** Shield Bash hits up to this many monsters in a multicombat area, within 5 tiles of the player (11 by 11). */
	static final int BULWARK_SPEC_TARGETS = 10;
	/** How far from the player Shield Bash reaches, in tiles. */
	static final int BULWARK_SPEC_REACH = 5;
	/** Its cost, in tenths of a percent of special attack energy (the game's varp): 50%. */
	static final int BULWARK_SPEC_COST = 500;

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
		in.strength = l.strength + bulwarkStrength(l);
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
			// keeps its 70%; the wiki decides)
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
		// the Dual macuahuitl: two hits, up to half the max each; the second only when the first lands (the calculator's)
		boolean dualMacuahuitl = n.startsWith("dual macuahuitl");
		// Araxxor's araxytes: the Noxious halberd, and a crush style while crush is the highest attack bonus worn
		String highest = highestBonus(l.stab, l.slash, l.crush, l.ranged, l.magic);
		// the Scythe on Xarpus and Verzik's second phase: one attack every 16/3 ticks, as the fight is played
		double weaponSpeed = t.scytheSpeed > 0 && n.contains("scythe of vitur") ? t.scytheSpeed : l.speed;
		// the Obligator: its Thrust rolls the damage more than once, larger monsters take more of it; its Smash is faster
		boolean obligator = ArchiveItems.isObligator(n);
		// the best damage per tick so far (the styles of one weapon can differ in speed: the Obligator's)
		double best = -1;
		for (StyleData.Style s : l.styles)
		{
			if (!StyleCalc.melee(s.type))
			{
				continue;
			}
			boolean thrust = obligator && !ArchiveItems.smash(s.name);
			double speed = obligator && !thrust ? ArchiveItems.OBLIGATOR_SMASH_SPEED : weaponSpeed;
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
				best = better(best, (top > 0 ? monsterSide(t.hitpoints, t, scale, 0) : 0) / speed, null, speed);
				continue;
			}
			if (t.demonbaneKills && demonbaneWeapon(n, StyleCalc.MELEE))
			{
				// a demonbane weapon's hit that lands kills it
				best = better(best, cm[0] * monsterSide(t.hitpoints, t, scale, 0) / speed, null, speed);
				continue;
			}
			if (t.araxyteMax && araxyteMaxMelee(n, s.type, highest))
			{
				// every attack lands at the max hit, a single hit whatever the weapon (the calculator returns before the
				// Scythe's other hits, the two-hit weapons' split and Dharok's set)
				best = better(best, atMax(top, t, scale) / speed, null, speed);
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
				// the Obligator's Thrust: the larger monster's share more on that hit
				best = better(best, atMax(all, t, thrust ? obligatorSized(scale, t) : scale) / speed, null, speed);
				continue;
			}
			if (thrust)
			{
				// the best of its damage rolls, each the larger monster's share more, after one roll of accuracy
				int rolls = ArchiveItems.obligatorRolls(t.stab, t.slash, t.crush);
				Scale sized = obligatorSized(scale, t);
				double value = cm[0] * bestOfRolls(top, rolls, t, sized, limit, tailCrush) + (tailCrush ? 1 - cm[0] : 0);
				double[] damage = value / speed > best && listening() && !tailCrush && limit <= 0
					? withChanceOf(cm[0], bestOfRollsDamage(top, rolls, t, sized)) : null;
				best = better(best, value / speed, damage, speed);
				continue;
			}
			double average = dualMacuahuitl
				? landed(least, Math.max(least, max / 2), t, scale, limit, tailCrush)
					+ cm[0] * landed(least, Math.max(least, max - max / 2), t, scale, limit, tailCrush)
				: twoHits
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
			// the keris's hit in 51 is tripled before the monster's own rules take it (the calculator's order): a flat
			// armour comes off the tripled hit once, not three times (the Locust riders' -2 adds 2, not 6)
			double value = keris > 1 && hasHitRules(t)
				? cm[0] * (average * 50 / 51.0 + tripled(least, top, t, scale) / 51.0) + misses
				: cm[0] * average * keris + misses;
			// the damage as chances, while ticksToKill listens: not with the rules that make a hit random beyond its roll
			double[] damage = value / speed > best && listening() && keris == 1 && !tailCrush && limit <= 0 && !l.dharokSet
				? meleeDamage(cm[0], least, top, max, hits, twoHits, dualMacuahuitl, t, scale) : null;
			best = better(best, value / speed, damage, speed);
		}
		return best < 0 ? -1 : best;
	}

	/** A hit's scaling with the Obligator's Thrust's 20% or 40% more on a large monster first, rounded down. */
	static Scale obligatorSized(Scale scale, Target t)
	{
		int[] size = ArchiveItems.obligatorSize(t.size);
		return size[0] == size[1] ? scale : scale.first(size[0], size[1]);
	}

	/**
	 * The chance the best of {@code rolls} rolls, each evenly from 0 to {@code max}, is {@code k} (the Obligator's Thrust):
	 * ((k + 1)^rolls - k^rolls) / (max + 1)^rolls.
	 */
	static double bestRollChance(long k, long max, int rolls)
	{
		double all = Math.pow(max + 1, rolls);
		return (Math.pow(k + 1, rolls) - Math.pow(k, rolls)) / all;
	}

	/**
	 * Mean damage of the Obligator's Thrust once it has landed: the best of {@code rolls} damage rolls from 0 to {@code
	 * max}, that roll scaled as {@code scale} says (the larger monster's 20% or 40% first, each roll rounded down),
	 * raised to 1, and then taken by the monster's own rules, its flat armour once (off the final hit, once). One roll
	 * of accuracy for all the damage rolls: a premise the game's posts do not state.
	 */
	static double bestOfRolls(long max, int rolls, Target t, Scale scale, int limit, boolean zeroToOne)
	{
		double sum = 0;
		for (long k = 0; k <= max; k++)
		{
			sum += bestRollChance(k, max, rolls) * landed(k, k, t, scale, limit, zeroToOne);
		}
		return sum;
	}

	/** {@link #bestOfRolls} as chances, the damage of a landed Thrust; null where Zulrah's or Seren's cap makes it random. */
	static double[] bestOfRollsDamage(long max, int rolls, Target t, Scale scale)
	{
		if (t.cap > 0 || t.damageCap > 0)
		{
			return null;
		}
		int top = 0;
		for (long k = 0; k <= max; k++)
		{
			top = Math.max(top, dealt(k, t, scale));
		}
		double[] p = new double[top + 1];
		for (long k = 0; k <= max; k++)
		{
			p[dealt(k, t, scale)] += bestRollChance(k, max, rolls);
		}
		return p;
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

	/**
	 * Dizana's quiver charged worn and the weapon shooting arrows or bolts: the quiver's bonus counts. Only what the
	 * quiver can hold (OSRS Wiki, Dizana's quiver: "As the ammunition must be storable in the quiver, Dizana's Sunfire will
	 * not apply to other types of ammunition"; javelins cannot be stored). The calculator gives it to the Heavy ballista's
	 * javelins too (its ammunition list); here the wiki is followed.
	 */
	static boolean quiverApplies(Loadout l)
	{
		return l.dizanaQuiver && l.ammo != null && !Weapons.has(l.ammo, "bolt rack")
			&& (Weapons.has(l.ammo, "arrow") || Weapons.has(l.ammo, "bolts"));
	}

	/** The least a hit of Seeking arrows deals when it lands. */
	static final int SEEKING_LEAST = 3;

	/**
	 * True when the weapon shoots Seeking arrows (Sailing's arrows, "Seeking bronze arrow" to "Seeking dragon arrow" and
	 * Seeking broad arrows): each hit that lands deals at least 3 (the OSRS Wiki DPS calculator's getAttackerDist: "Math.max(h.damage, 3)"
	 * on the accurate hits, with the arrows the weapon fires). The loadout's ammunition is only ever what the weapon shoots.
	 */
	static boolean seekingArrows(Loadout l)
	{
		return l.ammo != null && l.ammo.startsWith("Seeking ");
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
		// Dizana's quiver on the arrows and bolts shot (the calculator adds it to the gear's bonuses)
		boolean quiver = quiverApplies(l);
		int rangedAttack = l.ranged + (quiver ? QUIVER_ACCURACY : 0);
		int rangedStrength = l.rangedStrength + (quiver ? QUIVER_STRENGTH : 0);
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
			long roll = effective * (rangedAttack + 64);
			long max = (effectiveStrength * (rangedStrength + 64) + 320) / 640;
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
				best = better(best, (max > 0 ? monsterSide(t.hitpoints, t, perHit, 0) : 0) / speed, null, speed);
				continue;
			}
			if (t.araxyteMax && "Crossbow".equals(c) && "ranged".equals(highestBonus(l.stab, l.slash, l.crush, rangedAttack, l.magic)))
			{
				// Araxxor's araxytes: a crossbow, while ranged is the highest attack bonus worn, lands every shot at its max
				// hit, with no bolt's effect (the calculator returns before them)
				best = better(best, atMax(max, t, perHit) / speed, null, speed);
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
				best = better(best, atMax(top, t, perHit) / speed, null, speed);
				continue;
			}
			// the Corporeal Beast halves the shot and a diamond bolt's effect, not a ruby bolt's (the calculator's order)
			Scale scale = corpHalves(t, StyleCalc.RANGED, false) ? CORP_HALF : perHit;
			if (seekingArrows(l))
			{
				scale = scale.raisedTo(SEEKING_LEAST);
			}
			int limit = hitLimit(t, StyleCalc.RANGED, false, false);
			// the Wardens of the second phase: every shot lands, between a part of the max and that part plus a fifth
			// the Respiratory systems: every hit at least half the max
			long[] range = t.wardenP2 ? wardenP2Range(max, roll, defenceRoll) : new long[]{t.leastHalfMax ? max / 2 : 0, max};
			double chance = t.wardenP2 || t.everyAttackLands ? 1 : withDefenceless(t, StyleCalc.chance(roll, defenceRoll));
			if (t.demonbaneKills && demonbaneWeapon(n, StyleCalc.RANGED))
			{
				// a demonbane weapon's shot that lands kills it, no bolt's effect or second hit counted (the calculator's)
				best = better(best, chance * monsterSide(t.hitpoints, t, scale, 0) / speed, null, speed);
				continue;
			}
			double shot = chance * landed(range[0], range[1], t, scale, limit, false) * shots;
			if (bolts)
			{
				shot = withBolt(l.bolt, zaryte, ArchiveItems.ascensionCrossbows(l.weaponName), p, t, range[1], shot, scale,
					perHit, limit);
			}
			// the damage as chances, while ticksToKill listens: each shot rolled on its own; not with a bolt's effect
			double[] damage = shot / speed > best && listening() && !bolts && limit <= 0
				? shots(chance, landedDamage(range[0], range[1], t, scale), shots) : null;
			best = better(best, shot / speed, damage, speed);
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
		if (element != null && ("all".equals(l.elementalAmulet) || element.equals(l.elementalAmulet)))
		{
			// the Elemental amulet, or the amulet of the spell's element: 2 more on the spell's base, before the weakness
			// takes its share of it (the calculator's, 2026-10-26)
			base += 2;
		}
		if (element != null && p.fragments.contains(element))
		{
			// an elemental fragment the player broke: 2 more on the base of the spells of its element, everywhere, before
			// the magic damage and the weakness (OSRS Wiki, Elemental fragments)
			base += 2;
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
		// the Tome of Fire charged with searing pages: a fire spell's least hit is a tenth of its max before the tome's
		// tenth (OSRS Wiki, Searing page; the calculator's sunfire runes)
		long least = "fire".equals(element) && "fire".equals(l.tome) && l.searingPages ? max / 10 : 0;
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
		// flat armour does not apply to magic; Zulrah's and Seren's caps do, and a magic resistance as flat armour does
		Target noArmour = new Target();
		noArmour.cap = t.cap;
		noArmour.damageCap = t.damageCap;
		noArmour.flatArmour = t.magicResistance;
		int limit = hitLimit(t, StyleCalc.MAGIC, false, "earth".equals(element));
		// Ahrim's set with the Amulet of the damned: a quarter of the spells deal 30% more, rounded down, before a hit of 0
		// becomes 1 (the calculator's)
		boolean ahrims = l.ahrimsSet && n.startsWith("ahrim's staff");
		Scale ahrimsHit = perHit.first(13, 10);
		if (t.oneHit)
		{
			// a hit kills it: every cast deals its hitpoints
			return better(-1, (max > 0 ? monsterSide(t.hitpoints, noArmour, perHit, 0) : 0) / speed, null, speed);
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
			return better(-1, lands * atTop / speed, null, speed);
		}
		// the Wardens of the second phase: every cast lands, between a part of the max and that part plus a fifth
		// the Respiratory systems: every hit at least half the max
		long[] range = t.wardenP2 ? wardenP2Range(max, roll, defenceRoll) : new long[]{least + (t.leastHalfMax ? max / 2 : 0), max};
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
		double chance = t.wardenP2 || t.everyAttackLands ? 1 : withDefenceless(t, magicChance(l, roll, defenceRoll));
		// the damage as chances, while ticksToKill listens: the Sanguinesti staff's 8 more in one hit of five included
		double[] chances = !listening() || ahrims || twinflame || limit > 0 ? null
			: sanguinesti ? withChanceOf(chance, sanguinestiDamage(low, high, noArmour, perHit))
			: withChanceOf(chance, landedDamage(low, high, noArmour, perHit));
		return better(-1, chance * average / speed, chances, speed);
	}

	/**
	 * The chance a cast lands: with the Confliction gauntlets and a weapon of one hand (the Eye of Ayak, the Sanguinesti
	 * staff, the Kodai wand; not Tumeken's shadow), the calculator's roll for them (PlayerVsNPCCalc.getHitChance:
	 * "wearing('Confliction gauntlets') && style.type === 'magic' && !weapon.isTwoHanded"), the standard one otherwise.
	 */
	static double magicChance(Loadout l, long roll, long defenceRoll)
	{
		return l.conflictionGauntlets && !l.twoHanded ? StyleCalc.conflictionChance(roll, defenceRoll)
			: StyleCalc.chance(roll, defenceRoll);
	}

	/**
	 * One attack's damage as chances (index: the damage, all its hitsplats summed), with the ticks between attacks, as
	 * {@link #perTick} counts the attack: the best style's.
	 */
	static final class Attack
	{
		final double[] damage;
		final double speed;

		Attack(double[] damage, double speed)
		{
			this.damage = damage;
			this.speed = speed;
		}
	}

	/** What the attack counted is, while {@link #attack} listens to {@link #perTick}. */
	private static final class Listener
	{
		double[] damage;
		double speed;
	}

	private static final ThreadLocal<Listener> LISTENER = new ThreadLocal<>();

	private static boolean listening()
	{
		return LISTENER.get() != null;
	}

	/**
	 * The larger of the best figure so far and {@code value}; while {@link #attack} listens, the damage of the attack the
	 * larger one counts ({@code damage}, null for an attack whose damage is not put as chances here).
	 */
	private static double better(double best, double value, double[] damage, double speed)
	{
		if (value > best)
		{
			Listener listener = LISTENER.get();
			if (listener != null)
			{
				listener.damage = damage;
				listener.speed = speed;
			}
			return value;
		}
		return best;
	}

	/**
	 * The damage of one attack of the figure {@link #perTick} gives, as chances, or null when it is not put as chances:
	 * a bolt's effect, Dharok's set, the keris on a kalphite, the limits of Verzik's first phase and of the Hueycoatl's
	 * tail, Zulrah's and Seren's caps, the Twinflame staff, Ahrim's set, the monsters a hit kills or every attack lands at
	 * the max on. The chances must give the figure itself (their mean over the ticks between attacks, to a thousandth):
	 * a rule this left out would not.
	 */
	static Attack attack(Loadout l, Player p, Target t)
	{
		Listener listener = new Listener();
		LISTENER.set(listener);
		double figure;
		try
		{
			figure = perTick(l, p, t, false);
		}
		finally
		{
			LISTENER.remove();
		}
		if (figure <= 0 || listener.damage == null || listener.speed <= 0)
		{
			return null;
		}
		double mean = KillTime.mean(listener.damage) / listener.speed;
		if (Math.abs(mean - figure) > 0.001 * figure)
		{
			return null;
		}
		return new Attack(listener.damage, listener.speed);
	}

	/**
	 * The mean ticks the weapon takes to kill the monster from its full hitpoints (the calculator's getTtk, in ticks):
	 * the attacks it takes, each wasting what it deals beyond the hitpoints left, times the ticks between them. NaN when
	 * the attack's damage is not put as chances ({@link #attack}) or the monster has more than {@link
	 * KillTime#MOST_HITPOINTS}; infinite when no attack can deal anything.
	 */
	static double ticksToKill(Loadout l, Player p, Target t)
	{
		if (t.hitpoints <= 0 || t.hitpoints > KillTime.MOST_HITPOINTS)
		{
			return Double.NaN;
		}
		Attack a = attack(l, p, t);
		return a == null ? Double.NaN : KillTime.hitsToKill(a.damage, t.hitpoints) * a.speed;
	}

	/** {@link KillTime#withChance}, or null for no damage put as chances. */
	private static double[] withChanceOf(double chance, double[] landed)
	{
		return landed == null ? null : KillTime.withChance(chance, landed);
	}

	/** {@code shots} shots, each landing with {@code chance} on its own; null for no damage put as chances. */
	private static double[] shots(double chance, double[] landed, int shots)
	{
		double[] one = withChanceOf(chance, landed);
		double[] all = one;
		for (int i = 1; one != null && i < shots; i++)
		{
			all = KillTime.sum(all, one);
		}
		return all;
	}

	/**
	 * A melee attack's damage as chances: each hit lands on its own roll (the calculator's: the Scythe's hits, the two-hit
	 * weapons'), but the Dual macuahuitl's second only when its first lands.
	 */
	private static double[] meleeDamage(double chance, long least, long top, long max, int hits, boolean twoHits,
		boolean dualMacuahuitl, Target t, Scale scale)
	{
		if (dualMacuahuitl)
		{
			double[] first = landedDamage(least, Math.max(least, max / 2), t, scale);
			double[] second = withChanceOf(chance, landedDamage(least, Math.max(least, max - max / 2), t, scale));
			return first == null || second == null ? null : KillTime.withChance(chance, KillTime.sum(first, second));
		}
		if (twoHits)
		{
			double[] a = withChanceOf(chance, landedDamage(least, Math.max(least, max / 2), t, scale));
			double[] b = withChanceOf(chance, landedDamage(least, Math.max(least, max - max / 2), t, scale));
			return a == null || b == null ? null : KillTime.sum(a, b);
		}
		double[] damage = withChanceOf(chance, landedDamage(least, top, t, scale));
		for (int h = 1; damage != null && h < hits; h++)
		{
			double[] next = withChanceOf(chance, landedDamage(least, Math.max(least, max >> h), t, scale));
			damage = next == null ? null : KillTime.sum(damage, next);
		}
		return damage;
	}

	/** What a landed hit rolled as {@code rolled} deals: raised to 1 and scaled as {@link #landed} does, less flat armour. */
	private static int dealt(long rolled, Target t, Scale scale)
	{
		long h = scale.plain() ? Math.max(1, rolled) : scale.landed(rolled);
		return (int) Math.max(0, h - t.flatArmour);
	}

	/**
	 * The damage of a hit that lands, rolled evenly from {@code min} to {@code max}, as chances, as {@link #landed} takes
	 * it; null where Zulrah's or Seren's cap makes it random beyond the roll.
	 */
	static double[] landedDamage(long min, long max, Target t, Scale scale)
	{
		if (t.cap > 0 || t.damageCap > 0)
		{
			return null;
		}
		if (max < min)
		{
			return KillTime.certain(0);
		}
		int n = (int) (max - min + 1);
		int[] dealt = new int[n];
		int top = 0;
		for (int i = 0; i < n; i++)
		{
			dealt[i] = dealt(min + i, t, scale);
			top = Math.max(top, dealt[i]);
		}
		double[] p = new double[top + 1];
		for (int d : dealt)
		{
			p[d] += 1.0 / n;
		}
		return p;
	}

	/** The Sanguinesti staff's landed damage as chances: one hit in five 8 more, before it is raised and scaled. */
	static double[] sanguinestiDamage(long min, long max, Target t, Scale scale)
	{
		if (t.cap > 0 || t.damageCap > 0)
		{
			return null;
		}
		if (max < min)
		{
			return KillTime.certain(0);
		}
		int n = (int) (max - min + 1);
		int top = 0;
		for (long h = min; h <= max; h++)
		{
			top = Math.max(top, Math.max(dealt(h, t, scale), dealt(h + 8, t, scale)));
		}
		double[] p = new double[top + 1];
		for (long h = min; h <= max; h++)
		{
			p[dealt(h, t, scale)] += 0.8 / n;
			p[dealt(h + 8, t, scale)] += 0.2 / n;
		}
		return p;
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
