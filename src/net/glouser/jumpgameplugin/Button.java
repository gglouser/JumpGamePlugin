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

import org.bukkit.block.Block;

public class Button {

    private String worldName;
    private int x;
    private int y;
    private int z;

    public Button(String world, int x, int y, int z) {
        this.worldName = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Button(Block b) {
        this.worldName = b.getWorld().getName();
        this.x = b.getX();
        this.y = b.getY();
        this.z = b.getZ();
    }

    public boolean isBlock(Block b) {
        return b.getX() == x && b.getY() == y && b.getZ() == z
          && worldName.equals(b.getWorld().getName());
    }

    public String getWorldName() { return worldName; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

}
