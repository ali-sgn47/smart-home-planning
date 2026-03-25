package planning;

import environment.Task;

import java.util.*;

/**
 * Classe MDPPlanner
 *
 * Implémente un Processus de Décision Markovien (MDP) avec l'algorithme
 * d'Itération sur les Valeurs (Value Iteration, Bellman 1957).
 *
 * ─── Formalisme MDP ───
 *   Un MDP est un tuple (S, A, T, R, γ) où :
 *     S  = espace d'états (MDPState)
 *     A  = espace d'actions (IDs des tâches disponibles + "wait")
 *     T  = fonction de transition T(s, a, s') = P(s'|s,a) ∈ [0,1]
 *     R  = fonction de récompense R(s, a) ∈ ℝ
 *     γ  = facteur d'actualisation (discount factor) ∈ ]0,1[
 *
 * ─── Probabilités de transition réelles ───
 *   Le monde est partiellement stochastique :
 *     - P(succès nominal)    = 0.80  → la tâche se termine normalement
 *     - P(succès mais fatigué) = 0.10  → tâche finie mais -10 énergie bonus
 *     - P(échec / interruption) = 0.10  → tâche reremise en pending
 *
 *   Ces probabilités modélisent les incertitudes réelles d'un robot domestique :
 *   batterie qui se vide plus vite que prévu, obstacle imprévu, surface glissante.
 *
 * ─── Convergence ───
 *   L'itération sur les valeurs converge quand max|V_k+1(s) - V_k(s)| < ε.
 *   La politique π*(s) = argmax_a [ R(s,a) + γ·Σ T(s,a,s')·V*(s') ] est
 *   alors extraite par un simple greedy pass.
 *
 * Design Pattern : Strategy → le PlanningBDIAgent délègue tout le raisonnement
 * décisionnel à cette classe sans en connaître les détails (SRP + OCP).
 */
public class MDPPlanner {

    // ─── Hyperparamètres du MDP ───

    /** Facteur d'actualisation γ ∈ ]0,1[. Plus proche de 1 = agent myope. */
    private static final double GAMMA         = 0.95;

    /** Seuil de convergence ε. L'itération s'arrête quand Δ < EPSILON. */
    private static final double EPSILON       = 0.001;

    /** Nombre maximal d'itérations (garde-fou contre les boucles infinies). */
    private static final int    MAX_ITER      = 500;

    // ─── Probabilités de transition ───

    /** Probabilité que la tâche se termine avec succès et sans surprise. */
    private static final double P_SUCCESS     = 0.80;

    /**
     * Probabilité de succès mais avec fatigue accrue.
     * L'agent accomplit la tâche mais perd 10 points d'énergie supplémentaires.
     */
    private static final double P_TIRED       = 0.10;

    /**
     * Probabilité d'échec / interruption.
     * La tâche est remise dans la file "pending" (elle devra être refaite).
     */
    private static final double P_FAIL        = 0.10;

    // ─── Pénalités ───

    /** Pénalité de déplacement inter-pièces (coût du mouvement). */
    private static final double MOVE_PENALTY  = -1.0;

    /** Pénalité pour énergie très faible (décourage les actions risquées). */
    private static final double LOW_E_PENALTY = -3.0;

    // ─── Structures internes ───

    /** Table de valeurs V(s). Initialisée à 0, mise à jour par value iteration. */
    private Map<MDPState, Double> valueTable;

    /**
     * Politique optimale π*(s) : associe à chaque état l'ID de la tâche à exécuter.
     * Extraite après convergence de value iteration.
     */
    private Map<MDPState, String> policy;

    /** Catalogue des tâches connues du planificateur (référentiel partagé). */
    private Map<String, Task> taskCatalog;

    /**
     * Constructeur.
     * @param tasks Liste des tâches de la Smart Home (alimentée par le Main)
     */
    public MDPPlanner(List<Task> tasks) {
        this.valueTable  = new HashMap<>();
        this.policy      = new HashMap<>();
        this.taskCatalog = new HashMap<>();
        for (Task t : tasks) {
            taskCatalog.put(t.getId(), t);
        }
    }

