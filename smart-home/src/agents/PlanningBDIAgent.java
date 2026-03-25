package agents;

import communication.SpeechAct;
import environment.Environment;
import environment.Task;
import planning.MDPPlanner;
import planning.MDPState;
import reasoning.LogicReasoner;

import java.util.*;

/**
 * Classe PlanningBDIAgent
 *
 * Étend l'architecture BDI existante en y intégrant deux couches supplémentaires :
 *   1. Un planificateur MDP (MDPPlanner) pour la décision sous incertitude
 *   2. Un raisonneur logique (LogicReasoner) pour le veto symbolique
 *
 * ─── Architecture ───
 *
 *   [Perception] → met à jour les Beliefs (croyances BDI)
 *        ↓
 *   [LogicReasoner] → Forward Chaining sur les faits → filtrage logique des actions
 *        ↓
 *   [MDPPlanner]   → Value Iteration → politique optimale π*(s)
 *        ↓
 *   [Décision BDI] → confronte Beliefs × Desires → forme une Intention
 *        ↓
 *   [Action]       → exécute l'intention, communique via SpeechAct
 *
 * Cette architecture hybride symbolique/numérique illustre le paradigme SOAR
 * (Laird, 1987) et les systèmes BDI avec planification (Rao & Georgeff, 1991).
 *
 * ─── Rôle dans le MAS ───
 *   Ce Coordinator enrichi délègue les tâches aux agents réactifs (CleanerBot)
 *   et apprenants (LearnerBot) en leur envoyant des SpeechActs directifs.
 *   Il choisit à qui déléguer en fonction de la priorité de la tâche :
 *     - Tâche critique (priority=1) → LearnerBot (plus adaptatif)
 *     - Tâche standard              → CleanerBot  (plus rapide)
 *
 * Implémente l'interface Agent (Design Pattern Strategy).
 */
public class PlanningBDIAgent implements Agent {

    // ─── Identité ───
    private final String name;
    private String currentRoom;

    // ─── Environnement ───
    private final Environment env;

    // ─── Composantes BDI ───

    /** Beliefs : modèle interne de l'état du monde. */
    private final Map<String, Object> beliefs;

    /** Desires : objectifs permanents de l'agent. */
    private final List<String> desires;

    /** Intentions : file d'actions engagées (plan courant). */
    private final Queue<String> currentPlan;

    // ─── Composantes de Planification ───

    /** Planificateur MDP : calcule la politique optimale π*(s). */
    private final MDPPlanner mdpPlanner;

    /** Raisonneur logique : filtre les actions par forward chaining. */
    private final LogicReasoner logicReasoner;

    /** Niveau d'énergie courant de l'agent (0-100). */
    private int energy;

    /** Indique si le MDP a déjà été exécuté pour l'état courant. */
    private boolean mdpComputed;

    /** Dernière action recommandée par le MDP. */
    private String mdpRecommendedAction;

    /**
     * Constructeur.
     *
     * @param name        Nom de l'agent (ex: "SmartCoordinator")
     * @param startRoom   Pièce de départ
     * @param env         Environnement partagé (Singleton)
     * @param allTasks    Liste complète des tâches connues à l'initialisation
     */
    public PlanningBDIAgent(String name, String startRoom, Environment env, List<Task> allTasks) {
        this.name          = name;
        this.currentRoom   = startRoom;
        this.env           = env;
        this.beliefs       = new HashMap<>();
        this.desires       = new ArrayList<>();
        this.currentPlan   = new LinkedList<>();
        this.energy        = 100;
        this.mdpComputed   = false;

        // Initialisation des composantes de planification
        this.mdpPlanner    = new MDPPlanner(allTasks);
        this.logicReasoner = new LogicReasoner();

        // Désir fondamental BDI : gérer et optimiser les tâches de la maison
        this.desires.add("OptimizeHomeTasks");
        this.desires.add("MaintainEnergyLevel");
    }

    // ══════════════════════════════════════════════════════
    //  PHASE 1 : PERCEPTION → mise à jour des Beliefs
    // ══════════════════════════════════════════════════════

    /**
     * L'agent perçoit l'environnement et met à jour sa base de croyances (Beliefs).
     * Les croyances alimentent ensuite le raisonneur logique et le planificateur MDP.
     *
     * @param env Environnement courant
     */
    @Override
    public void perceive(Environment env) {
        List<Task> pending    = env.getPendingTasks();
        List<String> doneIds  = env.getCompletedTaskIds();

        // Mise à jour des croyances BDI
        beliefs.put("pending_tasks",   pending);
        beliefs.put("completed_ids",   doneIds);
        beliefs.put("tasks_available", !pending.isEmpty());
        beliefs.put("energy_points",   this.energy);
        beliefs.put("current_room",    this.currentRoom);

        // Dériver des croyances de haut niveau
        beliefs.put("is_exhausted",    this.energy <= 20);
        beliefs.put("all_done",        pending.isEmpty());

        if (!pending.isEmpty()) {
            System.out.printf("[%s] (Perception) %d tâches en attente | énergie=%d%n",
                    name, pending.size(), energy);
        }
    }

    // ══════════════════════════════════════════════════════
    //  PHASE 2 : DÉCISION (BDI + Logique + MDP)
    // ══════════════════════════════════════════════════════

