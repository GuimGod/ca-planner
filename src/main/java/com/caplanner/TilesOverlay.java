/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner;

import com.caplanner.core.Tiles;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Outlines the tiles of one boss, with what each is, while the player has asked for them in the
 * panel. Fixed tiles only: nothing here follows the fight.
 */
class TilesOverlay extends Overlay
{
	/** A tile further than this from the player is not drawn. */
	private static final int REACH = 40;

	private final Client client;
	private final CaPlannerPlugin plugin;

	@Inject
	TilesOverlay(Client client, CaPlannerPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		List<Tiles.Tile> tiles = plugin.tilesShown();
		Player me = client.getLocalPlayer();
		if (tiles.isEmpty() || me == null)
		{
			return null;
		}
		WorldView view = me.getWorldView();
		WorldPoint here = WorldPoint.fromLocalInstance(client, me.getLocalLocation());
		if (view == null || here == null)
		{
			return null;
		}
		for (Tiles.Tile tile : tiles)
		{
			if (tile.plane != here.getPlane() || Math.abs(tile.x - here.getX()) > REACH || Math.abs(tile.y - here.getY()) > REACH)
			{
				continue;
			}
			Color colour = new Color(tile.colour, true);
			// a fight inside an instance shows the same tile somewhere else in the scene
			for (WorldPoint shown : WorldPoint.toLocalInstance(view, new WorldPoint(tile.x, tile.y, tile.plane)))
			{
				LocalPoint at = LocalPoint.fromWorld(view, shown);
				if (at == null)
				{
					continue;
				}
				Polygon poly = Perspective.getCanvasTilePoly(client, at);
				if (poly != null)
				{
					OverlayUtil.renderPolygon(graphics, poly, colour, new BasicStroke(2));
				}
				if (!tile.label.isEmpty())
				{
					// in the language the player picked for the plugin
					String label = com.caplanner.ui.CaPlannerPanel.say(tile.label);
					Point text = Perspective.getCanvasTextLocation(client, graphics, at, label, 0);
					if (text != null)
					{
						OverlayUtil.renderTextLocation(graphics, text, label, colour);
					}
				}
			}
		}
		return null;
	}
}