    // ══════════════════════════════════════════════════════
    //  VALUE ITERATION
    // ══════════════════════════════════════════════════════

    /**
     * Lance l'algorithme d'Itération sur les Valeurs depuis un état initial.
     *
     * Complexité : O(MAX_ITER × |S| × |A|)
     * L'espace d'états est exploré dynamiquement (on-the-fly) à partir de
     * l'état initial, ce qui évite d'énumérer a priori tous les états possibles
     * (dont le nombre croît exponentiellement avec le nombre de tâches).
     *
     * @param initialState L'état courant de l'agent au moment de la planification
     */
    public void runValueIteration(MDPState initialState) {
        System.out.println("\n[MDPPlanner] === Démarrage Value Iteration ===");
        System.out.printf("[MDPPlanner] γ=%.2f  ε=%.4f  P(success)=%.2f  P(tired)=%.2f  P(fail)=%.2f%n",
                GAMMA, EPSILON, P_SUCCESS, P_TIRED, P_FAIL);

        // Exploration BFS pour découvrir les états atteignables
        Set<MDPState> visited   = new HashSet<>();
        Queue<MDPState> toVisit = new LinkedList<>();
        toVisit.add(initialState);

        while (!toVisit.isEmpty()) {
            MDPState s = toVisit.poll();
            if (visited.contains(s)) continue;
            visited.add(s);
            valueTable.put(s, 0.0);

            // Générer les états successeurs pour alimenter la BFS
            for (String actionId : getAvailableActions(s)) {
                for (MDPState successor : getSuccessorStates(s, actionId)) {
                    if (!visited.contains(successor)) {
                        toVisit.add(successor);
                    }
                }
            }
        }

        System.out.printf("[MDPPlanner] %d états atteignables découverts.%n", visited.size());

        // ─── Boucle principale de Value Iteration (équation de Bellman) ───
        int iter = 0;
        double delta;

        do {
            delta = 0.0;
            for (MDPState s : visited) {
                if (s.isTerminal()) continue;

                double oldV = valueTable.getOrDefault(s, 0.0);
                double bestQ = Double.NEGATIVE_INFINITY;

                for (String actionId : getAvailableActions(s)) {
                    double q = computeQValue(s, actionId);
                    if (q > bestQ) bestQ = q;
                }

                if (bestQ == Double.NEGATIVE_INFINITY) bestQ = 0.0;
                valueTable.put(s, bestQ);
                delta = Math.max(delta, Math.abs(bestQ - oldV));
            }
            iter++;
        } while (delta > EPSILON && iter < MAX_ITER);

        System.out.printf("[MDPPlanner] Convergence en %d itérations (Δ final = %.6f).%n", iter, delta);

        // ─── Extraction de la politique optimale ───
        extractPolicy(visited);
        System.out.println("[MDPPlanner] === Politique optimale extraite ===\n");
    }

    // ══════════════════════════════════════════════════════
    //  CALCUL DE LA Q-VALUE (équation de Bellman)
    // ══════════════════════════════════════════════════════

    /**
     * Calcule Q(s, a) = R(s, a) + γ · Σ_{s'} P(s'|s,a) · V(s').
     *
     * @param state    État courant s
     * @param actionId Action a (ID de la tâche à exécuter)
     * @return Valeur de l'action a depuis l'état s
     */
    private double computeQValue(MDPState state, String actionId) {
        double immediateReward = computeReward(state, actionId);
        double expectedFutureValue = 0.0;

        // Somme pondérée sur tous les états successeurs possibles
        Map<MDPState, Double> transitions = getTransitionDistribution(state, actionId);
        for (Map.Entry<MDPState, Double> entry : transitions.entrySet()) {
            double prob       = entry.getValue();
            double futureVal  = valueTable.getOrDefault(entry.getKey(), 0.0);
            expectedFutureValue += prob * futureVal;
        }

        return immediateReward + GAMMA * expectedFutureValue;
    }

