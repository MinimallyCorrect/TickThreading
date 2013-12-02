package ru.tehkode.permissions;

import java.util.Map;

public interface IPermissionEntity {
	/**
	 * This method 100% run after all constructors have been run and entity
	 * object, and entity object are completely ready to operate
	 */
	void initialize();

	/**
	 * Return name of permission entity (User or Group)
	 * User should be equal to Player's name on the server
	 *
	 * @return name
	 */
	String getName();

	/**
	 * Returns entity prefix
	 *
	 * @param worldName
	 * @return prefix
	 */
	String getPrefix(String worldName);

	String getPrefix();
	/**
	 * Returns entity prefix
	 *
	 */
	/**
	 * Set prefix to value
	 *
	 * @param prefix new prefix
	 */
	void setPrefix(String prefix, String worldName);

	/**
	 * Return entity suffix
	 *
	 * @return suffix
	 */
	String getSuffix(String worldName);

	String getSuffix();

	/**
	 * Set suffix to value
	 *
	 * @param suffix new suffix
	 */
	void setSuffix(String suffix, String worldName);

	/**
	 * Checks if entity has specified permission in default world
	 *
	 * @param permission Permission to check
	 * @return true if entity has this permission otherwise false
	 */
	boolean has(String permission);

	/**
	 * Check if entity has specified permission in world
	 *
	 * @param permission Permission to check
	 * @param dimension  World to check permission in
	 * @return true if entity has this permission otherwise false
	 */
	boolean has(String permission, String dimension);

	/**
	 * Return all entity permissions in specified world
	 *
	 * @param world World name
	 * @return Array of permission expressions
	 */
	String[] getPermissions(String world);

	/**
	 * Return permissions for all worlds
	 * Common permissions stored as "" (empty string) as world.
	 *
	 * @return Map with world name as key and permissions array as value
	 */
	Map<String, String[]> getAllPermissions();

	/**
	 * Add permissions for specified world
	 *
	 * @param permission Permission to add
	 * @param world      World name to add permission to
	 */
	void addPermission(String permission, String world);

	/**
	 * Add permission in common space (all worlds)
	 *
	 * @param permission Permission to add
	 */
	void addPermission(String permission);

	/**
	 * Remove permission in world
	 *
	 * @param permission Permission to remove
	 * @param world      World name to remove permission for
	 */
	void removePermission(String permission, String worldName);

	/**
	 * Remove specified permission from all worlds
	 *
	 * @param permission Permission to remove
	 */
	void removePermission(String permission);

	/**
	 * Set permissions in world
	 *
	 * @param permissions Array of permissions to set
	 * @param world       World to set permissions for
	 */
	void setPermissions(String[] permissions, String world);

	/**
	 * Set specified permissions in common space (all world)
	 *
	 * @param permission Permission to set
	 */
	void setPermissions(String[] permission);

	/**
	 * Get option in world
	 *
	 * @param option       Name of option
	 * @param world        World to look for
	 * @param defaultValue Default value to fallback if no such option was found
	 * @return Value of option as String
	 */
	String getOption(String option, String world, String defaultValue);

	/**
	 * Return option
	 * Option would be looked up in common options
	 *
	 * @param option Option name
	 * @return option value or empty string if option was not found
	 */
	String getOption(String option);

	/**
	 * Return option for world
	 *
	 * @param option Option name
	 * @param world  World to look in
	 * @return option value or empty string if option was not found
	 */
	String getOption(String option, String world);

	/**
	 * Return integer value for option
	 *
	 * @param optionName
	 * @param world
	 * @param defaultValue
	 * @return option value or defaultValue if option was not found or is not integer
	 */
	int getOptionInteger(String optionName, String world, int defaultValue);

	/**
	 * Returns double value for option
	 *
	 * @param optionName
	 * @param world
	 * @param defaultValue
	 * @return option value or defaultValue if option was not found or is not double
	 */
	double getOptionDouble(String optionName, String world, double defaultValue);

	/**
	 * Returns boolean value for option
	 *
	 * @param optionName
	 * @param world
	 * @param defaultValue
	 * @return option value or defaultValue if option was not found or is not boolean
	 */
	boolean getOptionBoolean(String optionName, String world,
							 boolean defaultValue);

	/**
	 * Set specified option in world
	 *
	 * @param option Option name
	 * @param value  Value to set, null to remove
	 * @param world  World name
	 */
	void setOption(String option, String value, String world);

	/**
	 * Set option for all worlds. Can be overwritten by world specific option
	 *
	 * @param option Option name
	 * @param value  Value to set, null to remove
	 */
	void setOption(String permission, String value);

	/**
	 * Get options in world
	 *
	 * @param world
	 * @return Option value as string Map
	 */
	Map<String, String> getOptions(String world);

	/**
	 * Return options for all worlds
	 * Common options stored as "" (empty string) as world.
	 *
	 * @return Map with world name as key and map of options as value
	 */
	Map<String, Map<String, String>> getAllOptions();

	/**
	 * Save in-memory data to storage backend
	 */
	void save();

	/**
	 * Remove entity data from backend
	 */
	void remove();

	/**
	 * Return state of entity
	 *
	 * @return true if entity is only in-memory
	 */
	boolean isVirtual();

	/**
	 * Return world names where entity have permissions/options/etc
	 *
	 * @return
	 */
	String[] getWorlds();

	/**
	 * Return entity timed (temporary) permission for world
	 *
	 * @param world
	 * @return Array of timed permissions in that world
	 */
	String[] getTimedPermissions(String world);

	/**
	 * Returns remaining lifetime of specified permission in world
	 *
	 * @param permission Name of permission
	 * @param world
	 * @return remaining lifetime in seconds of timed permission. 0 if permission is transient
	 */
	int getTimedPermissionLifetime(String permission, String world);

	/**
	 * Adds timed permission to specified world in seconds
	 *
	 * @param permission
	 * @param world
	 * @param lifeTime   Lifetime of permission in seconds. 0 for transient permission (world disappear only after server reload)
	 */
	void addTimedPermission(String permission, String world, int lifeTime);

	/**
	 * Removes specified timed permission for world
	 *
	 * @param permission
	 * @param world
	 */
	void removeTimedPermission(String permission, String world);

	boolean equals(Object obj);

	int hashCode();

	String toString();

	String getMatchingExpression(String permission, String world);

	String getMatchingExpression(String[] permissions, String permission);

	/**
	 * Checks if specified permission matches specified permission expression
	 *
	 * @param expression       permission expression - what user have in his permissions list (permission.nodes.*)
	 * @param permission       permission which are checking for (permission.node.some.subnode)
	 * @param additionalChecks check for parent node matching
	 * @return
	 */
	boolean isMatches(String expression, String permission,
					  boolean additionalChecks);

	boolean explainExpression(String expression);

	boolean isDebug();

	void setDebug(boolean debug);
}
