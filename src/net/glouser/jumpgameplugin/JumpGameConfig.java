/***********************************************************************
 * Jump Game - mini-game plugin for Bukkit
 * Copyright (C) 2013 Grant Glouser
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ***********************************************************************/

package net.glouser.jumpgameplugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public class JumpGameConfig {

    private static String KEY_WORLD = "world";
    private static String KEY_POOL_XZ = "poolXZ";
    private static String KEY_POOL_Y = "poolY";
    private static String KEY_JUMP_TIMEOUT = "jumpTimeout";
    private static String KEY_EXIT_POOL_TIMEOUT = "exitPoolTimeout";
    private static String KEY_POOL_SIZE_LIMIT = "poolSizeLimit";
    private static String KEY_RESPAWN_DIST = "respawnDist";
    private static String KEY_RESPAWN_LOCATION = "respawnLocation";
    private static String KEY_JUMP_LOCATION = "jumpLocation";
    private static String KEY_WAIT_LOCATION = "waitLocation";
    private static String KEY_JOIN_BUTTON = "joinButton";
    private static String KEY_START_BUTTON = "startButton";
    private static String KEY_RESET_BUTTON = "resetButton";
    private static String KEY_X = "x";
    private static String KEY_Y = "y";
    private static String KEY_Z = "z";
    private static String KEY_YAW = "yaw";
    private static String KEY_PITCH = "pitch";

    private static int DEFAULT_JUMP_TIMEOUT = 600;
    private static int DEFAULT_EXIT_POOL_TIMEOUT = 200;
    private static int DEFAULT_RESPAWN_DIST = 32;
    private static int DEFAULT_POOL_SIZE_LIMIT = 1000;

    private Plugin plugin;

    public JumpGameConfig(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean worldCheck(World w) {
        String worldName = getConfig().getString(KEY_WORLD);
        return worldName == null || worldName.equals(w.getName());
    }

    public int getJumpTimeout() {
        return getConfig().getInt(KEY_JUMP_TIMEOUT, DEFAULT_JUMP_TIMEOUT);
    }

    public int getExitPoolTimeout() {
        return getConfig().getInt(KEY_EXIT_POOL_TIMEOUT, DEFAULT_EXIT_POOL_TIMEOUT);
    }

    public int getPoolSizeLimit() {
        return getConfig().getInt(KEY_POOL_SIZE_LIMIT, DEFAULT_POOL_SIZE_LIMIT);
    }

    public int getRespawnDist() {
        return getConfig().getInt(KEY_RESPAWN_DIST, DEFAULT_RESPAWN_DIST);
    }

    public Location getRespawnLocation() {
        return getLocation(KEY_RESPAWN_LOCATION);
    }

    public void setRespawnLocation(Location l) {
        setLocation(KEY_RESPAWN_LOCATION, l);
    }

    public Location getJumpLocation() {
        return getLocation(KEY_JUMP_LOCATION);
    }

    public void setJumpLocation(Location l) {
        setLocation(KEY_JUMP_LOCATION, l);
    }

    public Location getWaitLocation() {
        return getLocation(KEY_WAIT_LOCATION);
    }

    public void setWaitLocation(Location l) {
        setLocation(KEY_WAIT_LOCATION, l);
    }

    public Button getJoinButton() {
        return getButton(KEY_JOIN_BUTTON);
    }

    public void setJoinButton(Button b) {
        setButton(KEY_JOIN_BUTTON, b);
    }

    public Button getStartButton() {
        return getButton(KEY_START_BUTTON);
    }

    public void setStartButton(Button b) {
        setButton(KEY_START_BUTTON, b);
    }

    public Button getResetButton() {
        return getButton(KEY_RESET_BUTTON);
    }

    public void setResetButton(Button b) {
        setButton(KEY_RESET_BUTTON, b);
    }

    public List<Block> getPool() {
        FileConfiguration config = getConfig();
        String worldName = config.getString(KEY_WORLD);
        String poolXZs = config.getString(KEY_POOL_XZ);
        if (worldName == null || poolXZs == null || !config.isSet(KEY_POOL_Y)) {
            return null;
        }
        int poolY = config.getInt(KEY_POOL_Y);
        World poolWorld = getWorld(worldName);
        if (poolWorld == null) {
            getLogger().warning("world '" + worldName + "' not found");
            return null;
        }
        String[] coords = poolXZs.split(",");
        if (coords.length % 2 != 0) {
            getLogger().warning("pool coordinates uneven length");
            return null;
        }
        ArrayList<Block> pool = new ArrayList<Block>();
        for (int i = 0; i < coords.length; i += 2) {
            int x = Integer.parseInt(coords[i]);
            int z = Integer.parseInt(coords[i+1]);
            pool.add(poolWorld.getBlockAt(x, poolY, z));
        }
        getLogger().info("Loaded pool with size " + pool.size());
        return pool;
    }

    public void setPool(List<Block> pool) {
        ArrayList<String> coords = new ArrayList<String>();
        for (Block b : pool) {
            coords.add(Integer.toString(b.getX()));
            coords.add(Integer.toString(b.getZ()));
        }
        FileConfiguration config = getConfig();
        config.set(KEY_POOL_XZ, join(coords, ","));
        config.set(KEY_POOL_Y, pool.get(0).getY());
        if (!config.isSet(KEY_WORLD)) {
            config.set(KEY_WORLD, pool.get(0).getWorld().getName());
        }
        saveConfig();
    }

    private Location getLocation(String key) {
        FileConfiguration config = getConfig();
        String worldName = config.getString(KEY_WORLD);
        if (worldName == null) { return null; }
        World world = getWorld(worldName);
        if (world == null) { return null; }
        ConfigurationSection cs = config.getConfigurationSection(key);
        if (cs == null) { return null; }
        double x = cs.getDouble(KEY_X);
        double y = cs.getDouble(KEY_Y);
        double z = cs.getDouble(KEY_Z);
        float yaw = (float) cs.getDouble(KEY_YAW);
        float pitch = (float) cs.getDouble(KEY_PITCH);
        return new Location(world, x, y, z, yaw, pitch);
    }

    private void setLocation(String key, Location l) {
        FileConfiguration config = getConfig();
        if (!config.isSet(KEY_WORLD)) {
            config.set(KEY_WORLD, l.getWorld().getName());
        }
        ConfigurationSection cs = config.getConfigurationSection(key);
        if (cs == null) {
            cs = config.createSection(key);
        }
        cs.set(KEY_X, l.getX());
        cs.set(KEY_Y, l.getY());
        cs.set(KEY_Z, l.getZ());
        cs.set(KEY_YAW, l.getYaw());
        cs.set(KEY_PITCH, l.getPitch());
        saveConfig();
    }

    private Button getButton(String key) {
        FileConfiguration config = getConfig();
        String worldName = config.getString(KEY_WORLD);
        if (worldName == null) { return null; }
        ConfigurationSection cs = config.getConfigurationSection(key);
        if (cs == null) { return null; }
        int x = cs.getInt(KEY_X);
        int y = cs.getInt(KEY_Y);
        int z = cs.getInt(KEY_Z);
        return new Button(worldName, x, y, z);
    }

    private void setButton(String key, Button b) {
        FileConfiguration config = getConfig();
        if (!config.isSet(KEY_WORLD)) {
            config.set(KEY_WORLD, b.getWorldName());
        }
        ConfigurationSection cs = config.getConfigurationSection(key);
        if (cs == null) {
            cs = config.createSection(key);
        }
        cs.set(KEY_X, b.getX());
        cs.set(KEY_Y, b.getY());
        cs.set(KEY_Z, b.getZ());
        saveConfig();
    }

    private FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    private void saveConfig() {
        plugin.saveConfig();
    }

    private Logger getLogger() {
        return plugin.getLogger();
    }

    private World getWorld(String worldName) {
        return plugin.getServer().getWorld(worldName);
    }

    /* *********************************************************** */

    private static String join(List<String> strs, String delim) {
        StringBuilder sb = new StringBuilder();
        Iterator<String> iter = strs.iterator();
        if (iter.hasNext())
            sb.append(iter.next());
        while (iter.hasNext()) {
            sb.append(delim);
            sb.append(iter.next());
        }
        return sb.toString();
    }

}
