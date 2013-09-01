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

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

class JumpTimeout extends BukkitRunnable {

    private final Player jumper;

    public JumpTimeout(Player jumper) {
        this.jumper = jumper;
    }

    public void run() {
        jumper.sendMessage("Jump into the water, quick!");
        jumper.setFireTicks(1000);
    }

}
