package players.groupAF;

import core.AbstractGameState;
import core.interfaces.IStateHeuristic;
import games.sushigo.*;
import games.sushigo.cards.SGCard;
import games.sushigo.cards.SGCard.SGCardType;

public class SGHeuristic implements IStateHeuristic {

    /// Weighting factors (TUNE THESE via experiments)
    private static final double CURRENT_SCORE_WEIGHT = 0.57;
    private static final double POTENTIAL_SCORE_WEIGHT = 0.18;
    private static final double OPPONENT_INTERFERENCE_WEIGHT = 0.15;
    private static final double PUDDING_STRATEGY_WEIGHT = 0.10;

    private boolean DEBUG = true;

    @Override
    public double evaluateState(AbstractGameState gs, int playerId) {
        SGGameState state = (SGGameState) gs;

        // Component 1: Current score advantage
        double currentScore = evaluateCurrentScore(state, playerId);

        // Component 2: Potential scoring opportunities
        double potentialScore = evaluatePotentialScore(state, playerId);

        // Component 3: Opponent interference capability
        double opponentInterference = evaluateOpponentInterference(state, playerId);

        // Component 4: Pudding long-term strategy
        double puddingScore = evaluatePuddingStrategy(state, playerId);

        // Weighted combination
        double total = CURRENT_SCORE_WEIGHT * currentScore +
                POTENTIAL_SCORE_WEIGHT * potentialScore +
                OPPONENT_INTERFERENCE_WEIGHT * opponentInterference +
                PUDDING_STRATEGY_WEIGHT * puddingScore;

        if (DEBUG) {
            System.out.printf(
                    "Player %d | ROUND %d | Total=%.3f | Current Score=%.3f Potential Score=%.3f Opponent Interference=%.3f Pudding Score=%.3f%n",
                    playerId, state.getRoundCounter(), total, currentScore, potentialScore, opponentInterference, puddingScore
            );
        }

        return total;
    }

    private double evaluateCurrentScore(SGGameState state, int playerId) {
        double myScore = state.getGameScore(playerId);
        double maxOpponentScore = 0;

        for (int i = 0; i < state.getNPlayers(); i++) {
            if (i != playerId) {
                maxOpponentScore = Math.max(maxOpponentScore, state.getGameScore(i));
            }
        }

        // Normalize to [-1, 1] range
        double scoreDiff = myScore - maxOpponentScore;
        return Math.tanh(scoreDiff / 20.0); // Sigmoid scaling
    }


    private double evaluatePotentialScore(SGGameState state, int playerId) {
        double potential = 0.0;

        // Check Tempura pairs (need 2 for 5pts)
        int tempuraCount = state.getPlayedCardTypes(SGCardType.Tempura, playerId).getValue();
        if (tempuraCount == 1) potential += 2.5; // Half value, incentivize completion

        // Check Sashimi sets (need 3 for 10pts)
        int sashimiCount = state.getPlayedCardTypes(SGCardType.Sashimi, playerId).getValue();
        if (sashimiCount == 1) potential += 2.0;
        if (sashimiCount == 2) potential += 5.0; // Strong incentive to complete

        // Check Dumpling progression
        int dumplingCount = state.getPlayedCardTypes(SGCardType.Dumpling, playerId).getValue();
        potential += evaluateDumplingValue(dumplingCount);

        // Wasabi without Nigiri (lost opportunity)
        int wasabiCount = state.getPlayedCardTypes(SGCardType.Wasabi, playerId).getValue();
        int nigiriTypes = countNigiriTypes(state, playerId);
        potential -= Math.max(0, wasabiCount - nigiriTypes) * 0.8; // Penalty for unused Wasabi

        // Normalize
        return Math.tanh(potential / 15.0);
    }

    private double evaluateDumplingValue(int count) {
        // Marginal value: 1,2,3,4,5pts for each additional dumpling
        int[] dumplingScores = {0, 1, 3, 6, 10, 15};
        if (count >= 5) return 5.0; // Max value reached

        int currentValue = dumplingScores[Math.min(count, 5)];
        int nextValue = dumplingScores[Math.min(count + 1, 5)];
        return nextValue - currentValue; // Marginal benefit
    }

    private int countNigiriTypes(SGGameState state, int playerId) {
        int count = 0;
        count += state.getPlayedCardTypes(SGCardType.EggNigiri, playerId).getValue();
        count += state.getPlayedCardTypes(SGCardType.SalmonNigiri, playerId).getValue();
        count += state.getPlayedCardTypes(SGCardType.SquidNigiri, playerId).getValue();
        return count;
    }

    private double evaluateOpponentInterference(SGGameState state, int playerId) {
        // Check Maki roll competition
        int myMaki = state.getPlayedCardTypes(SGCardType.Maki, playerId).getValue();
        int maxOpponentMaki = 0;
        int secondMaxOpponentMaki = 0;

        for (int i = 0; i < state.getNPlayers(); i++) {
            if (i != playerId) {
                int opponentMaki = state.getPlayedCardTypes(SGCardType.Maki, i).getValue();
                if (opponentMaki > maxOpponentMaki) {
                    secondMaxOpponentMaki = maxOpponentMaki;
                    maxOpponentMaki = opponentMaki;
                } else if (opponentMaki > secondMaxOpponentMaki) {
                    secondMaxOpponentMaki = opponentMaki;
                }
            }
        }

        // Reward being in 1st or 2nd place for Maki
//        double makiAdvantage = 0.0;
//        if (myMaki > maxOpponentMaki) makiAdvantage = 1.0; // First place
//        else if (myMaki > secondMaxOpponentMaki) makiAdvantage = 0.5; // Second place
//
//        return makiAdvantage;
        double makiDiff = myMaki - maxOpponentMaki;
        return Math.tanh(makiDiff / 5.0);
    }

    private double evaluatePuddingStrategy(SGGameState state, int playerId) {
        int round = state.getRoundCounter();
        int myPudding = state.getPlayedCardTypesAllGame()[playerId]
                .get(SGCardType.Pudding)
                .getValue();
        int maxOpponentPudding = 0;
        int minOpponentPudding = Integer.MAX_VALUE;

        for (int i = 0; i < state.getNPlayers(); i++) {
            if (i != playerId) {
                int opponentPudding = state.getPlayedCardTypesAllGame()[i]
                        .get(SGCardType.Pudding)
                        .getValue();
                maxOpponentPudding = Math.max(maxOpponentPudding, opponentPudding);
                minOpponentPudding = Math.min(minOpponentPudding, opponentPudding);
            }
        }

        // Reward being ahead (positive at end) or avoiding being last (negative at end)
        double puddingAdvantage = 0.0;
        if (round < 3) {
            if (myPudding < minOpponentPudding) puddingAdvantage = -0.125;
            else puddingAdvantage = 0.125;
        } else {
            if (myPudding > maxOpponentPudding) puddingAdvantage = 1.0; // Ahead
            else if (myPudding > minOpponentPudding) puddingAdvantage = 0.0; // Middle (safe)
            else puddingAdvantage = -1.0; // Last place risk
        }

        return puddingAdvantage;
    }
}