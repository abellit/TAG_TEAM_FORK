# Enhanced Sushi Go! MCTS Agent - Group AF

## Installation Instructions

1. Clone fresh TAG repository:
```bash
git clone https://github.com/GAIGResearch/TabletopGames
cd TabletopGames
```

2. Extract this ZIP into the players directory:
```bash
# Assuming GroupAF_Code.zip is in Downloads
unzip ~/Downloads/GroupAF_Code.zip -d src/main/java/players/
```

3. Compile:
```bash
mvn clean compile
```

## Quick Test

Run a single game:
```java
// In src/main/java/core/Game.java, modify main():
ArrayList players = new ArrayList<>();
players.add(new players.groupAF.EnhancedSushiGoMCTS());
players.add(new players.basicMCTS.BasicMCTSPlayer());
players.add(new players.simple.RandomPlayer());

runOne(GameType.valueOf("SushiGo"), players, seed, ac, false, listeners, 100);
```

## Tournament Configuration

Use provided JSON files in `json/players/groupAF/`:
```bash
java -cp target/classes evaluation.RunGames \
  config=json/experiments/groupAF_tournament.json
```

## Agent Components

### SGHeuristic.java (162 lines)
Domain-specific state evaluation with 4 weighted components:
- Current score advantage (45%): Normalized score difference vs leading opponent
- Potential completion (30%): Incomplete set values (Tempura pairs, Sashimi triplets)
- Opponent interference (15%): Maki roll competition positioning
- Pudding strategy (10%): Round-dependent long-term accumulation

### EnhancedSushiGoMCTS.java
Extended BasicMCTSPlayer with:
- Custom heuristic integration via setStateHeuristic()
- Multiple determinization implementation (N=10 samples)
- Logging infrastructure for decision analysis

Note: Determinization feature implemented but not fully validated due to
time constraints. Agent performs strongly with heuristic alone.

## Performance Summary

**2-Player vs BasicMCTS (500 games):**
- Win rate: 85-97% (p<0.001)
- Mean score: 52-54 vs 38-41 (baseline)

**3-Player vs RHEA (96 games):**
- Win rate: 6.3% vs 4.2% (parity, p=0.603)
- Mean score: 39.30 vs 39.78

## Key Parameters

- Budget: 100ms per decision (tested) / 1000ms (competition standard)
- Rollout length: 3 steps
- Exploration constant (K): 0.7
- Max tree depth: 30

## Troubleshooting

**Compilation errors:**
- Ensure Java 17+ installed: `java -version`
- Check Maven version: `mvn -v`
- Clean build: `mvn clean compile`

**Missing dependencies:**
- TAG framework includes all required libraries
- No external dependencies beyond TAG

**Agent not found in tournaments:**
- Verify JSON files reference correct class path: `players.groupAF.EnhancedSushiGoMCTS`
- Check package declaration matches directory structure

## Contact

For questions regarding this implementation, refer to the accompanying
report (GroupAF_Report.pdf) Section 3: Method for detailed algorithm
description.