package com.caplanner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class LangTest
{
	private static Lang portuguese() throws Exception
	{
		try (BufferedReader in = reader("/com/caplanner/lang/pt.tsv"))
		{
			return Lang.read(in);
		}
	}

	@Test
	public void aLineIsSaidInTheOtherLanguageAndNamesStayAsTheyAre() throws Exception
	{
		Lang pt = portuguese();
		assertEquals("Buscar", pt.of("Search"));
		assertEquals("161 / 2697 pontos · 416 tasks liberadas", pt.of("161 / 2697 points · 416 tasks you can do now"));
		assertEquals("Banco: 1184 itens vistos", pt.of("Bank: 1184 items seen"));
		// several things in one line, each said on its own
		assertEquals("Hard · Número de kills · 3 pts · 40% fez", pt.of("Hard · Kill Count · 3 points · 40% did it"));
		assertEquals("9 dão agora · faltam 9 · 40 pts", pt.of("9 you can do now · 9 left · 40 pts"));
		assertEquals("Libera 3 tasks · 12 pts · ajuda em mais 4", pt.of("Opens 3 tasks · 12 points · helps with 4 more"));
		assertEquals("Magic · 9.4 de dano por segundo · 0:53 batendo", pt.of("Magic · 9.4 damage a second · 0:53 of hitting"));
		assertEquals("Ranged · 11.3 de dano por segundo com estas peças · lançando Fire Surge",
			pt.of("Ranged · 11.3 damage a second with these on · casting Fire Surge"));
		// what a line holds is said in the other language too, and a name is left alone
		assertEquals("· Termine Dragon Slayer II", pt.of("· Finish Dragon Slayer II"));
		assertEquals("· Slayer 87 (você tem 85)", pt.of("· Slayer 87 (you have 85)"));
		assertEquals("· Só em Slayer task: Hellhounds", pt.of("· Only on a Slayer task: Hellhounds"));
		assertEquals("Precisa vestir Mirror shield", pt.of("Needs Mirror shield to wear"));
		assertEquals("Precisa de Dark totem", pt.of("Needs Dark totem"));
		assertEquals("Necessário aqui e não está no seu banco. Rock hammer: pra dar o golpe final",
			pt.of("Needed here and not in your bank. Rock hammer: to finish it off"));
		assertEquals("Vestido pra: O escudo dela", pt.of("Worn for: Her shield"));
		assertEquals("PRA TROCAR EM: TOTEMS", pt.of("TO CHANGE INTO FOR: Totems").toUpperCase());
		assertEquals("PRA 30 MINUTOS", pt.of("FOR 30 minutes").toUpperCase());
		assertEquals("Salvo como \"CA Vorkath\". Abra o banco pra ver.", pt.of("Saved as \"CA Vorkath\". Open your bank to see it."));
		assertEquals("O limite é 1:05. Só pelo dano este set cabe, com 0:12 de folga pra todo o resto.",
			pt.of("The limit is 1:05. By damage alone this set fits, with 0:12 to spare for everything else."));
		// the game's own names and anything the table does not have stay as they are
		assertEquals("Abyssal whip", pt.of("Abyssal whip"));
		assertEquals("Dodging the Dragon", pt.of("Dodging the Dragon"));
		assertEquals("Kill Vorkath 5 times without taking any damage.", pt.of("Kill Vorkath 5 times without taking any damage."));
		assertEquals("", pt.of(""));
	}

	@Test
	public void englishChangesNothing() throws Exception
	{
		Lang none = Lang.none();
		assertTrue(none.isNone());
		assertEquals("Search", none.of("Search"));
		Lang small = Lang.read(new StringReader("# c\nSearch\tBuscar\nbad line\nFinish {}\tTermine {1}\n\tx\n"));
		assertFalse(small.isNone());
		assertEquals("Buscar", small.of("Search"));
		assertEquals("Termine X", small.of("Finish X"));
		assertEquals("Start X", small.of("Start X"));
	}

	/** Every line the table of Portuguese words has is well formed, and every task has its objective. */
	@Test
	public void theBundledTablesAreSound() throws Exception
	{
		int lines = 0;
		try (BufferedReader in = reader("/com/caplanner/lang/pt.tsv"))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				if (line.trim().isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] c = line.split("\t", -1);
				assertEquals(line, 2, c.length);
				assertFalse(line, c[0].isEmpty() || c[1].isEmpty());
				// what is put back was found: no "{2}" without a second "{}"
				int holes = c[0].split("\\{\\}", -1).length - 1;
				for (int n = 1; n <= 9; n++)
				{
					assertEquals(line, n <= holes, c[1].contains("{" + n + "}"));
				}
				lines++;
			}
		}
		assertTrue(lines > 150);
		Set<Integer> ids = new HashSet<>();
		try (BufferedReader in = reader("/com/caplanner/tasks.tsv"))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				String[] c = line.split("\t");
				if (c.length > 1 && c[0].matches("\\d+"))
				{
					ids.add(Integer.parseInt(c[0]));
				}
			}
		}
		HowTo objectives;
		try (BufferedReader in = reader("/com/caplanner/lang/pt-tasks.tsv"))
		{
			objectives = HowTo.read(in);
		}
		assertEquals(ids.size(), objectives.size());
		for (int id : ids)
		{
			assertEquals("task " + id, 1, objectives.of(id).size());
		}
		assertEquals("Mate um Aberrant Spectre.", objectives.of(0).get(0));
	}

	private static BufferedReader reader(String resource)
	{
		return new BufferedReader(new InputStreamReader(LangTest.class.getResourceAsStream(resource), StandardCharsets.UTF_8));
	}
}