    // ══════════════════════════════════════════════════════
    //  FONCTION DE TRANSITION T(s, a) → Distribution sur S
    // ══════════════════════════════════════════════════════

    /**
     * Retourne la distribution de probabilité sur les états successeurs.
     *
     * Trois résultats possibles modélisant l'incertitude du monde réel :
     *   1. Succès nominal    (P=0.80) : tâche retirée des pending, énergie réduite normalement
     *   2. Succès avec fatigue (P=0.10) : tâche retirée, mais énergie réduite davantage
     *   3. Échec             (P=0.10) : tâche reste en pending, énergie réduite quand même
     *
     * @param state    État courant
     * @param actionId ID de la tâche sélectionnée
     * @return Map<MDPState, Double> : distribution de probabilité sur les successeurs
     */
    private Map<MDPState, Double> getTransitionDistribution(MDPState state, String actionId) {
        Map<MDPState, Double> distribution = new HashMap<>();

        Task task         = taskCatalog.get(actionId);
        if (task == null) return distribution;

        int currentEnergy = energyLevelToPoints(state.getEnergyLevel());
        int normalCost    = task.getEnergyCost();
        int tiredCost     = normalCost + 10;  // fatigue supplémentaire

        // Listes pour construire les nouveaux états
        List<String> newPending    = new ArrayList<>(state.getPendingTaskIds());
        List<String> newCompleted  = new ArrayList<>(state.getCompletedTaskIds());

        // ── Résultat 1 : Succès nominal (P = 0.80) ──
        List<String> pending1   = new ArrayList<>(newPending);
        List<String> completed1 = new ArrayList<>(newCompleted);
        pending1.remove(actionId);
        completed1.add(actionId);
        MDPState success = new MDPState(pending1, completed1, task.getTargetRoom(),
                                        Math.max(0, currentEnergy - normalCost));
        distribution.merge(success, P_SUCCESS, Double::sum);

        // ── Résultat 2 : Succès mais fatigué (P = 0.10) ──
        List<String> pending2   = new ArrayList<>(newPending);
        List<String> completed2 = new ArrayList<>(newCompleted);
        pending2.remove(actionId);
        completed2.add(actionId);
        MDPState tired = new MDPState(pending2, completed2, task.getTargetRoom(),
                                      Math.max(0, currentEnergy - tiredCost));
        distribution.merge(tired, P_TIRED, Double::sum);

        // ── Résultat 3 : Échec – tâche remise en pending (P = 0.10) ──
        MDPState failed = new MDPState(new ArrayList<>(newPending), new ArrayList<>(newCompleted),
                                       state.getCurrentRoom(),
                                       Math.max(0, currentEnergy - normalCost));
        distribution.merge(failed, P_FAIL, Double::sum);

        return distribution;
    }

    // ══════════════════════════════════════════════════════
    //  FONCTION DE RÉCOMPENSE R(s, a)
    // ══════════════════════════════════════════════════════

    /**
     * Calcule la récompense immédiate R(s, a).
     *
     * Composantes :
     *   + Récompense de la tâche (dépend de sa priorité et de son énergie)
     *   - Pénalité de déplacement si l'agent doit changer de pièce
     *   - Pénalité si le niveau d'énergie est critique (décourage les actions risquées)
     *
     * @param state    État courant
     * @param actionId ID de la tâche cible
     * @return Récompense immédiate R(s, a)
     */
    private double computeReward(MDPState state, String actionId) {
        Task task = taskCatalog.get(actionId);
        if (task == null) return -1.0;

        double reward = task.getReward();

        // Pénalité de déplacement
        if (!task.getTargetRoom().equals(state.getCurrentRoom())) {
            reward += MOVE_PENALTY;
        }

        // Pénalité énergie critique
        if (state.getEnergyLevel().equals("very_low") || state.getEnergyLevel().equals("low")) {
            reward += LOW_E_PENALTY;
        }

        return reward;
    }

