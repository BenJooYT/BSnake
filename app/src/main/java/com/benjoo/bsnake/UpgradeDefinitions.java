package com.benjoo.bsnake;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// ---------------------------------------------------------------------------
// UPGRADE DEFINITION FILE
// ---------------------------------------------------------------------------
// The single place where every post-boss upgrade card lives. The UpgradeManager
// reads this pool to offer cards and applies each card's effect by id, so
// adding or rebalancing a card only requires an edit here.
//
// Each card supplies:
//   id           unique identifier the UpgradeManager keys its logic on
//   name         short title shown on the card
//   description  one/two line player-facing effect
//   flavor       short thematic quote shown beneath the effect
//   rarity       COMMON / RARE / EPIC — drives the card's visual styling
//   maxStack     how many times the card can be picked in a single run
// ---------------------------------------------------------------------------
public class UpgradeDefinitions {

    private static final List<GameState.UpgradeCard> ALL = build();

    private UpgradeDefinitions() { }

    public static List<GameState.UpgradeCard> getAll() {
        return ALL;
    }

    private static List<GameState.UpgradeCard> build() {
        ArrayList<GameState.UpgradeCard> list = new ArrayList<>();

        // ---- COMMON ----
        list.add(new GameState.UpgradeCard(
                "rich_food", "Rich Food",
                "Normal food gives +1 score per stack.",
                "Abundance forgives the slowest tongue.",
                GameState.UpgradeRarity.COMMON, 3));
        list.add(new GameState.UpgradeCard(
                "food_sense", "Food Sense",
                "Food is increasingly likely to spawn near you.",
                "The board whispers its secrets.",
                GameState.UpgradeRarity.COMMON, 2));
        list.add(new GameState.UpgradeCard(
                "boss_hunter", "Boss Hunter",
                "Boss defeat grants +5 bonus score per stack.",
                "Every crown owes a debt.",
                GameState.UpgradeRarity.COMMON, 2));
        list.add(new GameState.UpgradeCard(
                "lucky_fruit", "Lucky Fruit",
                "Every 10th normal food grants +5 score.\nEach stack lowers the interval by 1 (min 8).",
                "Fortune favors the hungry.",
                GameState.UpgradeRarity.COMMON, 2));
        list.add(new GameState.UpgradeCard(
                "trail_hunter", "Trail Hunter",
                "Trail fruit gives +1 bonus score per stack.",
                "Follow the crumbs of kings.",
                GameState.UpgradeRarity.COMMON, 3));
        list.add(new GameState.UpgradeCard(
                "focused_strike", "Focused Strike",
                "First successful hit on every boss grants +2 bonus score per stack.",
                "One clean cut ends the dance.",
                GameState.UpgradeRarity.COMMON, 2));

        // ---- RARE ----
        list.add(new GameState.UpgradeCard(
                "big_bite", "Big Bite",
                "Normal food gives +1 extra segment.",
                "Eat like tomorrow matters.",
                GameState.UpgradeRarity.RARE, 1));
        list.add(new GameState.UpgradeCard(
                "light_appetite", "Light Appetite",
                "Every 5th food grants score but no growth.\nEach stack lowers the interval by 1 (min 2).",
                "Less weight, quicker turns.",
                GameState.UpgradeRarity.RARE, 3));
        list.add(new GameState.UpgradeCard(
                "quick_recovery", "Quick Recovery",
                "Boss defeat restores 2 segments.",
                "The old skin always sheds.",
                GameState.UpgradeRarity.RARE, 1));
        list.add(new GameState.UpgradeCard(
                "slow_pressure", "Slow Pressure",
                "Boss movement speed reduced by 3% per stack.",
                "Gravity remembers who lingers.",
                GameState.UpgradeRarity.RARE, 5));
        list.add(new GameState.UpgradeCard(
                "efficient_growth", "Efficient Growth",
                "Every 8th normal food gives one fewer segment.",
                "Nature wastes nothing.",
                GameState.UpgradeRarity.RARE, 1));
        list.add(new GameState.UpgradeCard(
                "boss_bounty", "Boss Bounty",
                "Boss rewards give +1 extra segment per stack.",
                "Victory pays its own wages.",
                GameState.UpgradeRarity.RARE, 3));
        list.add(new GameState.UpgradeCard(
                "patient", "Patient Snake",
                "Every 10 seconds without taking boss damage, gain +2 score per stack.",
                "Stillness is its own prey.",
                GameState.UpgradeRarity.RARE, 4));
        list.add(new GameState.UpgradeCard(
                "greedy", "Greedy",
                "Normal food gives +2 score and +1 extra segment per stack.",
                "More is never enough.",
                GameState.UpgradeRarity.RARE, 2));
        list.add(new GameState.UpgradeCard(
                "small_appetite", "Small Appetite",
                "Every 5th normal food gives no growth.",
                "The fastest carry the least.",
                GameState.UpgradeRarity.RARE, 1));

        // ---- EPIC ----
        list.add(new GameState.UpgradeCard(
                "thick_skin", "Thick Skin",
                "Boss attacks remove 1 fewer segment.",
                "They sharpen blades on the old.",
                GameState.UpgradeRarity.EPIC, 1));
        list.add(new GameState.UpgradeCard(
                "heavy_hit", "Heavy Hit",
                "Every 5th successful boss hit deals +1 damage.\nEach stack lowers the interval by 1 (min 3).",
                "The mountain does not rush.",
                GameState.UpgradeRarity.EPIC, 2));
        list.add(new GameState.UpgradeCard(
                "heavy_body", "Heavy Body",
                "Boss damage removes 1 fewer segment.\nSnake speed increases by 3% per stack.",
                "Weight is a promise kept.",
                GameState.UpgradeRarity.EPIC, 2));

        return Collections.unmodifiableList(list);
    }
}
