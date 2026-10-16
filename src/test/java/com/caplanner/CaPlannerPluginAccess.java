package com.caplanner;

/** What the tests of other packages may ask of the plugin's own package. */
public final class CaPlannerPluginAccess
{
	private CaPlannerPluginAccess()
	{
	}

	public static boolean isOwnData(String key)
	{
		return CaPlannerPlugin.isOwnData(key);
	}
}
