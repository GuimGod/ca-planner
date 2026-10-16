/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Writes a set of equipment as the text the Inventory Setups plugin imports ("Import setup"):
 * the equipment only, with an empty inventory for the player to fill. That plugin has no way
 * for another one to create a setup, so the text goes through the clipboard.
 */
public final class SetupText
{
	private static final int EQUIPMENT_SLOTS = 14;
	private static final int INVENTORY_SLOTS = 28;
	/** Inventory Setups does not take longer names. */
	private static final int MAX_NAME = 50;
	/** Where each equipment slot sits in the bank layout of a setup: head, amulet, body, legs, boots, ammunition, then cape, weapon, shield, gloves, ring. */
	private static final int[] LAYOUT_ORDER = {0, 2, 4, 7, 10, 13, -1, -1, 1, 3, 5, 9, 12, -1, -1, -1};
	private static final int LAYOUT_SIZE = 56;
	private static final int LAYOUT_COLUMNS = 8;

	private SetupText()
	{
	}

	/** The name a boss's setup is saved under, so the plugin can ask for it by name later. */
	public static String nameFor(String boss)
	{
		String name = "CA " + (boss == null ? "" : boss.replace('"', ' ').replace('\\', ' ').trim());
		return name.length() > MAX_NAME ? name.substring(0, MAX_NAME).trim() : name;
	}

	/**
	 * @param name          the setup's name
	 * @param itemIdBySlot  the item of each equipment slot index (0 head ... 13 ammunition)
	 */
	public static String of(String name, Map<Integer, Integer> itemIdBySlot)
	{
		return of(name, itemIdBySlot, Collections.emptyList());
	}

	/**
	 * @param inventory the inventory slot by slot, each as item id and quantity; what is left is empty
	 */
	public static String of(String name, Map<Integer, Integer> itemIdBySlot, List<int[]> inventory)
	{
		return of(name, itemIdBySlot, inventory, null);
	}

	/** @param notes the setup's notes; null for the planner's own */
	public static String of(String name, Map<Integer, Integer> itemIdBySlot, List<int[]> inventory, String notes)
	{
		return "{\"setup\":" + setup(name, itemIdBySlot, inventory, notes) + ",\"layout\":[" + layout(itemIdBySlot, inventory) + "]}";
	}

	/** The setup alone, as Inventory Setups keeps it. */
	public static String setup(String name, Map<Integer, Integer> itemIdBySlot)
	{
		return setup(name, itemIdBySlot, Collections.emptyList());
	}

	public static String setup(String name, Map<Integer, Integer> itemIdBySlot, List<int[]> inventory)
	{
		return setup(name, itemIdBySlot, inventory, null);
	}

	/** @param notes the setup's notes; null for the planner's own */
	public static String setup(String name, Map<Integer, Integer> itemIdBySlot, List<int[]> inventory, String notes)
	{
		StringBuilder out = new StringBuilder("{\"inv\":[");
		for (int i = 0; i < INVENTORY_SLOTS; i++)
		{
			int[] item = i < inventory.size() ? inventory.get(i) : null;
			out.append(i == 0 ? "" : ",");
			if (item == null || item[0] <= 0)
			{
				out.append("null");
			}
			else
			{
				out.append("{\"id\":").append(item[0]).append(item[1] > 1 ? ",\"q\":" + item[1] : "").append('}');
			}
		}
		out.append("],\"eq\":[");
		for (int slot = 0; slot < EQUIPMENT_SLOTS; slot++)
		{
			Integer id = itemIdBySlot.get(slot);
			out.append(slot == 0 ? "" : ",").append(id == null || id <= 0 ? "null" : "{\"id\":" + id + "}");
		}
		out.append("],\"name\":\"").append(name.replace("\\", " ").replace("\"", " "))
			.append("\",\"notes\":\"").append(notes != null ? notes.replace("\\", " ").replace("\"", " ")
				: inventory.isEmpty() ? "Picked by CA Planner from your bank. Add your own supplies."
				: "Picked by CA Planner from your bank. The inventory is a place to start from.").append('"')
			.append(",\"hc\":\"#FFFF0000\",\"hd\":false,\"fb\":true,\"uh\":false}");
		return out.toString();
	}

	/** The bank layout of the setup: item ids joined by commas, -1 for an empty place. */
	public static String layout(Map<Integer, Integer> itemIdBySlot)
	{
		return layout(itemIdBySlot, Collections.emptyList());
	}

	/**
	 * The equipment in the first two rows; under it the inventory, two rows at a time and going
	 * down each column before the next (first item on top, second under it, third beside the first).
	 */
	public static String layout(Map<Integer, Integer> itemIdBySlot, List<int[]> inventory)
	{
		int[] cells = new int[LAYOUT_SIZE];
		java.util.Arrays.fill(cells, -1);
		for (int i = 0; i < LAYOUT_ORDER.length; i++)
		{
			Integer id = LAYOUT_ORDER[i] >= 0 ? itemIdBySlot.get(LAYOUT_ORDER[i]) : null;
			cells[i] = id == null || id <= 0 ? -1 : id;
		}
		for (int i = 0; i < inventory.size() && i < INVENTORY_SLOTS; i++)
		{
			int within = i % (2 * LAYOUT_COLUMNS);
			int row = 2 + 2 * (i / (2 * LAYOUT_COLUMNS)) + within % 2;
			int cell = row * LAYOUT_COLUMNS + within / 2;
			if (cell < LAYOUT_SIZE && inventory.get(i)[0] > 0)
			{
				cells[cell] = inventory.get(i)[0];
			}
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < LAYOUT_SIZE; i++)
		{
			out.append(i == 0 ? "" : ",").append(cells[i]);
		}
		return out.toString();
	}

	/** The notes of a setup the player changed: so it is not taken for the planner's own, with what they do not own. */
	public static String changedNotes(List<String> notOwned)
	{
		return "Picked by CA Planner from your bank, changed by you." + (notOwned.isEmpty() ? ""
			: " Not in your bank: " + String.join(", ", notOwned) + ".");
	}

	/** The name of a task's own setup, when its rule gives it a set of its own; else the boss's. */
	public static String nameFor(String boss, int taskId)
	{
		if (taskId < 0)
		{
			return nameFor(boss);
		}
		String tail = " #" + taskId;
		String name = nameFor(boss);
		return (name.length() + tail.length() > MAX_NAME ? name.substring(0, MAX_NAME - tail.length()).trim() : name) + tail;
	}
}
