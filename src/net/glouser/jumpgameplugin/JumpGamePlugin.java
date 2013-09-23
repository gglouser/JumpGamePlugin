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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class JumpGamePlugin extends JavaPlugin implements Listener {

    private interface ConfigSetter<T> {
        String label();
        void set(T b);
    }

    private JumpGame game;
    private JumpPool pool;
    private JumpGameConfig config;
    private Button btnJoin;
    private Button btnStart;
    private Button btnReset;
    private Location respawnLocation;
    private Player configPlayer;
    private ConfigSetter<Button> buttonSetter;
    private int respawnDist;

    @Override
    public void onEnable() {
        getLogger().info("Registering event listeners");
        getServer().getPluginManager().registerEvents(this, this);
        pool = new JumpPool();
        game = new JumpGame(this, pool);
        config = new JumpGameConfig(this);
        loadConfig();
    }

    @Override
    public void onDisable() {
        getLogger().info("Unregistering event listeners");
        HandlerList.unregisterAll((org.bukkit.plugin.Plugin)this);
        game.disable();
        game = null;
        config = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd,
                             String label, String[] args) {
        // Require op status for all commands.
        if (!sender.isOp()) {
            sender.sendMessage("That command requires op status.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("jumpAddPlayer")) {
            return doAddPlayer(sender, args);
        } else if (cmd.getName().equalsIgnoreCase("jumpRmPlayer")) {
            return doRemovePlayer(sender, args);
        } else if (cmd.getName().equalsIgnoreCase("jumpList")) {
            doListPlayers(sender);
            return true;
        } else if (cmd.getName().equalsIgnoreCase("jumpStart")) {
            doStart(sender);
            return true;
        } else if (cmd.getName().equalsIgnoreCase("jumpReset")) {
            doReset(sender);
            return true;
        } else if (cmd.getName().equalsIgnoreCase("jumpSetJoin")) {
            configButton(sender, new ConfigSetter<Button>() {
                public String label() { return "join game"; }
                public void set(Button b) {
                    btnJoin = b;
                    config.setJoinButton(b);
                }
            });
            return true;
        } else if (cmd.getName().equalsIgnoreCase("jumpSetStart")) {
            configButton(sender, new ConfigSetter<Button>() {
                public String label() { return "game start"; }
                public void set(Button b) {
                    btnStart = b;
                    config.setStartButton(b);
                }
            });
            return true;
        } else if (cmd.getName().equalsIgnoreCase("jumpSetReset")) {
            configButton(sender, new ConfigSetter<Button>() {
                public String label() { return "game reset"; }
                public void set(Button b) {
                    btnReset = b;
                    config.setResetButton(b);
                }
            });
            return true;
        } else if (cmd.getName().equalsIgnoreCase("jumpSetJump")) {
            configLocation(sender, new ConfigSetter<Location>() {
                public String label() { return "jumper TP point"; }
                public void set(Location l) {
                    game.setJumpTP(l);
                    config.setJumpLocation(l);
                }
            });
            return true;
        } else if (cmd.getName().equalsIgnoreCase("jumpSetWait")) {
            configLocation(sender, new ConfigSetter<Location>() {
                public String label() { return "wait area TP point"; }
                public void set(Location l) {
                    game.setWaitTP(l);
                    config.setWaitLocation(l);
                }
            });
            return true;
        } else if (cmd.getName().equalsIgnoreCase("jumpSetSpawn")) {
            configLocation(sender, new ConfigSetter<Location>() {
                public String label() { return "respawn point"; }
                public void set(Location l) {
                    respawnLocation = l;
                    config.setRespawnLocation(l);
                }
            });
            return true;
        } else if (cmd.getName().equalsIgnoreCase("jumpSetPool")) {
            doSetPool(sender);
            return true;
        }
        return false;
    }

    private void configButton(CommandSender sender, ConfigSetter<Button> cs) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Must be set by player");
        } else {
            sender.sendMessage("Left-click to set the " + cs.label() + " button");
            configPlayer = (Player) sender;
            buttonSetter = cs;
        }
    }

    private void configLocation(CommandSender sender, ConfigSetter<Location> cs) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Must be set by player");
        } else {
            Player p = (Player) sender;
            Location l = p.getLocation();
            if (!config.worldCheck(l.getWorld())) {
                sender.sendMessage("All jump game elements must be in the same world.");
            } else {
                cs.set(l.clone());
                String msg = "Set jump game " + cs.label() + " to "
                  + l.getX() + ", " + l.getY() + ", " + l.getZ();
                sender.sendMessage(msg);
                getLogger().info(msg);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        game.removePlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (game.gameInProgress()) {
            game.playerDied(event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (game.isCurrentPlayer(event.getPlayer())) {
            event.setRespawnLocation(config.getJumpLocation());
        } else if (respawnLocation != null) {
            Location pl = event.getPlayer().getLocation();
            double dist = Math.max(Math.abs(pl.getX() - respawnLocation.getX()),
                                   Math.abs(pl.getZ() - respawnLocation.getZ()));
            if (dist < respawnDist) {
                event.setRespawnLocation(respawnLocation);
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (game.gameInProgress()) {
            game.playerMoved(event.getPlayer(), event.getTo());
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
              && event.getClickedBlock().getType() == Material.STONE_BUTTON) {
            if (btnJoin != null && btnJoin.isBlock(event.getClickedBlock())) {
                playerJoinGame(event.getPlayer());
            } else if (btnStart != null && btnStart.isBlock(event.getClickedBlock())) {
                doStart(event.getPlayer());
            } else if (btnReset != null && btnReset.isBlock(event.getClickedBlock())) {
                doReset(event.getPlayer());
            }
        } else if (buttonSetter != null && configPlayer == event.getPlayer()
              && event.getAction() == Action.LEFT_CLICK_BLOCK) {
            Block b = event.getClickedBlock();
            if (event.getClickedBlock().getType() == Material.STONE_BUTTON) {
                if (!config.worldCheck(b.getWorld())) {
                    event.getPlayer().sendMessage("All jump game elements must be in the same world.");
                } else {
                    Button btn = new Button(b);
                    buttonSetter.set(btn);
                    String msg = "Set " + buttonSetter.label() + " button to "
                      + b.getX() + "," + b.getY() + "," + b.getZ();
                    getLogger().info(msg);
                    configPlayer.sendMessage(msg);
                    configPlayer = null;
                    buttonSetter = null;
                }
            } else {
                configPlayer.sendMessage("You must left-click a button to set "
                  + buttonSetter.label() + " button");
            }
        }
    }

    private boolean doAddPlayer(CommandSender sender, String[] args) {
        if (args.length == 0 && sender instanceof Player) {
            playerJoinGame((Player) sender);
            return true;
        } else if (args.length < 1) {
            sender.sendMessage("Player name(s) required");
            return false;
        } else if (game.gameInProgress()) {
            sender.sendMessage("Can't add player - game in progress.");
            return true;
        }
        for (int i = 0; i < args.length; i++) {
            Player target = getServer().getPlayer(args[i]);
            if (target == null) {
                sender.sendMessage("Player " + args[i] + " is not online");
            } else {
                switch (game.addPlayer(target)) {
                    case SUCCESS:
                        break;
                    case FAILED_ALREADY_PLAYING:
                        sender.sendMessage("Can't add player - " + target.getName() + " has already joined");
                        break;
                }
            }
        }
        return true;
    }

    private void playerJoinGame(Player p) {
        switch (game.addPlayer(p)) {
            case SUCCESS:
                break;

            case FAILED_IN_PROGRESS:
                p.sendMessage("Can't join - game in progress.");
                break;

            case FAILED_ALREADY_PLAYING:
                p.sendMessage("You already joined the jump game");
                break;
        }
    }

    private boolean doRemovePlayer(CommandSender sender, String[] args) {
        if (args.length == 0 && sender instanceof Player) {
            switch (game.removePlayer((Player) sender)) {
                case SUCCESS:
                case SUCCESS_NEW_CURRENT_PLAYER:
                case SUCCESS_NEW_STATE:
                    break;
                case FAILED_NOT_FOUND:
                    sender.sendMessage("You were not playing the jump game");
                    break;
            }
            return true;
        } else if (args.length < 1) {
            sender.sendMessage("Player name(s) required");
            return false;
        }
        for (int i = 0; i < args.length; i++) {
            Player target = getServer().getPlayer(args[i]);
            if (target == null) {
                sender.sendMessage("Player " + args[i] + " is not online");
            } else {
                switch (game.removePlayer(target)) {
                    case SUCCESS:
                    case SUCCESS_NEW_CURRENT_PLAYER:
                    case SUCCESS_NEW_STATE:
                        break;
                    case FAILED_NOT_FOUND:
                        sender.sendMessage("Player " + target.getName() + " was not playing the jump game.");
                        break;
                }
            }
        }
        return true;
    }

    private void doListPlayers(CommandSender sender) {
        List<Player> players = game.getPlayers();
        StringBuilder msg = new StringBuilder("Current jump game players:");
        for (Player p : players) {
            msg.append(" ");
            msg.append(p.getName());
        }
        sender.sendMessage(msg.toString());
    }

    private void doStart(CommandSender sender) {
        switch (game.start()) {
            case SUCCESS:
                break;

            case FAILED_IN_PROGRESS:
                sender.sendMessage("Game already in progress");
                break;

            case FAILED_STARTING:
                sender.sendMessage("The jump game will start soon");
                break;

            case FAILED_NO_JUMP_TP:
                sender.sendMessage("Can't start game: jump platform not set");
                break;

            case FAILED_NO_POOL:
                sender.sendMessage("Can't start game: no pool");
                break;

            case FAILED_NO_PLAYERS:
                sender.sendMessage("Can't start game: no players");
                break;
        }
    }

    private void doReset(CommandSender sender) {
        // All players in the game get notified. Don't notify twice.
        if (!(sender instanceof Player && game.isPlaying((Player) sender))) {
            sender.sendMessage("Jump game has been reset");
        }
        game.reset();
    }

    private void doSetPool(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Must be set by player");
            return;
        }

        Player p = (Player) sender;
        Location l = p.getLocation();
        if (l.getBlock().getType() != Material.STATIONARY_WATER) {
            sender.sendMessage("Must be standing in water to set pool");
            return;
        }

        if (!config.worldCheck(l.getWorld())) {
            sender.sendMessage("All jump game elements must be in the same world.");
            return;
        }

        int poolSizeLimit = config.getPoolSizeLimit();
        pool.buildPool(l.getBlock(), poolSizeLimit);
        if (pool.size() >= poolSizeLimit) {
            sender.sendMessage("Pool size exceeded limit");
            getLogger().info("Pool size exceeded limit");
        }
        config.setPool(pool.getBlocks());

        String msg = "Found pool with size " + pool.size();
        sender.sendMessage(msg);
        getLogger().info(msg);
    }

    private void loadConfig() {
        saveDefaultConfig(); // does not overwrite existing config
        respawnLocation = config.getRespawnLocation();
        respawnDist = config.getRespawnDist();
        btnJoin = config.getJoinButton();
        btnStart = config.getStartButton();
        btnReset = config.getResetButton();
        game.setJumpTimeoutTicks(config.getJumpTimeout());
        game.setJumpHardTimeoutTicks(config.getJumpHardTimeout());
        game.setExitPoolTimeoutTicks(config.getExitPoolTimeout());
        game.setJumpTP(config.getJumpLocation());
        game.setWaitTP(config.getWaitLocation());
        pool.setBlocks(config.getPool());
    }

}
