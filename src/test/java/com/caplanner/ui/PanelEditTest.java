package com.caplanner.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import com.caplanner.core.GearView;
import com.caplanner.core.Planner;
import com.caplanner.core.Task;
import com.caplanner.core.TaskType;
import com.caplanner.core.Tier;
import com.caplanner.core.Verdict;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.Test;

/** Case 19: a change makes the boss's cards be worked out again, and only those; the old card shows until the new one comes. */
public class PanelEditTest
{
	@Test
	public void aChangeHasTheBossCardsWorkedOutAgainAndNoOthers() throws Exception
	{
		assumeFalse(GraphicsEnvironment.isHeadless());
		final List<String> asked = new ArrayList<>();
		final CaPlannerPanel[] panel = new CaPlannerPanel[1];
		SwingUtilities.invokeAndWait(() ->
		{
			panel[0] = new CaPlannerPanel((CaPlannerPanel.Actions) java.lang.reflect.Proxy.newProxyInstance(PanelEditTest.class.getClassLoader(),
				new Class<?>[]{CaPlannerPanel.Actions.class}, (proxy, method, a) ->
				{
					if ("wantGear".equals(method.getName()))
					{
						asked.add(a[0] + "@" + a[1]);
						return null;
					}
					if (method.isDefault())
					{
						return java.lang.invoke.MethodHandles.privateLookupIn(CaPlannerPanel.Actions.class, java.lang.invoke.MethodHandles.lookup())
							.unreflectSpecial(method, CaPlannerPanel.Actions.class).bindTo(proxy).invokeWithArguments(a);
					}
					Class<?> r = method.getReturnType();
					return r == boolean.class ? Boolean.FALSE : r == int.class ? 0 : r == String.class ? "" : r == List.class ? new ArrayList<>() : null;
				}));
			List<Task> tasks = new ArrayList<>();
			tasks.add(new Task(1, "A kill", "Kill Vorkath once.", Tier.EASY, TaskType.KILL_COUNT, "Vorkath"));
			tasks.add(new Task(2, "Another", "Kill Zulrah once.", Tier.EASY, TaskType.KILL_COUNT, "Zulrah"));
			panel[0].render(new Planner.State(tasks, new HashSet<Integer>(), new HashMap<Integer, Verdict>(), new HashMap<Integer, Double>()));
			panel[0].open(false, null, "Vorkath", -1);
		});
		SwingUtilities.invokeAndWait(() ->
		{
		});
		assertTrue(asked.toString(), asked.contains("Vorkath@0"));
		SwingUtilities.invokeAndWait(() ->
		{
			GearView ready = GearView.ready(new HashMap<Integer, Integer>(), new HashMap<Integer, String>(), "Melee", 10, 750);
			panel[0].gear("Vorkath", 0, ready);
			panel[0].gear("Zulrah", 0, ready);
			asked.clear();
			panel[0].forgetGearOf("Vorkath");
		});
		// the boss's card is asked for again, in the new generation; Zulrah's is kept
		assertEquals(asked.toString(), 1, asked.size());
		assertEquals("Vorkath@1", asked.get(0));
		SwingUtilities.invokeAndWait(() -> panel[0].open(false, null, "Zulrah", -1));
		assertEquals("Zulrah's card was not forgotten: " + asked, 1, asked.size());
	}
}
