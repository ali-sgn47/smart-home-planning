package learning;

import agents.Agent;
import communication.SpeechAct;
import environment.Environment;
import environment.Task;
import planning.MDPState;

import java.util.*;

/**
 * Classe QLearningAgent
 *
 * Implémente un agent d'apprentissage par renforcement (RL) utilisant l'algorithme
 * Q-Learning (Watkins & Dayan, 1992).
 *
 * ─── Principe du Q-Learning ───
 *   L'agent apprend une fonction Q(s, a) ∈ ℝ qui estime la valeur à long terme
 *   d'exécuter l'action a depuis l'état s. La mise à jour se fait par la règle :
 *
 *   Q(s,a) ← Q(s,a) + α · [r + γ · max_a' Q(s',a') − Q(s,a)]
 *
 *   où :
 *     α   = taux d'apprentissage (learning rate) ∈ ]0,1]
 *     γ   = facteur d'actualisation (discount factor) ∈ ]0,1[
 *     r   = récompense immédiate reçue après l'action
 *     s'  = état successeur observé
 *
 * ─── Différence avec le MDP ───
 *   Le MDP (MDPPlanner) planifie offline en connaissant T et R à l'avance.
 *   Le Q-Learning apprend online : il n'a pas accès à T(s,a,s') ni à R(s,a).
 *   Il les découvre par expérience directe avec l'environnement.
 *   → Le MDP est un planificateur. Le Q-Learning est un apprenant.
 *
 * ─── Politique ε-greedy ───
 *   Avec probabilité ε → exploration (action aléatoire)
 *   Avec probabilité 1-ε → exploitation (action avec Q maximal)
 *   ε décroît au fil du temps (ε_decay) pour favoriser l'exploitation à maturité.
 *
 * Implémente l'interface Agent (Design Pattern Strategy).
 */
public class QLearningAgent implements Agent {

    // ─── Hyperparamètres RL ───

    /** Taux d'apprentissage α : à quel point les nouvelles expériences écrasent l'ancien savoir. */
    private static final double ALPHA         = 0.1;

    /** Facteur d'actualisation γ : importance des récompenses futures. */
    private static final double GAMMA         = 0.95;

    /** Probabilité d'exploration initiale ε₀. */
    private static final double EPSILON_START = 0.9;

    /** Probabilité minimale d'exploration ε_min (toujours un peu d'exploration). */
    private static final double EPSILON_MIN   = 0.05;

    /** Taux de décroissance de ε par épisode (ε ← ε × EPSILON_DECAY). */
    private static final double EPSILON_DECAY = 0.995;

    // ─── État interne de l'agent ───

    private final String  name;
    private final Environment env;

    /** Q-table : Map<encodage_état, Map<actionId, Q-valeur>>. */
    private final Map<String, Map<String, Double>> qTable;

    /** Épisode courant (incrémenté à chaque cycle percevoir/décider/agir). */
    private int episode;

    /** Epsilon courant (décroissant au fil des épisodes). */
    private double epsilon;

    /** Niveau d'énergie courant de l'agent (0-100). */
    private int energy;

    /** Tâche sélectionnée lors de la phase decide(). */
    private Task selectedTask;

    /** État MDP observé lors de la phase perceive() (pour la mise à jour Q). */
    private MDPState previousState;

    /** Action exécutée lors du cycle précédent (pour la mise à jour Q). */
    private String previousAction;

    /** Générateur aléatoire pour la politique ε-greedy. */
    private final Random random;

    /**
     * Constructeur.
     *
     * @param name Nom de l'agent (ex: "LearnerBot")
     * @param env  Environnement partagé
     */
    public QLearningAgent(String name, Environment env) {
        this.name          = name;
        this.env           = env;
        this.qTable        = new HashMap<>();
        this.episode       = 0;
        this.epsilon       = EPSILON_START;
        this.energy        = 100;
        this.random        = new Random();
        this.selectedTask  = null;
        this.previousState = null;
        this.previousAction= null;
    }

    // ══════════════════════════════════════════════════════
    //  CYCLE PERCEVOIR / DÉCIDER / AGIR
    // ══════════════════════════════════════════════════════

