package players.groupAF;


import core.AbstractGameState;
import core.AbstractPlayer;
import core.actions.AbstractAction;
import core.interfaces.IStateHeuristic;
import org.apache.hadoop.shaded.com.nimbusds.jose.shaded.json.JSONObject;
import players.basicMCTS.BasicMCTSPlayer;

import players.mcts.MCTSPlayer;
import utilities.Pair;


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

    private static final boolean DEBUG_MODE = true;
    private FileWriter logWriter;
    private DateTimeFormatter  dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    public static PrintWriter csvLogWriter;

    public EnhancedSushiGoMCTS() {
        super();
    }



    static {
        try {
            // Create a timestamped tournament log so files don’t overwrite
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
            csvLogWriter = new PrintWriter(new FileWriter("logs/decision_trace_" + timestamp + ".csv", true));

            // Write header once
            csvLogWriter.println("round,turn,agent,card,score,chosen");
            csvLogWriter.flush();
        } catch (IOException e) { e.printStackTrace(); }
    }

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

        // --- JSON log ---
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

        // --- CSV log ---
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
                    scores.values().stream().mapToDouble(v -> v).max().orElse(0.0), // best score
                    state.getNPlayers(),
                    state.getRoundCounter()
            );

        }
        csvLogWriter.flush();

        // --- Regret calculation ---
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


    @Override
    public AbstractAction _getAction(AbstractGameState state, List<AbstractAction> possibleActions) {
        initLog(String.valueOf(getPlayerID()));


        Map<AbstractAction, Double> debugScores = computeActionScores(state, possibleActions);


        AbstractAction chosen = super.getAction(state, possibleActions);


        try {
            logDecision(state, chosen, debugScores);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        if (DEBUG_MODE) {
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        }


        return chosen;
    }

    // Tier 1: Custom heuristic (MANDATORY - enables 60-70% equivalent to a pass)

    // Tier 2: Partial observability handling (MANDATORY for 70%+)

    // Tier 3: Advanced enhancement (REQUIRED for 80%+)



}