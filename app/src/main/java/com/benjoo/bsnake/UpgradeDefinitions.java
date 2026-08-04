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
                "Every meal has hidden value.",
                GameState.UpgradeRarity.COMMON, 3));
        list.add(new GameState.UpgradeCard(
                "food_sense", "Food Sense",
                "Food is more likely to appear near the snake.\nImproves with each stack.",
                "You always know where the next bite is.",
                GameState.UpgradeRarity.COMMON, 2));
        list.add(new GameState.UpgradeCard(
                "boss_hunter", "Boss Hunter",
                "Defeating a boss grants +5 bonus score per stack.",
                "The bigger they are, the greater the reward.",
                GameState.UpgradeRarity.COMMON, 2));
        list.add(new GameState.UpgradeCard(
                "lucky_fruit", "Lucky Fruit",
                "Every 10th normal food grants +5 bonus score.\nEach stack reduces the interval by 1 (minimum 8).",
                "Fortune ripens for the patient.",
                GameState.UpgradeRarity.COMMON, 2));
        list.add(new GameState.UpgradeCard(
                "trail_hunter", "Trail Hunter",
                "Trail Fruit gives +1 bonus score per stack.",
                "Follow the glittering path.",
                GameState.UpgradeRarity.COMMON, 3));
        list.add(new GameState.UpgradeCard(
                "focused_strike", "Focused Strike",
                "The first hit on each boss grants +2 bonus score per stack.",
                "A clean opening changes everything.",
                GameState.UpgradeRarity.COMMON, 2));

        // ---- RARE ----
        list.add(new GameState.UpgradeCard(
                "big_bite", "Big Bite",
                "Normal food gives +1 extra segment.",
                "Leave nothing behind.",
                GameState.UpgradeRarity.RARE, 1));
        list.add(new GameState.UpgradeCard(
                "light_appetite", "Light Appetite",
                "Every 5th food grants score but no growth.\nEach stack reduces the interval by 1 (minimum 2).",
                "Sometimes restraint is the fastest path forward.",
                GameState.UpgradeRarity.RARE, 3));
        list.add(new GameState.UpgradeCard(
                "quick_recovery", "Quick Recovery",
                "Defeating a boss restores 2 segments.",
                "Victory heals old wounds.",
                GameState.UpgradeRarity.RARE, 1));
        list.add(new GameState.UpgradeCard(
                "slow_pressure", "Slow Pressure",
                "Boss movement speed is reduced by 3% per stack.",
                "Steady pressure wears down any foe.",
                GameState.UpgradeRarity.RARE, 5));
        list.add(new GameState.UpgradeCard(
                "efficient_growth", "Efficient Growth",
                "Every 8th normal food gives one fewer segment.",
                "Grow only when it matters.",
                GameState.UpgradeRarity.RARE, 1));
        list.add(new GameState.UpgradeCard(
                "boss_bounty", "Boss Bounty",
                "Boss rewards grant +1 extra segment per stack.",
                "Great prey feeds many.",
                GameState.UpgradeRarity.RARE, 3));
        list.add(new GameState.UpgradeCard(
                "patient", "Patient Snake",
                "After 10 seconds without taking boss damage, gain +2 score per stack.",
                "Stillness has its own rewards.",
                GameState.UpgradeRarity.RARE, 4));
        list.add(new GameState.UpgradeCard(
                "greedy", "Greedy",
                "Normal food gives +2 score and +1 extra segment per stack.",
                "If some is good, more is better.",
                GameState.UpgradeRarity.RARE, 2));
        list.add(new GameState.UpgradeCard(
                "small_appetite", "Small Appetite",
                "Every 5th normal food gives no growth.",
                "Not every meal needs to be filling.",
                GameState.UpgradeRarity.RARE, 1));

        // ---- EPIC ----
        list.add(new GameState.UpgradeCard(
                "thick_skin", "Thick Skin",
                "Boss attacks remove 1 fewer segment.",
                "You've learned to take the hit.",
                GameState.UpgradeRarity.EPIC, 1));
        list.add(new GameState.UpgradeCard(
                "heavy_hit", "Heavy Hit",
                "Every 5th successful boss hit deals +1 damage.\nEach stack reduces the interval by 1 (minimum 3).",
                "Wait for the perfect strike.",
                GameState.UpgradeRarity.EPIC, 2));
        list.add(new GameState.UpgradeCard(
                "heavy_body", "Heavy Body",
                "Boss attacks remove 1 fewer segment, but snake speed\nincreases by 3% per stack.",
                "Strength comes with momentum.",
                GameState.UpgradeRarity.EPIC, 2));

        return Collections.unmodifiableList(list);
    }
}