    /**
     * Phase de Perception.
     * L'agent observe l'environnement et construit son état courant.
     * Si une action précédente existe, il met à jour la Q-table
     * avec la récompense observée (apprentissage online).
     *
     * @param env Environnement courant
     */
    @Override
    public void perceive(Environment env) {
        // Vérifier les messages entrants
        SpeechAct msg = env.readMessageFor(this.name);
        if (msg != null) receiveMessage(msg);

        // Construire l'état courant
        List<String> pendingIds   = new ArrayList<>();
        List<String> completedIds = env.getCompletedTaskIds();

        for (Task t : env.getPendingTasks()) {
            pendingIds.add(t.getId());
        }

        MDPState currentState = new MDPState(pendingIds, completedIds,
                                              "LivingRoom", this.energy);

        // ── Mise à jour Q-Learning (si une action précédente existe) ──
        if (previousState != null && previousAction != null) {
            double reward = computeReward(env, currentState);
            updateQTable(previousState, previousAction, reward, currentState);
        }

        this.previousState = currentState;
    }

    /**
     * Phase de Décision (politique ε-greedy).
     *
     * Avec probabilité ε : exploration → action aléatoire parmi les disponibles.
     * Avec probabilité 1-ε : exploitation → action avec Q(s,a) maximal.
     *
     * ε décroît à chaque épisode pour basculer progressivement vers l'exploitation.
     */
    @Override
    public void decide() {
        List<Task> available = env.getPendingTasks();
        if (available.isEmpty()) {
            selectedTask = null;
            return;
        }

        String stateKey = encodeState(previousState);

        if (random.nextDouble() < epsilon) {
            // ── Exploration : action aléatoire ──
            selectedTask = available.get(random.nextInt(available.size()));
            System.out.printf("[%s] (Exploration ε=%.3f) → tâche aléatoire : %s%n",
                    name, epsilon, selectedTask.getName());
        } else {
            // ── Exploitation : argmax Q(s,a) ──
            selectedTask = getBestTask(stateKey, available);
            System.out.printf("[%s] (Exploitation) → meilleure tâche Q : %s%n",
                    name, selectedTask.getName());
        }

        // Décroissance de ε après chaque épisode
        epsilon = Math.max(EPSILON_MIN, epsilon * EPSILON_DECAY);
        episode++;
    }

    /**
     * Phase d'Action.
     * L'agent exécute la tâche sélectionnée, modifie l'environnement,
     * et enregistre l'action pour la mise à jour Q du prochain cycle.
     *
     * @param env Environnement sur lequel agir
     */
    @Override
    public void act(Environment env) {
        if (selectedTask == null) return;

        Task task = env.getAvailableTask();
        if (task != null) {
            System.out.printf("[%s] (Action Q-Learning) → Exécution de '%s' (énergie-%d)%n",
                    name, task.getName(), task.getEnergyCost());

            // Simuler la consommation d'énergie
            this.energy = Math.max(0, this.energy - task.getEnergyCost());

            // Modifier l'environnement
            env.updateTaskStatus(task, "completed");

            // Mémoriser l'action pour la mise à jour Q du prochain cycle
            this.previousAction = task.getId();

            System.out.printf("[%s] Énergie restante : %d/100 | Épisode : %d | ε=%.3f%n",
                    name, this.energy, episode, epsilon);
        }
    }

    /**
     * Traitement des messages reçus.
     * @param message Message reçu
     */
    @Override
    public void receiveMessage(SpeechAct message) {
        if (message.getType().equals("directive")) {
            System.out.printf("[%s] (Message) Ordre de %s : %s%n",
                    name, message.getSender(), message.getContent());
        }
    }

