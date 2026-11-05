package com.groupironmencompanion;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Enforces the promises the README makes about this plugin, so they cannot quietly regress.
 * <p>
 * The plugin's whole case for being publishable is that it is mechanically verifiable: it
 * adds no menu entries, never touches a game widget's state or geometry, uses no reflection,
 * and talks to exactly one configurable host. Each of those is a grep, so each is a test.
 */
public class ComplianceTest
{
	private static final Path SOURCE_ROOT = new File("src/main/java").toPath();

	/** A bare {@code client.} field access, not a package name and not {@code clientThread}. */
	private static final java.util.regex.Pattern BARE_CLIENT_ACCESS =
		java.util.regex.Pattern.compile("(?<![.\\w])client\\s*\\.");

	private static List<Path> sources() throws IOException
	{
		try (Stream<Path> paths = Files.walk(SOURCE_ROOT))
		{
			return paths.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
		}
	}

	/**
	 * Fails if any source file contains a banned token. Occurrences inside a {@code //} or
	 * {@code *} comment line are ignored so the rules can be discussed in documentation.
	 */
	private void assertAbsent(String reason, String... tokens) throws IOException
	{
		List<String> hits = new ArrayList<>();

		for (Path path : sources())
		{
			List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
			for (int i = 0; i < lines.size(); i++)
			{
				String line = lines.get(i);
				String trimmed = line.trim();
				if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*"))
				{
					continue;
				}
				for (String token : tokens)
				{
					if (line.contains(token))
					{
						hits.add(SOURCE_ROOT.relativize(path) + ":" + (i + 1) + "  " + trimmed);
					}
				}
			}
		}

		if (!hits.isEmpty())
		{
			fail(reason + "\n  " + String.join("\n  ", hits));
		}
	}

	@Test
	public void sourceTreeIsPresent() throws IOException
	{
		assertTrue("compliance tests must run from the project directory", Files.isDirectory(SOURCE_ROOT));
		assertTrue(sources().size() > 20);
	}

	@Test
	public void addsNoMenuEntries() throws IOException
	{
		// Jagex prohibit "any addition of new menu entries which cause actions to be sent to
		// the server". The plugin sidesteps the question entirely by creating none at all. The
		// one place it touches the menu, while the mouse is over the group bank window, only
		// removes the scene's options so nothing behind the window can be acted on.
		assertAbsent("This plugin must never create or invoke a menu entry.",
			"createMenuEntry", "addMenuEntry", "invokeMenuAction", "menuAction(", "setMenuOptions",
			"setOption(", "setTarget(", "setType(", "onClick(");
	}

	/**
	 * setMenuEntries is the only menu write allowed, and it can only remove entries since
	 * creating one is banned above. It must only happen over our own window though, or it
	 * would blank the game's menu everywhere. So there's exactly one call and it's gated.
	 */
	@Test
	public void theOnlyMenuWriteIsGatedOnTheWindow() throws IOException
	{
		List<String> sites = new ArrayList<>();
		for (Path path : sources())
		{
			List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
			for (int i = 0; i < lines.size(); i++)
			{
				if (lines.get(i).contains("setMenuEntries"))
				{
					String trimmed = lines.get(i).trim();
					if (trimmed.startsWith("//") || trimmed.startsWith("*"))
					{
						continue;
					}
					sites.add(SOURCE_ROOT.relativize(path) + ":" + (i + 1));

					// The guard must be visible in the same method, and the method is short.
					String window = String.join("\n",
						lines.subList(Math.max(0, i - 40), i));
					assertTrue("the menu write at " + SOURCE_ROOT.relativize(path) + ":" + (i + 1)
							+ " must be reached only while the cursor is over the plugin's window",
						window.contains("covers("));
					assertTrue("the menu write at " + SOURCE_ROOT.relativize(path) + ":" + (i + 1)
							+ " must leave an already-open menu alone",
						window.contains("isMenuOpen()"));
				}
			}
		}

		assertEquals("exactly one place may write the menu, so there is one place to audit",
			1, sites.size());
	}

	@Test
	public void neverModifiesAGameWidget() throws IOException
	{
		// Jagex prohibit unhiding interface components and moving or resizing click zones.
		// Widgets are read for their bounds only; nothing about them is ever written.
		assertAbsent("This plugin must never modify, hide, unhide, resize or reparent a game widget.",
			"setHidden", "createChild", "setOriginalX", "setOriginalY", "setOriginalWidth",
			"setOriginalHeight", "deleteAllChildren", "setOnOpListener", "setItemId",
			"setTextColor", "setSpriteId");
	}

	@Test
	public void neverWritesClientState() throws IOException
	{
		// Sprite overrides are how interface reskins work. The group bank reads them to match
		// a reskinned bank, and must never add, replace or remove one itself.
		assertAbsent("This plugin is read-only with respect to the game client.",
			"setVarbit", "setVarp", "runScript", "client.set", "getWidgetSpriteOverrides",
			"overrides.put", "overrides.remove", "overrides.clear", "getSpriteOverrides().put",
			"getSpriteOverrides().remove", "getSpriteOverrides().clear");
	}

	@Test
	public void drawsNothingOnTheMinimap() throws IOException
	{
		// Member positions on the minimap would make this a radar.
		assertAbsent("Group member data must never be drawn on the minimap.",
			"localToMinimap", "getMinimapLocation", "worldToMinimap");
	}

