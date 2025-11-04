package players.groupAF;


import core.AbstractGameState;
import core.AbstractPlayer;
import core.actions.AbstractAction;

import org.apache.hadoop.shaded.com.nimbusds.jose.shaded.json.JSONObject;
import players.basicMCTS.BasicMCTSPlayer;
import players.basicMCTS.BasicTreeNode;
import players.basicMCTS.BasicMCTSParams;
import players.groupAF.SGHeuristic;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class EnhancedSushiGoMCTS extends BasicMCTSPlayer {

    private static final boolean DEBUG_MODE = false; // ⚠️ SET FALSE FOR COMPETITION

    private int determinizationSamples = 10; // Number of state samples

    private FileWriter logWriter;
    private DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    public static PrintWriter csvLogWriter;

    static {
        try {
            String projectDir = System.getProperty("user.dir");
            File logDir = new File(projectDir + File.separator + "logs");

            if (!logDir.exists()) {
                logDir.mkdirs();
                System.out.println("✅ Created logs directory at: " + logDir.getAbsolutePath());
            }

            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
            csvLogWriter = new PrintWriter(new FileWriter("logs/decision_trace_" + timestamp + ".csv", true));
            csvLogWriter.println("round,turn,agent,card,score,chosen");
            csvLogWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ====================================================================
    // CONSTRUCTORS
    // ====================================================================

    public EnhancedSushiGoMCTS() {
        super();
        this.determinizationSamples = 10;
        // Use your custom heuristic
        this.setStateHeuristic(new SGHeuristic());
    }

    public EnhancedSushiGoMCTS(BasicMCTSParams params) {
        super(params);
        this.determinizationSamples = 10;
        this.setStateHeuristic(new SGHeuristic());
    }

    // Constructor for JSON configuration with custom sample count
    public EnhancedSushiGoMCTS(BasicMCTSParams params, int determinizationSamples) {
        super(params);
        this.determinizationSamples = determinizationSamples;
        this.setStateHeuristic(new SGHeuristic());
    }

    // ====================================================================
    // MAIN DECISION METHOD: MULTIPLE DETERMINIZATION
    // ====================================================================

    @Override
    public AbstractAction _getAction(AbstractGameState state, List<AbstractAction> possibleActions) {
        // Initialize logging
        if (DEBUG_MODE) {
            initLog(String.valueOf(getPlayerID()));
        }

        // ================================================================
        // TIER 2: MULTIPLE DETERMINIZATION IMPLEMENTATION
        // ================================================================

        long startTime = System.currentTimeMillis();
        long timeoutThreshold = 950; // Safety margin (1000ms - 50ms buffer)

        // Map to accumulate action values across determinizations
        Map<AbstractAction, Double> actionValues = new HashMap<>();
        for (AbstractAction action : possibleActions) {
            actionValues.put(action, 0.0);
        }

        int samplesCompleted = 0;

        // Run multiple determinizations
        for (int sample = 0; sample < determinizationSamples; sample++) {
            // SAFETY CHECK: Timeout protection
            if (System.currentTimeMillis() - startTime > timeoutThreshold) {
                if (DEBUG_MODE) {
                    System.err.println("⚠️ Determinization timeout after " + sample + " samples");
                }
                break;
            }

            // Step 1: Create a new determinized state
            // TAG's copy() automatically shuffles hidden information (opponent hands)
            AbstractGameState sampledState = state.copy(getPlayerID());

            // Step 2: Run MCTS on this determinization
            // Budget is automatically divided by determinizationSamples in params
            BasicTreeNode root = new BasicTreeNode(this, null, sampledState, rnd);

            root.mctsSearch();

            // Step 3: Extract and accumulate action values from this sample
            for (AbstractAction action : possibleActions) {
                double value = getActionValueFromTree(root, action);
                actionValues.put(action, actionValues.get(action) + value);
            }

            samplesCompleted++;
        }

        // SAFETY CHECK: Handle case where no samples completed
        if (samplesCompleted == 0) {
            System.err.println("❌ EMERGENCY: No determinization samples completed!");
            // Fallback to random action
            return possibleActions.get(rnd.nextInt(possibleActions.size()));
        }

        // Step 4: Select action with highest AVERAGE value across samples
        AbstractAction bestAction = null;
        double bestAvgValue = Double.NEGATIVE_INFINITY;

        for (Map.Entry<AbstractAction, Double> entry : actionValues.entrySet()) {
            double avgValue = entry.getValue() / samplesCompleted;
            if (avgValue > bestAvgValue) {
                bestAvgValue = avgValue;
                bestAction = entry.getKey();
            }
        }

        // ================================================================
        // LOGGING (for debugging/analysis only)
        // ================================================================

        if (DEBUG_MODE) {
            Map<AbstractAction, Double> debugScores = computeActionScores(state, possibleActions);
            try {
                logDecision(state, bestAction, debugScores);
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Visual delay for observation (DISABLE FOR COMPETITION)
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        }

        return bestAction;
    }

    /**
     * HELPER METHOD: Extract action value from MCTS tree
     *
     * This method accesses the BasicTreeNode's children map to find the child
     * corresponding to the given action, then returns its average value (Q/N).
     *
     * @param root The root node of the MCTS tree
     * @param action The action to get value for
     * @return Average value (totValue / nVisits) for this action, or 0.0 if not explored
     */
    private double getActionValueFromTree(BasicTreeNode root, AbstractAction action) {
        try {
            // Access the children map using reflection
            // (BasicTreeNode.children is package-private, not public)
            java.lang.reflect.Field childrenField = BasicTreeNode.class.getDeclaredField("children");
            childrenField.setAccessible(true);

            @SuppressWarnings("unchecked")
            Map<AbstractAction, BasicTreeNode> children = (Map<AbstractAction, BasicTreeNode>) childrenField.get(root);

            if (children == null || children.isEmpty()) {
                return 0.0; // No children expanded
            }

            // Get the child node for this action
            BasicTreeNode child = children.get(action);

            if (child == null) {
                return 0.0; // Action not explored in this determinization
            }

            // Access totValue and nVisits fields
            java.lang.reflect.Field totValueField = BasicTreeNode.class.getDeclaredField("totValue");
            totValueField.setAccessible(true);
            double totValue = totValueField.getDouble(child);

            java.lang.reflect.Field nVisitsField = BasicTreeNode.class.getDeclaredField("nVisits");
            nVisitsField.setAccessible(true);
            int nVisits = nVisitsField.getInt(child);

            if (nVisits == 0) {
                return 0.0; // Not visited (shouldn't happen but safety check)
            }

            // Return average value: Q/N
            return totValue / nVisits;

        } catch (NoSuchFieldException | IllegalAccessException e) {
            System.err.println("❌ Reflection error accessing BasicTreeNode fields: " + e.getMessage());
            e.printStackTrace();
            return 0.0; // Fallback to 0 if reflection fails
        }
    }

    // ====================================================================
    // LOGGING METHODS (existing code - keep as is)
    // ====================================================================

    private void initLog(String playerName) {
        if (!DEBUG_MODE) return;
        try {
            String filename = "logs/" + playerName + "_" + LocalDateTime.now().format(dtf) + ".log";
            logWriter = new FileWriter(filename, false);
            logWriter.write("---- Sushi Go Agent Log ----\n");
            logWriter.write("Player: " + playerName + "\n");
            logWriter.write("---------------------------------------\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void logDecision(AbstractGameState state, AbstractAction chosenAction, Map<AbstractAction, Double> scores) throws IOException {
        if (!DEBUG_MODE || logWriter == null || csvLogWriter == null) return;

        int round = state.getRoundCounter();
        int turn = state.getTurnCounter();
        String agent = "Agent" + getPlayerID();

        // JSON log
        JSONObject obj = new JSONObject();
        obj.put("round", round);
        obj.put("turn", turn);
        obj.put("agent", agent);
        obj.put("state", state.toString());
        obj.put("chosen", chosenAction.toString());

        JSONObject scoreObj = new JSONObject();
        for (Map.Entry<AbstractAction, Double> e : scores.entrySet()) {
            scoreObj.put(e.getKey().toString(), e.getValue());
        }
        obj.put("scores", scoreObj);

        logWriter.write(obj.toJSONString());
        logWriter.flush();

        // CSV log
        for (Map.Entry<AbstractAction, Double> e : scores.entrySet()) {
            String action = e.getKey().toString();
            double score = e.getValue();
            csvLogWriter.printf(
                    "%d,%d,%s,%s,%s,%.3f,%s,%.3f,%d,%d%n",
                    round,
                    turn,
                    agent,
                    action,
                    chosenAction.toString(),
                    score,
                    action.equals(chosenAction.toString()),
                    scores.values().stream().mapToDouble(v -> v).max().orElse(0.0),
                    state.getNPlayers(),
                    state.getRoundCounter()
            );
        }
        csvLogWriter.flush();

        // Regret calculation
        try (PrintWriter regretWriter = new PrintWriter(new FileWriter("logs/regret_log.csv", true))) {
            double regret = scores.get(chosenAction) - scores.values().stream().mapToDouble(v -> v).max().orElse(0.0);
            regretWriter.printf("%d,%d,%s,%.3f%n", round, turn, agent, regret);
            regretWriter.flush();
        }
    }

    private Map<AbstractAction, Double> computeActionScores(AbstractGameState state, List<AbstractAction> actions) {
        Map<AbstractAction, Double> map = new HashMap<>();
        SGHeuristic heuristic = new SGHeuristic();
        for (AbstractAction action : actions) {
            AbstractGameState copy = state.copy(getPlayerID());
            action.execute(copy);
            double score = heuristic.evaluateState(copy, getPlayerID());
            map.put(action, score);
        }
        return map;
    }



    // Partial observability handling (MANDATORY for 70%+)

    // Tier 3: Advanced enhancement (REQUIRED for 80%+)



}