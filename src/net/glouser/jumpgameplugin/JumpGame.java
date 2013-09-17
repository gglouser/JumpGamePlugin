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
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitRunnable;

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

    private static String MSG_PREFIX = "[" + ChatColor.DARK_AQUA + "Jump"
                                     + ChatColor.WHITE + "] ";

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
    private int jumpHardTimeoutTicks;
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

    public boolean isCurrentPlayer(Player p) {
        return p == players.getCurrentPlayer();
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

    public void setJumpHardTimeoutTicks(int ticks) {
        jumpHardTimeoutTicks = ticks;
    }

    public void setExitPoolTimeoutTicks(int ticks) {
        exitPoolTimeoutTicks = ticks;
    }

    public TurnTracker.AddResult addPlayer(Player p) {
        return players.addPlayer(p);
    }

    public TurnTracker.RemoveResult removePlayer(Player p) {
        broadcast("Removed " + p.getName() + " from the jump game");

        if (p == players.getCurrentPlayer()) {
            plugin.getLogger().info("removing current player");
            cancelTimeout();

            // If removing the current player and they have already
            // jumped into the water, go ahead and fill in the block.
            if (jumpState == JumpState.EXIT_POOL) {
                fillSplashdownBlock();
            }
        }

        TurnTracker.RemoveResult res = players.removePlayer(p);
        switch (res) {
            case SUCCESS_NEW_CURRENT_PLAYER:
                update();
                break;
            case SUCCESS_NEW_STATE:
                switch (players.getState()) {
                    case WINNER:
                        broadcast("As the last player remaining in the game, "
                            + players.getCurrentPlayer().getName()
                            + " wins by default");
                        gameOver();
                        break;
                    case GAME_POINT:
                        broadcast("You are the last contender, "
                            + players.getCurrentPlayer().getName()
                            + "; if you can prove your worth, you win.");
                        break;
                }
                break;
        }
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
                        p.sendMessage(MSG_PREFIX + "Please exit the pool.");
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
                broadcast("Game halted");
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

    /* Set the "soft" jump timeout.
     * The soft timeout sets the player on fire so that they need
     * to jump into the water or die. This should deal with players
     * who are taking too long or who go AFK in the middle of the game.
     * If this takes a player out, they get a second chance as if
     * they had missed a jump.
     */
    private void setJumpTimeout() {
        BukkitRunnable br = new BukkitRunnable() {
            public void run() {
                Player jumper = players.getCurrentPlayer();
                jumper.sendMessage(MSG_PREFIX + "Jump into the water, quick!");
                jumper.setFireTicks(1000);
                setHardJumpTimeout();
            }
        };
        timeoutTask = br.runTaskLater(plugin, jumpTimeoutTicks);
    }

    /* Set the "hard" jump timeout.
     * The hard timeout removes the player from the game if they
     * haven't jumped. The soft timeout could fail to the player
     * not dying from the fire for some reason (creative mode, getting
     * in some non-pool water, easy difficulty setting, rain, etc.).
     * In this case, the player is completely removed from the game.
     */
    private void setHardJumpTimeout() {
        BukkitRunnable br = new BukkitRunnable() {
            public void run() {
                Player p = players.getCurrentPlayer();
                broadcast(p.getName() + " took too long to jump and is eliminated");
                removePlayer(p);
                if (waitTP != null) {
                    p.teleport(waitTP);
                }
            }
        };
        timeoutTask = br.runTaskLater(plugin, jumpHardTimeoutTicks);
    }

    private void setExitPoolTimeout() {
        BukkitRunnable br = new BukkitRunnable() {
            public void run() {
                forceEndTurn();
            }
        };
        timeoutTask = br.runTaskLater(plugin, exitPoolTimeoutTicks);
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
            p.sendMessage(MSG_PREFIX + msg);
        }
    }

}
