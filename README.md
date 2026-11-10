**Branch:** `feature/weapon-modes`

**Titel:**

Add weapon fire modes and round impacts

**Beschreibung:**

This builds on my bug-fix PR (the explosion-block fix is needed for the HE rounds), so until that one is merged, its commits also show up here.

## Weapon fire modes (key X)

The original `KeySwitchWeaponMode`: weapons with `ModeNum` > 1 can now switch modes, and `FixMode` is respected (like the reference, a `FixMode` that is not below `ModeNum` is ignored).

- **MachineGun2** (e.g. M230, 2A42): the normal mode fires non-explosive rounds (only the blast sound, like the original), `[HE]` fires explosive rounds.
- **Rocket**: only the `[HEIAP]` mode pierces blocks.
- **ATMissile**: `[TA]` = top attack, with the original three-point profile (climb above the shooter, back down, then onto the target, +20% damage / +1 blast).
- **TVMissile**: the mode can be switched, and the normal mode flies 1.5x faster like in the original. The TV guidance itself comes in a separate PR.
- The mode shows up as a suffix in the HUD weapon name and on the action bar. Switching blocks the trigger for 15 ticks (reference `countWait`).

## Round impacts

- Explosive rounds of MachineGun-type weapons (chain guns and cannons) explode on contact and their blasts drop no items, so sustained fire doesn't litter the ground. This runs in an `ExplosionEvent.Detonate` listener at the lowest priority, so protection mods prune the block list first. It only removes blocks when vanilla would break them too (mobGriefing / the grief event). Flaming blasts still light fire.
- Rounds fly through leaves and plants (`#mcheli:bullet_passthrough`) and shatter glass panes on the way (`#mcheli:bullet_breakable`, like the original `BulletBreakableBlocks`). Both are block tags, so datapacks can extend them. Shattering respects mobGriefing via NeoForge's grief event.
- Blasts detonate a tiny bit outside the struck block face. Before, mobs right next to an impact took no splash damage.

## Testing

- `:mcheli-agnostic:test`: all tests pass, including the new `WeaponModeTest`.
- New headless self test `DemoControlsSelfTest` (`./gradlew :neoforge-1.21.1:runServer -PmcheliSelfTests`, like `DemoCrushSelfTest`): M230 normal/[HE] rounds and the block interaction (leaves, HE without drops, glass panes, nothing breaks with mobGriefing off). Passes.
- I tested this branch in-game (singleplayer).

## Note on AI usage

I used AI (Claude by Anthropic) to write the code, and had it reviewed again before opening this PR. I reviewed the changes, tested them in-game and ran the tests. Feel free to ask if anything looks off. I'm happy to change it.
