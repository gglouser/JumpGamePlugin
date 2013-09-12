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
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;

public class TurnTracker {

    public enum Mode {
        CONTINUOUS,
        ROUNDS
    }

    public enum State {
        STOPPED,
        READY,
        SP_READY,
        GAME_POINT,
        WINNER,
        SP_GAME_OVER,
        NEW_ROUND,
        SECOND_CHANCE_ROUND
    }

    public enum AddResult {
        SUCCESS,
        FAILED_IN_PROGRESS,
        FAILED_ALREADY_PLAYING
    }

    public enum RemoveResult {
        SUCCESS,
        FAILED_NOT_FOUND
    }

    private Plugin plugin;
    private Random rand;
    private Mode mode;
    private State state;
    private ArrayList<Player> allPlayers;
    private LinkedList<Player> nextPlayers;
    private ArrayList<Player> prevPlayers;
    private ArrayList<Player> provisionalOut;
    private Player currentPlayer;

    public TurnTracker(Plugin plugin) {
        this.plugin = plugin;
        rand = new Random();
        mode = Mode.CONTINUOUS;
        state = State.STOPPED;
        allPlayers = new ArrayList<Player>();
        nextPlayers = new LinkedList<Player>();
        prevPlayers = new ArrayList<Player>();
        provisionalOut = new ArrayList<Player>();
    }

    public void reset() {
        mode = Mode.CONTINUOUS;
        state = State.STOPPED;
        allPlayers.clear();
        nextPlayers.clear();
        prevPlayers.clear();
        provisionalOut.clear();
        currentPlayer = null;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode m) {
        mode = m;
    }

    public State getState() {
        return state;
    }

    public boolean isPlaying(Player p) {
        return allPlayers.contains(p);
    }

    public List<Player> getPlayers() {
        return allPlayers;
    }

    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    public List<Player> getNextPlayers() {
        return nextPlayers;
    }

    public AddResult addPlayer(Player p) {
        if (state != State.STOPPED) {
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
        nextPlayers.remove(p);
        prevPlayers.remove(p);
        provisionalOut.remove(p);
        if (p == currentPlayer) {
            removalNewCurrentPlayer();
        }
        removalStateFixup();
        return RemoveResult.SUCCESS;
    }

    public void start() {
        if (state != State.STOPPED) { return; }
        for (int i = 0; i < allPlayers.size(); i++) {
            int pos = rand.nextInt(i+1);
            nextPlayers.add(pos, allPlayers.get(i));
        }
        currentPlayer = nextPlayers.remove();
        state = (nextPlayers.size() == 0) ? State.SP_READY : State.READY;
    }

    public void endTurnSuccess() {
        switch (state) {
            case STOPPED:
            case WINNER:
            case SP_GAME_OVER:
                break;

            case READY:
            case NEW_ROUND:
            case SECOND_CHANCE_ROUND:
                switch (mode) {
                    case CONTINUOUS:
                        nextPlayers.add(currentPlayer);
                        provisionalOut.clear();
                        currentPlayer = nextPlayers.remove();
                        state = State.READY;
                        break;

                    case ROUNDS:
                        prevPlayers.add(currentPlayer);
                        if (nextPlayers.size() == 0) {
                            finishRound();
                        } else {
                            currentPlayer = nextPlayers.remove();
                            state = State.READY;
                        }
                        break;
                }
                break;

            case SP_READY:
                // Current player doesn't change.
                // No lists need updating.
                // State doesn't change.
                break;

            case GAME_POINT:
                // Current player wins
                state = State.WINNER;
                break;

            default:
                plugin.getLogger().info("unexpected state in TurnTracker.endTurnSuccess: " + state);
                break;
        }
    }

    public void endTurnFailure() {
        switch (state) {
            case STOPPED:
            case WINNER:
            case SP_GAME_OVER:
                break;

            case READY:
            case NEW_ROUND:
            case SECOND_CHANCE_ROUND:
                switch (mode) {
                    case CONTINUOUS:
                        provisionalOut.add(currentPlayer);
                        if (nextPlayers.size() == 1) {
                            currentPlayer = nextPlayers.remove();
                            state = State.GAME_POINT;
                        } else {
                            currentPlayer = nextPlayers.remove();
                            state = State.READY;
                        }
                        break;

                    case ROUNDS:
                        provisionalOut.add(currentPlayer);
                        if (nextPlayers.size() == 0) {
                            finishRound();
                        } else {
                            currentPlayer = nextPlayers.remove();
                            state = State.READY;
                        }
                        break;
                }
                break;

            case SP_READY:
                state = State.SP_GAME_OVER;
                break;

            case GAME_POINT:
                nextPlayers.addAll(provisionalOut);
                provisionalOut.clear();
                state = State.SECOND_CHANCE_ROUND;
                break;

            default:
                plugin.getLogger().info("unexpected state in TurnTracker.endTurnSuccess: " + state);
                break;
        }
    }

    private void finishRound() {
        if (prevPlayers.size() == 0) {
            nextPlayers.addAll(provisionalOut);
            provisionalOut.clear();
            currentPlayer = nextPlayers.remove();
            state = State.SECOND_CHANCE_ROUND;
        } else if (prevPlayers.size() == 1) {
            currentPlayer = prevPlayers.get(0);
            state = State.WINNER;
        } else {
            nextPlayers.addAll(prevPlayers);
            prevPlayers.clear();
            provisionalOut.clear();
            currentPlayer = nextPlayers.remove();
            state = State.NEW_ROUND;
        }
    }

    private void removalNewCurrentPlayer() {
        if (nextPlayers.size() > 0) {
            currentPlayer = nextPlayers.remove();
            state = State.READY;
        } else if (prevPlayers.size() > 0) {
            nextPlayers.addAll(prevPlayers);
            prevPlayers.clear();
            provisionalOut.clear();
            currentPlayer = nextPlayers.remove();
            state = State.NEW_ROUND;
        } else if (provisionalOut.size() > 0) {
            nextPlayers.addAll(provisionalOut);
            provisionalOut.clear();
            currentPlayer = nextPlayers.remove();
            state = State.SECOND_CHANCE_ROUND;
        } else {
            currentPlayer = null;
            state = State.STOPPED;
        }
    }

    private void removalStateFixup() {
        if (allPlayers.size() == 0) {
            currentPlayer = null;
            state = State.STOPPED;
        } else if (allPlayers.size() == 1) {
            // Game was two player, now single player
            // Remaining player can't have died,
            // otherwise, game would have ended.
            // Keep playing, or declare winner?
            state = State.WINNER;
        } else {
            switch (mode) {
                case CONTINUOUS:
                    if (nextPlayers.size() == 0) {
                        if (provisionalOut.size() == 0) {
                            state = State.WINNER;
                        } else {
                            state = State.GAME_POINT;
                        }
                    }
                    break;
                case ROUNDS:
                    if (nextPlayers.size() + prevPlayers.size() + provisionalOut.size() == 0) {
                        state = State.WINNER;
                    }
                    break;
            }
        }
    }

}
