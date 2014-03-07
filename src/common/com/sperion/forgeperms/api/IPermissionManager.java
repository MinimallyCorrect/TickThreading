package com.sperion.forgeperms.api;

/**
 * Base for all permission handlers
 * @author Joe Goett
 */
public interface IPermissionManager {
    /**
     * Returns the name of the permission manager
     * @return
     */
    public String getName();
    
    /**
     * Loads the PermissionHandler, typically used to check if the Permission
     * Manager is there and register anything with the manager
     * 
     * @return
     */
    public boolean load();
    
    /**
     * Gets the load error string
     * @return
     */
    public String getLoadError();
    
    /**
     * Checks if a user has the permission node in the given world
     * 
     * @param name
     * @param world
     * @param node
     * @return
     */
    public boolean canAccess(String name, String world, String node);

    public boolean addGroup(String player, String group);
    
    public boolean removeGroup(String player, String group);
    
    public String[] getGroupNames(String player);
    
    public String getPrimaryGroup(String world, String playerName);
}