	/** Only scene presence draws in the scene, and it all lives in scene/, so there's one place to audit. */
	@Test
	public void sceneDrawingIsConfinedToTheScenePackage() throws IOException
	{
		String[] sceneTokens = {
			"LocalPoint", "Perspective.", "RuneLiteObject", "ABOVE_SCENE", "getCanvasTilePoly"
		};

		List<String> hits = new ArrayList<>();
		for (Path path : sources())
		{
			String name = path.toString().replace('\\', '/');
			if (name.contains("/scene/"))
			{
				continue;
			}

			List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
			for (int i = 0; i < lines.size(); i++)
			{
				String trimmed = lines.get(i).trim();
				if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
					|| trimmed.startsWith("import "))
				{
					continue;
				}
				for (String token : sceneTokens)
				{
					if (lines.get(i).contains(token))
					{
						hits.add(SOURCE_ROOT.relativize(path) + ":" + (i + 1) + "  " + trimmed);
					}
				}
			}
		}

		if (!hits.isEmpty())
		{
			fail("Only the scene package may draw in the game scene.\n  "
				+ String.join("\n  ", hits));
		}
	}

	/** The Wilderness and PvP cutout has no setting to turn it off, and this keeps it that way. */
	@Test
	public void scenePresenceIsHardwiredOffInPvp() throws IOException
	{
		Path gate = SOURCE_ROOT.resolve("com/groupironmencompanion/scene/ScenePresenceGate.java");
		assertTrue("the scene presence gate must exist", Files.isRegularFile(gate));

		String source = new String(Files.readAllBytes(gate), StandardCharsets.UTF_8);
		for (String required : new String[]{
			"WorldType.PVP", "WorldType.BOUNTY", "WorldType.HIGH_RISK", "WorldType.DEADMAN",
			"WorldType.LAST_MAN_STANDING", "WorldType.PVP_ARENA", "VarbitID.INSIDE_WILDERNESS"})
		{
			assertTrue("the gate must refuse to draw for " + required, source.contains(required));
		}

		Path config = SOURCE_ROOT.resolve("com/groupironmencompanion/GroupIronmenCompanionConfig.java");
		String configSource = new String(Files.readAllBytes(config), StandardCharsets.UTF_8)
			.toLowerCase(java.util.Locale.ROOT);
		for (String forbidden : new String[]{"wilderness", "pvp"})
		{
			if (configSource.contains("keyname = \"" + forbidden))
			{
				fail("There must be no setting that relaxes the PvP cutout: found a key named "
					+ forbidden);
			}
		}
	}

	@Test
	public void usesNoReflectionOrDynamicCode() throws IOException
	{
		// A plugin hub rule: reviewers must be able to read every line the plugin executes.
		assertAbsent("The plugin hub forbids reflection, native calls and runtime code loading.",
			"java.lang.reflect", "Class.forName", "setAccessible", "TypeToken", "MethodHandle",
			"ClassLoader", "defineClass", "Runtime.getRuntime", "ProcessBuilder", "System.load");
	}

	@Test
	public void touchesNoFilesystem() throws IOException
	{
		// The only resource read is bundled in the jar, via getResourceAsStream.
		assertAbsent("The plugin must not read or write the filesystem.",
			"new File(", "FileWriter", "FileOutputStream", "FileInputStream", "Paths.get", "Files.");
	}

	/**
	 * The Swing views and the mouse/keyboard handler all run on AWT threads, where calling
	 * into {@link net.runelite.api.Client} is illegal. Getting this wrong is silent: the call
	 * throws somewhere the exception is swallowed, and the feature simply does nothing. That
	 * is exactly what happened to the group bank hotkey and its Group tab, which called
	 * {@code client.getWidget} inline and so never got as far as opening the window.
	 * <p>
	 * These classes must reach the client only through {@code clientThread.invoke}.
	 */
	@Test
	public void awtFacingClassesNeverTouchTheClientDirectly() throws IOException
	{
		List<String> hits = new ArrayList<>();

		for (Path path : sources())
		{
			String name = path.toString().replace('\\', '/');
			boolean awtFacing = name.contains("/ui/") || name.endsWith("Input.java");
			if (!awtFacing)
			{
				continue;
			}

			List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
			for (int i = 0; i < lines.size(); i++)
			{
				String line = lines.get(i);
				String trimmed = line.trim();
				if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
					|| trimmed.startsWith("import "))
				{
					continue;
				}
				// A bare "client." field access. The lookbehind keeps package names such as
				// net.runelite.client.ui out of it, and "clientThread." has no dot after
				// "client" so it never matches.
				if (BARE_CLIENT_ACCESS.matcher(line).find())
				{
					hits.add(SOURCE_ROOT.relativize(path) + ":" + (i + 1) + "  " + trimmed);
				}
			}
		}

		if (!hits.isEmpty())
		{
			fail("AWT-facing classes must reach the client via clientThread.invoke, not directly.\n  "
				+ String.join("\n  ", hits));
		}
	}

	@Test
	public void talksToExactlyOneConfigurableHost() throws IOException
	{
		List<String> literals = new ArrayList<>();
		for (Path path : sources())
		{
			for (String line : Files.readAllLines(path, StandardCharsets.UTF_8))
			{
				String trimmed = line.trim();
				if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*"))
				{
					continue;
				}
				if (line.contains("http://") || line.contains("https://"))
				{
					literals.add(SOURCE_ROOT.relativize(path) + "  " + trimmed);
				}
			}
		}

		if (literals.size() != 1 || !literals.get(0).contains("https://groupiron.men"))
		{
			fail("The only URL in the plugin should be the default groupiron.men base URL, "
				+ "which the user can override. Found:\n  " + String.join("\n  ", literals));
		}
	}
}
