package com.groupironmencompanion.ui;

import javax.swing.JComponent;
import javax.swing.JLabel;

/**
 * Labels for text that comes from the group's server: member names, NPC names, activity lines.
 * <p>
 * Swing renders label text starting with {@code <html>} as HTML, and that HTML can load images
 * from any URL. Member and NPC names are whatever was uploaded with the group's token, so they
 * get plain text only.
 */
final class ServerText
{
	private ServerText()
	{
	}

	static JLabel label(String text)
	{
		// The flag has to be on before the text goes in, or the HTML is already parsed.
		JLabel label = plain(new JLabel());
		label.setText(text);
		return label;
	}

	static <T extends JComponent> T plain(T component)
	{
		component.putClientProperty("html.disable", Boolean.TRUE);
		return component;
	}
}
