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
import java.util.LinkedList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.Block;

public class JumpPool {

    private static Material FILL_TYPE = Material.OBSIDIAN;

    private List<Block> blocks;
    private int fillCount;

    public JumpPool() {
        this.blocks = new ArrayList<Block>();
    }

    public void setBlocks(List<Block> bs) {
        blocks = bs;
    }

    public List<Block> getBlocks() {
        return blocks;
    }

    public int size() {
        return blocks.size();
    }

    public void reset() {
        for (Block b : blocks) {
            b.setType(Material.STATIONARY_WATER);
        }
        fillCount = 0;
    }

    public boolean isPoolWater(Block b) {
        return b.getType() == Material.STATIONARY_WATER && blocks.contains(b);
    }

    public void fillBlock(Block b) {
        if (fillCount < blocks.size() - 1) {
            b.setType(FILL_TYPE);
            fillCount += 1;
        }
    }

    public void buildPool(Block startBlock, int poolSizeLimit) {
        blocks.clear();
        fillCount = 0;
        LinkedList<Block> pending = new LinkedList<Block>();
        pending.add(startBlock);
        while (pending.size() > 0) {
            Block b = pending.remove();
            if (!blocks.contains(b) && b.getType() == Material.STATIONARY_WATER) {
                if (blocks.size() >= poolSizeLimit) {
                    break;
                }
                blocks.add(b);
                pending.add(b.getRelative(1, 0, 0));
                pending.add(b.getRelative(-1, 0, 0));
                pending.add(b.getRelative(0, 0, 1));
                pending.add(b.getRelative(0, 0, -1));
            }
        }
    }

}
