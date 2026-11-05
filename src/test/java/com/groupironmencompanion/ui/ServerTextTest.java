package com.groupironmencompanion.ui;

import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ServerTextTest
{
	// A member name that would make Swing fetch a URL if it were rendered as HTML.
	private static final String HOSTILE = "<html><img src='http://example.invalid/x.png'>";

	@Test
	public void anOrdinaryLabelRendersHtml()
	{
		// The control: if this stops holding, the test below proves nothing.
		assertNotNull(new JLabel(HOSTILE).getClientProperty(BasicHTML.propertyKey));
	}

	@Test
	public void serverTextIsNeverRenderedAsHtml()
	{
		JLabel label = ServerText.label(HOSTILE);
		assertNull(label.getClientProperty(BasicHTML.propertyKey));
		assertEquals(HOSTILE, label.getText());
	}

	@Test
	public void aPlainLabelStaysPlainWhenTheTextChanges()
	{
		// MemberCard sets its text on every refresh.
		JLabel label = ServerText.plain(new JLabel());
		label.setText(HOSTILE);
		assertNull(label.getClientProperty(BasicHTML.propertyKey));
	}
}