    /**
     * Boucle de vie autonome de l'agent (Thread indépendant).
     */
    @Override
    public void run() {
        System.out.printf("[%s] Agent Q-Learning démarré (α=%.2f, γ=%.2f, ε₀=%.2f).%n",
                name, ALPHA, GAMMA, EPSILON_START);
        while (true) {
            try {
                perceive(this.env);
                decide();
                act(this.env);
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // ══════════════════════════════════════════════════════
    //  Q-TABLE : MISE À JOUR ET CONSULTATION
    // ══════════════════════════════════════════════════════

    /**
     * Met à jour la Q-table selon la règle de Bellman (Q-Learning update).
     *
     * Q(s,a) ← Q(s,a) + α · [r + γ · max_{a'} Q(s',a') − Q(s,a)]
     *
     * @param state      État s au moment de l'action
     * @param actionId   Action a exécutée
     * @param reward     Récompense r obtenue
     * @param nextState  État s' observé après l'action
     */
    private void updateQTable(MDPState state, String actionId, double reward, MDPState nextState) {
        String stateKey     = encodeState(state);
        String nextStateKey = encodeState(nextState);

        // Q actuel : Q(s, a)
        double currentQ = getQValue(stateKey, actionId);

        // Meilleure valeur future : max_{a'} Q(s', a')
        double maxNextQ = getMaxQValue(nextStateKey);

        // Règle de mise à jour Q-Learning
        double newQ = currentQ + ALPHA * (reward + GAMMA * maxNextQ - currentQ);

        // Enregistrer dans la table
        qTable.computeIfAbsent(stateKey, k -> new HashMap<>()).put(actionId, newQ);

        System.out.printf("[%s] Q-update: Q(%s,%s): %.3f → %.3f (r=%.2f, maxQ'=%.3f)%n",
                name, stateKey.substring(0, Math.min(20, stateKey.length())),
                actionId, currentQ, newQ, reward, maxNextQ);
    }

    /**
     * Retourne la Q-valeur d'un couple (état, action).
     * Valeur initiale optimiste : 0.0 (encourage l'exploration de nouvelles actions).
     */
    private double getQValue(String stateKey, String actionId) {
        return qTable.getOrDefault(stateKey, Collections.emptyMap())
                     .getOrDefault(actionId, 0.0);
    }

    /**
     * Retourne max_{a} Q(s, a) pour un état donné.
     * Utilisé dans le calcul de la cible de la mise à jour Q-Learning.
     */
    private double getMaxQValue(String stateKey) {
        Map<String, Double> actions = qTable.get(stateKey);
        if (actions == null || actions.isEmpty()) return 0.0;
        return Collections.max(actions.values());
    }

    /**
     * Sélectionne la tâche avec la Q-valeur maximale (exploitation).
     * En cas d'égalité, choisit la tâche de plus haute priorité.
     *
     * @param stateKey  Encodage de l'état courant
     * @param available Tâches disponibles dans l'environnement
     * @return Tâche sélectionnée
     */
    private Task getBestTask(String stateKey, List<Task> available) {
        Task best    = null;
        double bestQ = Double.NEGATIVE_INFINITY;

        for (Task task : available) {
            double q = getQValue(stateKey, task.getId());
            if (q > bestQ || (q == bestQ && best != null &&
                    task.getPriority() < best.getPriority())) {
                bestQ = q;
                best  = task;
            }
        }
        return best != null ? best : available.get(0);
    }

    // ══════════════════════════════════════════════════════
    //  UTILITAIRES
    // ══════════════════════════════════════════════════════

    /**
     * Encode un MDPState en chaîne de caractères pour servir de clé dans la Q-table.
     * L'encodage doit être déterministe : le même état produit toujours la même clé.
     *
     * Format : "pending:[id1,id2]|done:[id3]|room:Kitchen|energy:medium"
     */
    private String encodeState(MDPState state) {
        if (state == null) return "null_state";
        List<String> pending   = new ArrayList<>(state.getPendingTaskIds());
        List<String> completed = new ArrayList<>(state.getCompletedTaskIds());
        Collections.sort(pending);
        Collections.sort(completed);
        return "pending:" + pending + "|done:" + completed
             + "|room:" + state.getCurrentRoom()
             + "|energy:" + state.getEnergyLevel();
    }

    /**
     * Calcule la récompense immédiate observée après une action.
     * Basée sur les changements d'état entre deux perceptions.
     *
     * @param env          Environnement courant
     * @param currentState État courant (après l'action)
     * @return Récompense r
     */
    private double computeReward(Environment env, MDPState currentState) {
        // Récompense positive pour chaque tâche nouvellement complétée
        int newlyCompleted = currentState.getCompletedTaskIds().size()
                           - previousState.getCompletedTaskIds().size();
        double reward = newlyCompleted * 8.0;

        // Pénalité si l'énergie a trop baissé
        if (currentState.getEnergyLevel().equals("very_low")) reward -= 5.0;
        if (currentState.getEnergyLevel().equals("low"))      reward -= 2.0;

        // Pénalité si rien n'a été fait
        if (newlyCompleted == 0) reward -= 1.0;

        return reward;
    }

    /**
     * Affiche les statistiques d'apprentissage de l'agent.
     * Utile pour le rapport et le débogage.
     */
    public void printStats() {
        System.out.printf("%n[%s] === Statistiques Q-Learning ===%n", name);
        System.out.printf("  Épisodes  : %d%n", episode);
        System.out.printf("  ε final   : %.4f%n", epsilon);
        System.out.printf("  États dans la Q-table : %d%n", qTable.size());
        int totalEntries = qTable.values().stream().mapToInt(Map::size).sum();
        System.out.printf("  Entrées Q(s,a) totales : %d%n", totalEntries);
    }
}