    /**
     * Phase de décision tri-couche :
     *
     *   Couche 1 – Logique : forward chaining pour filtrer les actions dangereuses.
     *   Couche 2 – MDP     : value iteration pour trouver la politique optimale.
     *   Couche 3 – BDI     : confrontation Beliefs/Desires pour former une Intention.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void decide() {
        boolean hasTasks = (boolean) beliefs.getOrDefault("tasks_available", false);
        boolean allDone  = (boolean) beliefs.getOrDefault("all_done", false);

        if (allDone || !hasTasks || !desires.contains("OptimizeHomeTasks")) return;
        if (!currentPlan.isEmpty()) return; // Plan déjà en cours

        List<Task>   pending    = (List<Task>)   beliefs.get("pending_tasks");
        List<String> doneIds    = (List<String>) beliefs.get("completed_ids");
        int          energyPts  = (int)          beliefs.get("energy_points");

        // ── Couche 1 : Raisonnement logique (veto symbolique) ──
        Set<String> facts       = logicReasoner.buildFactBase(pending, doneIds, energyPts);
        Set<String> derivedFacts= logicReasoner.forwardChain(facts);
        List<Task>  allowedTasks= logicReasoner.filterActions(pending, derivedFacts);

        if (allowedTasks.isEmpty()) {
            System.out.printf("[%s] (Logique) Toutes les actions sont bloquées. " +
                              "Intention : attendre / recharger.%n", name);
            currentPlan.add("Wait");
            return;
        }

        // ── Couche 2 : Planification MDP (si pas encore calculé) ──
        if (!mdpComputed) {
            List<String> pendingIds = new ArrayList<>();
            for (Task t : allowedTasks) pendingIds.add(t.getId());

            MDPState initialState = new MDPState(pendingIds, doneIds, currentRoom, energyPts);
            mdpPlanner.runValueIteration(initialState);

            MDPState currentMDPState = new MDPState(pendingIds, doneIds, currentRoom, energyPts);
            this.mdpRecommendedAction = mdpPlanner.getBestAction(currentMDPState);
            this.mdpComputed = true;

            System.out.printf("[%s] (MDP) Action optimale recommandée : %s%n",
                    name, mdpRecommendedAction);
        }

        // ── Couche 3 : Formation de l'Intention BDI ──
        // L'intention = déléguer l'action MDP à l'agent le plus adapté
        if (mdpRecommendedAction != null) {
            // Trouver la tâche correspondante dans les actions autorisées
            Task targetTask = allowedTasks.stream()
                    .filter(t -> t.getId().equals(mdpRecommendedAction))
                    .findFirst()
                    .orElse(allowedTasks.get(0));

            System.out.printf("[%s] (BDI) Intention formée → Déléguer '%s' (priorité %d)%n",
                    name, targetTask.getName(), targetTask.getPriority());

            // Encoder la délégation dans le plan
            currentPlan.add("Delegate:" + targetTask.getId() + ":" + targetTask.getPriority());
        }
    }

    // ══════════════════════════════════════════════════════
    //  PHASE 3 : ACTION → délégation via SpeechActs
    // ══════════════════════════════════════════════════════

    /**
     * Exécute le plan courant.
     * La stratégie de délégation dépend de la priorité de la tâche :
     *   - Priorité critique (1) → LearnerBot (plus adaptatif, apprend des erreurs)
     *   - Priorité standard     → CleanerBot  (réactif, plus rapide sur les tâches simples)
     *
     * @param env Environnement sur lequel agir
     */
    @Override
    public void act(Environment env) {
        if (currentPlan.isEmpty()) return;

        String intention = currentPlan.poll();

        if (intention.equals("Wait")) {
            System.out.printf("[%s] (Action) En attente / recharge simulée. +10 énergie.%n", name);
            this.energy = Math.min(100, this.energy + 10);
            this.mdpComputed = false; // Recalculer le MDP après repos
            return;
        }

        if (intention.startsWith("Delegate:")) {
            String[] parts    = intention.split(":");
            String   taskId   = parts[1];
            int      priority = Integer.parseInt(parts[2]);

            // Choisir le destinataire selon la priorité (stratégie de délégation)
            String receiver = (priority == 1) ? "LearnerBot" : "CleanerBot";

            System.out.printf("[%s] (Action) Délégation de '%s' → %s%n", name, taskId, receiver);

            // Créer un SpeechAct directif (Speech Act Theory - Austin & Searle)
            SpeechAct order = new SpeechAct(
                "directive",
                "Execute task: " + taskId,
                this.name,
                receiver
            );
            env.broadcastMessage(order);

            // Consomme un peu d'énergie pour la coordination
            this.energy = Math.max(0, this.energy - 1);

            // Réinitialiser pour le prochain cycle de planification
            this.mdpRecommendedAction = null;
            this.mdpComputed = false;
        }
    }

    /**
     * Traitement des messages reçus des autres agents.
     * @param message Message FIPA-ACL reçu
     */
    @Override
    public void receiveMessage(SpeechAct message) {
        System.out.printf("[%s] (Message) Reçu de %s : %s%n",
                name, message.getSender(), message.getContent());

        // Si un agent rapporte avoir complété une tâche, invalider le plan MDP
        if (message.getContent().startsWith("completed:")) {
            this.mdpComputed = false;
            System.out.printf("[%s] → Plan MDP invalidé, re-planification au prochain cycle.%n", name);
        }
    }

    /**
     * Boucle de vie autonome (Thread indépendant).
     */
    @Override
    public void run() {
        System.out.printf("[%s] PlanningBDIAgent démarré (BDI + MDP + LogicReasoner).%n", name);
        while (true) {
            try {
                SpeechAct msg = env.readMessageFor(this.name);
                if (msg != null) receiveMessage(msg);

                perceive(this.env);
                decide();
                act(this.env);

                Thread.sleep(2000); // Cycle plus long : le raisonnement prend du temps
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
