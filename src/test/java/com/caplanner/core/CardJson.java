package com.caplanner.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Development only: a gear card from the JSON the bench writes, for drawing it outside the game. */
public final class CardJson
{
	private static final Map<String, Integer> SLOT = new HashMap<>();

	static
	{
		String[] names = {"head", "cape", "neck", "weapon", "body", "shield", "", "legs", "", "hands", "feet", "", "ring", "ammo"};
		for (int i = 0; i < names.length; i++)
		{
			if (!names[i].isEmpty())
			{
				SLOT.put(names[i], i);
			}
		}
	}

	private CardJson()
	{
	}

	private static Bag.Line line(JsonObject o)
	{
		return new Bag.Line(o.get("id").getAsInt(), o.get("name").getAsString(), o.get("quantity").getAsInt(),
			o.has("slots") && o.get("slots").getAsInt() == 1 && o.get("quantity").getAsInt() > 1, o.get("needed").getAsBoolean(),
			o.get("owned").getAsBoolean(), o.get("why").getAsString());
	}

	private static List<Bag.Line> lines(JsonArray a)
	{
		List<Bag.Line> out = new ArrayList<>();
		for (JsonElement e : a)
		{
			if (!e.isJsonNull())
			{
				out.add(line(e.getAsJsonObject()));
			}
		}
		return out;
	}

	private static String text(JsonObject o, String key)
	{
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
	}

	public static GearView view(JsonObject c)
	{
		Map<Integer, Integer> ids = new HashMap<>();
		Map<Integer, String> names = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : c.getAsJsonObject("set").entrySet())
		{
			JsonObject piece = e.getValue().getAsJsonObject();
			ids.put(SLOT.get(e.getKey()), piece.get("id").getAsInt());
			names.put(SLOT.get(e.getKey()), piece.get("name").getAsString());
		}
		List<Bag.Line> bag = lines(c.getAsJsonArray("bag"));
		List<String> notes = new ArrayList<>();
		for (JsonElement e : c.getAsJsonArray("notes"))
		{
			notes.add(e.getAsString());
		}
		GearView v = GearView.ready(ids, names, c.get("style").getAsString(), c.get("dps").getAsDouble(),
			c.has("hp") ? c.get("hp").getAsInt() : 0, bag).with(lines(c.getAsJsonArray("worn")), notes);
		List<GearView.Part> parts = new ArrayList<>();
		for (JsonElement e : c.getAsJsonArray("parts"))
		{
			JsonObject p = e.getAsJsonObject();
			GearView.Part part = new GearView.Part(p.get("label").getAsString(), p.get("style").getAsString(), p.get("dps").getAsDouble(),
				lines(p.getAsJsonArray("carried")), text(p, "spell"), p.get("note").getAsString(), p.get("nothing").getAsBoolean(),
				text(p, "sharedWeapon"), text(p, "sharedWith"));
			part.best = text(p, "best");
			parts.add(part);
		}
		if (!parts.isEmpty() || !c.get("wornFor").getAsString().isEmpty())
		{
			v = v.withParts(c.get("wornFor").getAsString(), parts, bag);
		}
		return v;
	}

}
