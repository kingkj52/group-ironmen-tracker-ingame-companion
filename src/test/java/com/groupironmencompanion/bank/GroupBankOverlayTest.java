package com.groupironmencompanion.bank;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GroupBankOverlayTest
{
	private static FontMetrics metrics;

	private static final List<String> PARTS = Arrays.asList("812 stacks", "14.2M value", "3.1M alch profit");

	@BeforeClass
	public static void setUp()
	{
		Graphics2D graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
		metrics = graphics.getFontMetrics();
		graphics.dispose();
	}

	@Test
	public void showsEverythingWhenThereIsRoom()
	{
		assertEquals("812 stacks  ·  14.2M value  ·  3.1M alch profit",
			GroupBankOverlay.footerText(PARTS, metrics, 2000));
	}

	@Test
	public void dropsFromTheEndWhenTheWindowIsNarrow()
	{
		String twoParts = "812 stacks  ·  14.2M value";
		int justEnough = metrics.stringWidth(twoParts);
		assertEquals(twoParts, GroupBankOverlay.footerText(PARTS, metrics, justEnough));
	}

	@Test
	public void alwaysKeepsTheStackCount()
	{
		// Even when nothing fits, a footer with nothing in it is worse than one that runs over.
		assertEquals("812 stacks", GroupBankOverlay.footerText(PARTS, metrics, 1));
	}

	@Test
	public void neverDrawsPastTheWidthItWasGiven()
	{
		String first = PARTS.get(0);
		for (int width = metrics.stringWidth(first); width < 600; width += 7)
		{
			String text = GroupBankOverlay.footerText(PARTS, metrics, width);
			assertTrue("at " + width + "px: " + text, metrics.stringWidth(text) <= width);
		}
	}
}
