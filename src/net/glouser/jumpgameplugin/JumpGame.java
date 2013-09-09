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
    private ArrayList<Player> recentDead;
    private JumpPool pool;
    private Player currentJumper;
    private int jumpCount;
    private Location jumpTP;
    private Location waitTP;
    private Location splashdown;
    private int jumpTimeoutTicks;
    private int exitPoolTimeoutTicks;
    private BukkitTask timeoutTask;

    public JumpGame(Plugin plugin, JumpPool pool) {
        this.plugin = plugin;
        this.pool = pool;
        rand = new Random();
        allPlayers = new ArrayList<Player>();
        activePlayers = new LinkedList<Player>();
        recentDead = new ArrayList<Player>();
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
        recentDead.remove(p);
        if (p == currentJumper) {
            cancelTimeout();
            switch (gameState) {

                case JUMPING:
                    if (allPlayers.size() == 0) {
                        // Game was single player
                        gameOver();
                    } else if (allPlayers.size() == 1) {
                        // Game was two player, now single player
                        // Remaining player can't have died,
                        // otherwise, we would be in PROVE_YOUR_WORTH.
                        // Keep playing, or declare winner?
                        nextJumper(GameState.JUMPING);
                    } else if (activePlayers.size() == 1) {
                        if (recentDead.size() > 0) {
                            broadcast(activePlayers.get(0).getName() + ": prove your worth!");
                            nextJumper(GameState.PROVE_YOUR_WORTH);
                        } else {
                            // No recent dead, this is the last player standing
                            broadcast(activePlayers.get(0).getName() + " is the last player standing.");
                            gameOver();
                        }
                    } else {
                        nextJumper(GameState.JUMPING);
                    }
                    break;

                case PROVE_YOUR_WORTH:
                    if (recentDead.size() == 1) {
                        broadcast("Worth was not proven. " + recentDead.get(0).getName() + " wins by default");
                        gameOver();
                    } else {
                        activePlayers.addAll(recentDead);
                        recentDead.clear();
                        nextJumper(GameState.JUMPING);
                    }
                    break;

                case EXIT_POOL:
                    if (allPlayers.size() == 0) {
                        // Game was single player
                        gameOver();
                    } else if (allPlayers.size() == 1) {
                        // Game was two player, now single player.
                        // Remaining player can't have died,
                        // otherwise, current player would have won.
                        // Keep playing or declare winner?
                        endTurn(GameState.JUMPING);
                    } else if (activePlayers.size() == 1) {
                        if (recentDead.size() > 0) {
                            broadcast(activePlayers.get(0).getName() + ": prove your worth!");
                            endTurn(GameState.PROVE_YOUR_WORTH);
                        } else {
                            // No recent dead, this is the last player standing
                            broadcast(activePlayers.get(0).getName() + " is the last player standing.");
                            gameOver();
                        }
                    } else {
                        endTurn(GameState.JUMPING);
                    }
                    break;
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
        pool.reset();
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
        pool.reset();
        initActivePlayers();
        recentDead.clear();
        nextJumper(GameState.JUMPING);
        moveAllWaiters();
        return StartResult.SUCCESS;
    }

    public void playerDied(Player p) {
        if (p != currentJumper) return;
        cancelTimeout();
        switch (gameState) {

            case JUMPING:
                recentDead.add(currentJumper);
                if (allPlayers.size() == 1) {
                    // Single player game
                    broadcast("Game over! You made " + jumpCount + " successful jumps.");
                    gameOver();
                } else if (activePlayers.size() == 1) {
                    broadcast(activePlayers.get(0).getName() + ": prove your worth!");
                    nextJumper(GameState.PROVE_YOUR_WORTH);
                } else {
                    nextJumper(GameState.JUMPING);
                }
                break;

            case PROVE_YOUR_WORTH:
                broadcast("Everyone in the last round missed, so they all get another chance.");
                activePlayers.addAll(recentDead);
                activePlayers.add(currentJumper);
                recentDead.clear();
                nextJumper(GameState.JUMPING);
                break;

            case EXIT_POOL:
                activePlayers.add(currentJumper);
                endTurn(GameState.JUMPING);
                break;
        }
    }

    public void playerMoved(Player p, Location movedTo) {
        if (p != currentJumper) return;
        boolean movedToPool = pool.isPoolWater(movedTo.getBlock());
        switch (gameState) {

            case JUMPING:
                if (movedToPool) {
                    broadcast("Splashdown! Good jump by " + p.getName());
                    currentJumper.sendMessage("Please exit the pool.");
                    jumpCount += 1;
                    splashdown = movedTo;
                    cancelTimeout();
                    setExitPoolTimeout();
                    gameState = GameState.EXIT_POOL;
                }
                break;

            case PROVE_YOUR_WORTH:
                if (movedToPool) {
                    jumpCount += 1;
                    broadcast(p.getName() + " has proved their worth.");
                    broadcast(p.getName() + " wins!");
                    broadcast("There were " + jumpCount + " successful jumps in all.");
                    gameOver();
                }
                break;

            case EXIT_POOL:
                if (!movedToPool) {
                    cancelTimeout();
                    if (waitTP != null && allPlayers.size() > 1) {
                        // Not single-player. Send last jumper to waiting area.
                        currentJumper.teleport(waitTP);
                    }
                    activePlayers.add(currentJumper);
                    endTurn(GameState.JUMPING);
                }
                break;
        }
    }

    public void forceEndTurn() {
        Location dest;
        if (waitTP != null) {
            dest = waitTP;
        } else {
            dest = splashdown.clone();
            dest.add(0, 2, 0);
        }
        currentJumper.teleport(dest);
        activePlayers.add(currentJumper);
        endTurn(GameState.JUMPING);
    }

    private void endTurn(GameState nextState) {
        pool.fillBlock(splashdown.getBlock());
        recentDead.clear();
        nextJumper(nextState);
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
        recentDead.clear();
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
