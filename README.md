Jump Game Plugin
================

Version 0.3 for Bukkit 1.6.2-R1.0

This plugin runs a simple mini-game. Players jump from a high platform 
into a shallow pool. After every jump, one block in the pool is replaced 
with obsidian. The last player alive wins.

How to Play
-----------

1. Each person who wants to play pushes the join game button.

2. When everyone is ready, push the start game button. The order in 
which players will jump is random.

3. The first jumper is teleported to the jump platform. The other 
players are teleported to the observation/waiting area (if one has been 
set).

4. The jumper attempts to jump into the water. If they die from fall 
damage (or any other reason), they are out. If they land in the water 
and survive, then once they get out of the water, the water block that 
they landed in is replaced with obsidian.

5. The next jumper is teleported to the jump platform, the last jumper 
(if they survived) is teleported to the waiting area, and the game 
continues from step 4.

6. In order to win, the last player alive must survive a final jump.
If the last player misses the final jump, then every player who died
since the last successful jump gets another chance.

Set up
------

1. Install the jump game plugin jar file to your bukkit server's plugins 
directory and restart/reload.

2. Build a jump arena with a high platform, a landing pool, and at least 
two buttons (join and start). Optional: an observation/waiting area; a 
reset button.

3. Do "/jumpSetJoin" and left-click on the join button.

4. Do "/jumpSetStart" and left-click on the start button.

5. Optional: do "/jumpSetReset" and left-click on the reset button.

6. Stand in the landing pool and do "/jumpSetPool". NOTE: this remembers 
the blocks that make up the pool at the time you give the command. If 
you change the pool (add or remove), you should do /jumpSetPool again to 
record the new pool configuration.

7. Stand on the jump platform and do "/jumpSetJump". The position and 
direction you are facing is recorded, so stand exactly where jumpers 
should be teleported and face the direction they should face (probably 
toward the landing pool).

8. Optional: stand in the waiting area and do "/jumpSetWait". As above, 
position and facing direction are saved.

9. Optional: stand where dead players should respawn and do 
"/jumpSetSpawn". Any player who dies in the neighborhood of this point 
will respawn there. As above, position and facing direction are saved.

Other Configuration Options
---------------------------

The plugin configuration file (plugins/JumpGame/config.yml) will store 
the settings described above in "Set up". In addition, the following 
options can be set there:

- jumpTimeout: time in ticks that the jumper has to jump after being 
teleported to the jump platform. When time runs out, the jumper is set 
on fire to force them to either jump in the pool or die and get 
eliminated.

- jumpHardTimeout: time in ticks that the jumper has to jump after
the first timeout. When this timeout expires, the jumper is removed
from the game immediately, no second chances.

- exitPoolTimeout: time in ticks that the jumper has to get out of the 
pool after a successful jump. When time runs out, the jumper is 
teleported out (to the waiting area if one is set) and the next player's 
turn begins.

- startDelay: time in seconds from when the start button is pushed until 
the final three-second countdown to game start. For example, if 
startDelay is 7, the game will start 10 seconds after the start button 
is pushed (7 second delay plus 3 second countdown).

- poolSizeLimit: maximum size of the landing pool

- respawnDist: any player who dies within this distance from the 
(optional) respawn point will respawn there

Command Reference
-----------------

All commands require op status.

/jumpAddPlayer: Add player(s) to the game. If no player name is given, 
add self.

/jumpList: List players currently in the game.

/jumpReset: Reset the game. Removes all players and sets all landing 
pool blocks back to water.

/jumpRmPlayer: Remove player(s) from the game. If no player name is 
given, remove self.

/jumpSetJoin: Set the join game button.

/jumpSetJump: Set jump platform teleport location.

/jumpSetPool: Set the landing pool.

/jumpSetReset: Set the game reset button.

/jumpSetSpawn: Set the place where players who die will respawn.

/jumpSetStart: Set the start game button.

/jumpSetWait: Set waiting area teleport location.

/jumpStart: Start the game.


Change log
----------

0.3:
- Added delay between pushing start button and actual game start.
  Players can still join the game during the delay.
- Added more info to game messages, like number of players remaining
  and eliminations.
- Made game messages prettier.

0.2.1:
- Fixed problem with timeouts on players who disconnect.
- Fixed the jumper getting teleported to the jump spot again if another
  player disconnects.
- Added separate config value for the hard jump timeout.

0.2:
- Give all players who died since the last good jump a second chance
  instead of just the last two players.
- Always leave the last block of water in the pool as water rather than
  changing it to obsidian and ending the game.
- Added "hard" jump timeout in case the "soft" timeout is ineffective.
- Improved handling of removing players from game in progress.
- Improved in-game messages.
- Added checks to set-up commands to ensure all game elements are in
  the same world.
- Updated to Bukkit 1.6.2-R1.0.