    // ══════════════════════════════════════════════════════
    //  ACTIONS ET POLITIQUE
    // ══════════════════════════════════════════════════════

    /**
     * Retourne les actions disponibles depuis un état donné.
     * Une action est disponible si : la tâche est en pending ET ses dépendances
     * sont satisfaites ET l'énergie n'est pas épuisée.
     *
     * @param state État courant
     * @return Liste des IDs de tâches exécutables
     */
    private List<String> getAvailableActions(MDPState state) {
        if (state.isTerminal()) return Collections.emptyList();

        List<String> actions = new ArrayList<>();
        int energy = energyLevelToPoints(state.getEnergyLevel());

        for (String taskId : state.getPendingTaskIds()) {
            Task task = taskCatalog.get(taskId);
            if (task == null) continue;

            // Vérifier les dépendances
            if (!task.areDependenciesSatisfied(state.getCompletedTaskIds())) continue;

            // Vérifier l'énergie minimale (au moins 5 points)
            if (energy < 5) continue;

            actions.add(taskId);
        }
        return actions;
    }

    /**
     * Extrait la politique optimale π* après convergence de value iteration.
     * π*(s) = argmax_a Q(s, a) pour tout état non terminal.
     *
     * @param states Ensemble des états explorés
     */
    private void extractPolicy(Set<MDPState> states) {
        for (MDPState s : states) {
            if (s.isTerminal()) continue;

            String bestAction = null;
            double bestQ      = Double.NEGATIVE_INFINITY;

            for (String actionId : getAvailableActions(s)) {
                double q = computeQValue(s, actionId);
                if (q > bestQ) {
                    bestQ      = q;
                    bestAction = actionId;
                }
            }

            if (bestAction != null) {
                policy.put(s, bestAction);
                System.out.printf("[MDPPlanner] π(%s) = %s  [Q=%.3f]%n", s, bestAction, bestQ);
            }
        }
    }

    /**
     * Interroge la politique optimale pour obtenir l'action recommandée.
     * Si l'état est inconnu (non exploré), on choisit la tâche de plus haute priorité.
     *
     * @param state État courant de l'agent
     * @return ID de la tâche recommandée, ou null si aucune action disponible
     */
    public String getBestAction(MDPState state) {
        // Recherche exacte dans la table de politique
        if (policy.containsKey(state)) {
            return policy.get(state);
        }

        // Fallback heuristique : tâche avec priorité la plus haute parmi les disponibles
        System.out.println("[MDPPlanner] État inconnu de la politique, fallback heuristique.");
        List<String> available = getAvailableActions(state);
        return available.stream()
                .map(taskCatalog::get)
                .filter(Objects::nonNull)
                .min(Comparator.comparingInt(Task::getPriority))
                .map(Task::getId)
                .orElse(null);
    }

    /**
     * Retourne la valeur estimée V(s) d'un état.
     * Utile pour le logging et le débogage.
     *
     * @param state État à évaluer
     * @return Valeur estimée (0.0 si état inconnu)
     */
    public double getStateValue(MDPState state) {
        return valueTable.getOrDefault(state, 0.0);
    }

    // ══════════════════════════════════════════════════════
    //  UTILITAIRES
    // ══════════════════════════════════════════════════════

    /**
     * Génère la liste des états successeurs possibles depuis (s, a).
     * Utilisé par la BFS d'exploration lors de value iteration.
     */
    private List<MDPState> getSuccessorStates(MDPState state, String actionId) {
        return new ArrayList<>(getTransitionDistribution(state, actionId).keySet());
    }

    /**
     * Convertit un palier d'énergie symbolique en points (milieu du palier).
     * Utilisé pour calculer les transitions d'énergie lors des actions.
     */
    private int energyLevelToPoints(String level) {
        switch (level) {
            case "very_low": return 10;
            case "low":      return 30;
            case "medium":   return 50;
            case "high":     return 70;
            case "full":     return 90;
            default:         return 50;
        }
    }
}
