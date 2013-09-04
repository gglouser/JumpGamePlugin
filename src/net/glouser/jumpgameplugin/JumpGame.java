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
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class JumpGame {

    public enum AddResult {
        SUCCESS,
        FAILED_IN_PROGRESS,
        FAILED_ALREADY_PLAYING
    };

    public enum RemoveResult {
        SUCCESS,
        FAILED_NOT_FOUND
    };

    public enum StartResult {
        SUCCESS,
        FAILED_IN_PROGRESS,
        FAILED_NO_JUMP_TP,
        FAILED_NO_POOL,
        FAILED_NO_PLAYERS
    };

    private enum GameState {
        OVER,
        JUMPING,
        EXIT_POOL,
        PROVE_YOUR_WORTH
    };

    private Plugin plugin;
    private Random rand;
    private GameState gameState;
    private ArrayList<Player> allPlayers;
    private LinkedList<Player> activePlayers;
    private List<Block> pool;
    private Player currentJumper;
    private int jumpCount;
    private Location jumpTP;
    private Location waitTP;
    private Location splashdown;
    private int jumpTimeoutTicks;
    private int exitPoolTimeoutTicks;
    private BukkitTask timeoutTask;

    public JumpGame(Plugin plugin) {
        this.plugin = plugin;
        rand = new Random();
        allPlayers = new ArrayList<Player>();
        activePlayers = new LinkedList<Player>();
        gameState = GameState.OVER;
    }

    public void disable() {
        cancelTimeout();
    }

    public boolean gameInProgress() {
        return gameState != GameState.OVER;
    }

    public boolean isPlaying(Player p) {
        return allPlayers.contains(p);
    }

    public void setJumpTP(Location loc) {
        jumpTP = loc;
    }

    public void setWaitTP(Location loc) {
        waitTP = loc;
    }

    public void setJumpTimeoutTicks(int ticks) {
        jumpTimeoutTicks = ticks;
    }

    public void setExitPoolTimeoutTicks(int ticks) {
        exitPoolTimeoutTicks = ticks;
    }

    public void setPool(List<Block> pool) {
        this.pool = pool;
    }

    public AddResult addPlayer(Player p) {
        if (gameInProgress()) {
            return AddResult.FAILED_IN_PROGRESS;
        } else if (isPlaying(p)) {
            return AddResult.FAILED_ALREADY_PLAYING;
        }
        allPlayers.add(p);
        return AddResult.SUCCESS;
    }

    public RemoveResult removePlayer(Player p) {
        if (!isPlaying(p)) {
            return RemoveResult.FAILED_NOT_FOUND;
        }
        allPlayers.remove(p);
        activePlayers.remove(p);
        if (p == currentJumper) {
            if (activePlayers.size() > 0) {
                cancelTimeout();
                nextJumper(GameState.JUMPING);
            } else {
                broadcast("Last active player left game - game over.");
                gameOver();
            }
        }
        return RemoveResult.SUCCESS;
    }

    public List<Player> getPlayers() {
        return allPlayers;
    }

    public void reset() {
        broadcast("Jump game has been reset");
        gameOver();
        resetPool();
        jumpCount = 0;
    }

    public StartResult start() {
        if (gameInProgress()) {
            return StartResult.FAILED_IN_PROGRESS;
        } else if (jumpTP == null) {
            return StartResult.FAILED_NO_JUMP_TP;
        } else if (pool == null || pool.size() == 0) {
            return StartResult.FAILED_NO_POOL;
        } else if (allPlayers.size() == 0) {
            return StartResult.FAILED_NO_PLAYERS;
        }
        broadcast("Jump game starts now!");
        jumpCount = 0;
        resetPool();
        initActivePlayers();
        nextJumper(GameState.JUMPING);
        moveAllWaiters();
        return StartResult.SUCCESS;
    }

    public void playerDied(Player p) {
        if (p != currentJumper) return;
        cancelTimeout();
        if (gameState == GameState.JUMPING) {
            if (activePlayers.size() == 1) {
                broadcast(activePlayers.get(0).getName() + ": prove your worth!");
                activePlayers.add(currentJumper);
                nextJumper(GameState.PROVE_YOUR_WORTH);
            } else if (activePlayers.size() == 0) {
                // single player game
                broadcast("Game over! You made " + jumpCount + " successful jumps.");
                gameOver();
            } else {
                broadcast(p.getName() + " is out!" +
                  ((activePlayers.size() == 2) ? " Only two players remain." : ""));
                nextJumper(GameState.JUMPING);
            }
        } else if (gameState == GameState.PROVE_YOUR_WORTH) {
            broadcast(activePlayers.get(0).getName() + " gets another chance. The game continues.");
            activePlayers.add(currentJumper);
            nextJumper(GameState.JUMPING);
        }
    }

    public void playerMoved(Player p, Location movedTo) {
        if (p != currentJumper) return;
        if (gameState == GameState.JUMPING && isPoolBlock(movedTo.getBlock())) {
            broadcast("Splashdown! Good jump by " + p.getName());
            currentJumper.sendMessage("Please exit the pool.");
            jumpCount += 1;
            splashdown = movedTo;
            cancelTimeout();
            setExitPoolTimeout();
            gameState = GameState.EXIT_POOL;
        } else if (gameState == GameState.PROVE_YOUR_WORTH && isPoolBlock(movedTo.getBlock())) {
            jumpCount += 1;
            broadcast(p.getName() + " has proved their worth.");
            broadcast(p.getName() + " wins!");
            broadcast("There were " + jumpCount + " successful jumps in all.");
            gameOver();
        } else if (gameState == GameState.EXIT_POOL && !isPoolBlock(movedTo.getBlock())) {
            cancelTimeout();
            if (allPlayers.size() > 1 && waitTP != null) {
                // Not single-player. Send last jumper to waiting area.
                currentJumper.teleport(waitTP);
            }
            endTurn();
        }
    }

    public void forceEndTurn() {
        Location dest;
        if (waitTP != null) {
            dest = waitTP;
        } else {
            dest = splashdown.clone();
            dest.add(0.5, 2, 0.5);
        }
        currentJumper.teleport(dest);
        endTurn();
    }

    private void endTurn() {
        splashdown.getBlock().setType(Material.OBSIDIAN);
        activePlayers.add(currentJumper);
        if (poolIsGone()) {
            broadcast("Every spot in the pool has been hit.");
            broadcast("Everybody wins. Congratulations!");
            gameOver();
        } else {
            nextJumper(GameState.JUMPING);
        }
    }

    private void resetPool() {
        for (Block b : pool) {
            b.setType(Material.STATIONARY_WATER);
        }
    }

    private boolean isPoolBlock(Block b) {
        return b.getType() == Material.STATIONARY_WATER && pool.contains(b);
    }

    private boolean poolIsGone() {
        return jumpCount == pool.size();
    }

    private void initActivePlayers() {
        activePlayers.clear();
        for (int i = 0; i < allPlayers.size(); i++) {
            int pos = rand.nextInt(i+1);
            activePlayers.add(pos, allPlayers.get(i));
        }
    }

    private void nextJumper(GameState newGameState) {
        gameState = newGameState;
        currentJumper = activePlayers.remove();
        currentJumper.teleport(jumpTP);
        setJumpTimeout();
        broadcast("Now jumping: " + currentJumper.getName());
    }

    private void moveAllWaiters() {
        if (waitTP != null) {
            for (Player p : activePlayers) {
                p.teleport(waitTP);
            }
        }
    }

    private void gameOver() {
        gameState = GameState.OVER;
        currentJumper = null;
        cancelTimeout();
        allPlayers.clear();
        activePlayers.clear();
    }

    private void setJumpTimeout() {
        JumpTimeout j = new JumpTimeout(currentJumper);
        timeoutTask = j.runTaskLater(plugin, jumpTimeoutTicks);
    }

    private void setExitPoolTimeout() {
        ExitPoolTimeout ep = new ExitPoolTimeout(this);
        timeoutTask = ep.runTaskLater(plugin, exitPoolTimeoutTicks);
    }

    private void cancelTimeout() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
    }

    /* Send a message to all players.
    */
    private void broadcast(String msg) {
        for (Player p : allPlayers) {
            p.sendMessage(msg);
        }
    }

}
