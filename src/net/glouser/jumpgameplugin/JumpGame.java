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
import java.util.List;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class JumpGame {

    public enum StartResult {
        SUCCESS,
        FAILED_IN_PROGRESS,
        FAILED_NO_JUMP_TP,
        FAILED_NO_POOL,
        FAILED_NO_PLAYERS
    }

    private enum JumpState {
        NO_GAME,
        JUMPING,
        EXIT_POOL,
    }

    private Plugin plugin;
    private JumpPool pool;
    private TurnTracker players;

    private JumpState jumpState;
    private int jumpCount;
    private Location splashdown;
    private ArrayList<Block> splashdownBlocks;

    private Location jumpTP;
    private Location waitTP;
    private int jumpTimeoutTicks;
    private int exitPoolTimeoutTicks;
    private BukkitTask timeoutTask;

    public JumpGame(Plugin plugin, JumpPool pool) {
        this.plugin = plugin;
        this.pool = pool;
        players = new TurnTracker(plugin);
        jumpState = JumpState.NO_GAME;
        splashdownBlocks = new ArrayList<Block>();
    }

    public void disable() {
        cancelTimeout();
    }

    public boolean gameInProgress() {
        return jumpState != JumpState.NO_GAME;
    }

    public boolean isPlaying(Player p) {
        return players.isPlaying(p);
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

    public TurnTracker.AddResult addPlayer(Player p) {
        return players.addPlayer(p);
    }

    public TurnTracker.RemoveResult removePlayer(Player p) {
        // If removing the current player and they have already
        // jumped into the water, go ahead and fill in the block.
        if (p == players.getCurrentPlayer() && jumpState == JumpState.EXIT_POOL) {
            fillSplashdownBlock();
        }
        TurnTracker.RemoveResult res = players.removePlayer(p);
        update();
        return res;
    }

    public List<Player> getPlayers() {
        return players.getPlayers();
    }

    public void reset() {
        broadcast("Jump game has been reset");
        gameOver();
        pool.reset();
    }

    public StartResult start() {
        if (gameInProgress()) {
            return StartResult.FAILED_IN_PROGRESS;
        } else if (jumpTP == null) {
            return StartResult.FAILED_NO_JUMP_TP;
        } else if (pool.size() == 0) {
            return StartResult.FAILED_NO_POOL;
        } else if (players.getPlayers().size() == 0) {
            return StartResult.FAILED_NO_PLAYERS;
        }
        broadcast("Jump game starts now!");
        jumpCount = 0;
        pool.reset();
        splashdownBlocks.clear();
        players.setMode(TurnTracker.Mode.CONTINUOUS);
        players.start();
        broadcast("First up: " + players.getCurrentPlayer().getName());
        nextJumper();
        moveAllWaiters();
        return StartResult.SUCCESS;
    }

    public void playerDied(Player p) {
        if (p != players.getCurrentPlayer()) return;
        cancelTimeout();
        switch (jumpState) {

            case JUMPING:
                endTurnFailure();
                break;

            case EXIT_POOL:
                endTurnSuccess();
                break;
        }
    }

    public void playerMoved(Player p, Location movedTo) {
        if (p != players.getCurrentPlayer()) return;
        boolean movedToPool = pool.isPoolWater(movedTo.getBlock());
        switch (jumpState) {

            case JUMPING:
                if (movedToPool) {
                    jumpCount += 1;
                    splashdown = movedTo;
                    if (players.getState() == TurnTracker.State.GAME_POINT) {
                        players.endTurnSuccess();
                        update();
                    } else {
                        broadcast("Splashdown! Good jump by " + p.getName());
                        p.sendMessage("Please exit the pool.");
                        cancelTimeout();
                        setExitPoolTimeout();
                        jumpState = JumpState.EXIT_POOL;
                    }
                }
                break;

            case EXIT_POOL:
                if (!movedToPool) {
                    cancelTimeout();
                    if (waitTP != null && players.getPlayers().size() > 1) {
                        // Not single-player. Send last jumper to waiting area.
                        p.teleport(waitTP);
                    }
                    endTurnSuccess();
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
        players.getCurrentPlayer().teleport(dest);
        endTurnSuccess();
    }

    private void endTurnSuccess() {
        players.endTurnSuccess();
        fillSplashdownBlock();
        update();
    }

    private void endTurnFailure() {
        players.endTurnFailure();
        update();
    }

    private void update() {
        switch (players.getState()) {
            case STOPPED:
                plugin.getLogger().info("Game halted");
                gameOver();
                break;

            case READY:
                broadcast("Now jumping: " + players.getCurrentPlayer().getName());
                nextJumper();
                break;

            case GAME_POINT:
                broadcast("To win the game, " + players.getCurrentPlayer().getName() + ", prove your worth!");
                nextJumper();
                break;

            case SECOND_CHANCE:
                broadcast("All contenders missed, so you get a second chance");
                broadcast("Starting with: " + players.getCurrentPlayer().getName());
                nextJumper();
                break;

            case WINNER:
                broadcast(players.getCurrentPlayer().getName() + " has proved their worth.");
                broadcast(players.getCurrentPlayer().getName() + " wins!");
                broadcast("There were " + jumpCount + " successful jumps in all.");
                gameOver();
                break;

            case SP_READY:
                broadcast("You are cleared to jump");
                nextJumper();
                break;

            case SP_GAME_OVER:
                broadcast("Game over! You made " + jumpCount + " successful jumps.");
                gameOver();
                break;

            case NEW_ROUND:
                broadcast("Round " + players.getRoundNum() + "! Now jumping: " + players.getCurrentPlayer().getName());
                fillSavedBlocks();
                nextJumper();
                break;

            case SECOND_CHANCE_ROUND:
                broadcast("All contenders in that round missed, so you get a second chance");
                broadcast("Round " + players.getRoundNum() + ", starting with: " + players.getCurrentPlayer().getName());
                nextJumper();
                break;
        }
    }

    private void nextJumper() {
        players.getCurrentPlayer().teleport(jumpTP);
        setJumpTimeout();
        jumpState = JumpState.JUMPING;
    }

    private void gameOver() {
        jumpState = JumpState.NO_GAME;
        players.reset();
        cancelTimeout();
        splashdownBlocks.clear();
    }

    private void moveAllWaiters() {
        if (waitTP != null) {
            List<Player> ps = players.getNextPlayers();
            for (Player p : ps) {
                p.teleport(waitTP);
            }
        }
    }

    private void fillSplashdownBlock() {
        Block splashdownBlock = splashdown.getBlock();
        switch (players.getMode()) {
            case CONTINUOUS:
                pool.fillBlock(splashdownBlock);

                // If there is only one water left in the pool,
                // switch to round format.
                if (pool.atFillLimit() && players.getPlayers().size() > 1) {
                    players.setMode(TurnTracker.Mode.ROUNDS);
                }
                break;

            case ROUNDS:
                if (!splashdownBlocks.contains(splashdownBlock)) {
                    splashdownBlocks.add(splashdownBlock);
                }
                break;

            default:
                plugin.getLogger().info("Unexpected TurnTracker.Mode: " + players.getMode());
                break;
        }
    }

    private void fillSavedBlocks() {
        for (Block b : splashdownBlocks) {
            pool.fillBlock(b);
        }
        splashdownBlocks.clear();
    }

    private void setJumpTimeout() {
        JumpTimeout j = new JumpTimeout(players.getCurrentPlayer());
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
        List<Player> ps = players.getPlayers();
        for (Player p : ps) {
            p.sendMessage(msg);
        }
    }

}
