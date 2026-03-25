package reasoning;

import environment.Task;

import java.util.*;
import java.util.function.Predicate;

/**
 * Classe LogicReasoner
 *
 * Implémente un moteur de raisonnement logique basé sur le chaînage avant
 * (Forward Chaining), une technique classique de l'IA symbolique (Nilsson, 1980).
 *
 * ─── Principe du Forward Chaining ───
 *   1. La base de faits (Working Memory) est initialisée avec l'état courant.
 *   2. Le moteur cherche parmi les règles celles dont les prémisses sont satisfaites.
 *   3. Les conclusions de ces règles sont ajoutées à la base de faits.
 *   4. Le processus itère jusqu'à saturation (plus aucune règle ne s'applique).
 *
 * ─── Structure d'une règle ───
 *   IF (condition sur les faits) THEN (conclusion → fait à ajouter)
 *   Ex : IF énergie_faible AND tâche_lourde_disponible → conclusion "recharge_d'abord"
 *
 * ─── Rôle dans le MAS ───
 *   Le LogicReasoner est utilisé par le PlanningBDIAgent pour :
 *   a) Filtrer les actions proposées par le MDP (veto logique)
 *   b) Dériver des faits implicites (inférence de contraintes cachées)
 *   c) Produire des justifications symboliques des décisions (explicabilité)
 *
 * Design Pattern : Strategy → le PlanningBDIAgent délègue le raisonnement
 * symbolique à cette classe (interchangeable avec d'autres raisonneurs).
 */
public class LogicReasoner {

    // ─── Base de règles (Knowledge Base) ───

    /**
     * Représente une règle logique de la forme IF condition THEN conclusion.
     * La condition est un prédicat sur la base de faits courante.
     */
    public static class Rule {
        private final String name;
        private final Predicate<Set<String>> condition;
        private final String conclusion;

        public Rule(String name, Predicate<Set<String>> condition, String conclusion) {
            this.name       = name;
            this.condition  = condition;
            this.conclusion = conclusion;
        }

        public String getName()        { return name; }
        public String getConclusion()  { return conclusion; }

        public boolean isApplicable(Set<String> facts) {
            return condition.test(facts);
        }
    }

    /** Base de règles (Knowledge Base) du raisonneur. */
    private final List<Rule> rules;

    /**
     * Constructeur.
     * Initialise la Knowledge Base avec les règles métier de la Smart Home.
     * Les règles encodent la connaissance d'un expert du domaine domestique.
     */
    public LogicReasoner() {
        this.rules = new ArrayList<>();
        initializeRules();
    }

    /**
     * Initialise la base de règles avec les règles métier de la Smart Home.
     *
     * Conventions pour les faits :
     *   "energy_low"             → énergie ≤ 40%
     *   "energy_critical"        → énergie ≤ 20%
     *   "task_heavy_available"   → une tâche de coût ≥ 7 est en pending
     *   "garbage_collected"      → la tâche "collect_garbage" est terminée
     *   "dirty_dishes_present"   → la tâche "wash_dishes" est en pending
     *   "bathroom_dirty"         → la tâche "clean_bathroom" est en pending
     *   "multiple_rooms_dirty"   → plusieurs pièces différentes ont des tâches
     *   "recharge_first"         → CONCLUSION : l'agent doit recharger avant d'agir
     *   "throw_garbage_unlocked" → CONCLUSION : jeter la poubelle est autorisé
     *   "prioritize_kitchen"     → CONCLUSION : la cuisine est prioritaire
     *   "rest_recommended"       → CONCLUSION : l'agent devrait se reposer
     *   "coordinate_needed"      → CONCLUSION : coordination avec un autre agent requise
     */
    private void initializeRules() {

        // ── Règle 1 : Énergie critique → recharger en priorité ──
        // IF énergie_critique THEN recharger_d'abord
        rules.add(new Rule(
            "R1_CriticalEnergyMustRecharge",
            facts -> facts.contains("energy_critical"),
            "recharge_first"
        ));

        // ── Règle 2 : Énergie faible + tâche lourde disponible → se reposer ──
        // IF énergie_faible AND tâche_lourde_disponible THEN repos_recommandé
        rules.add(new Rule(
            "R2_LowEnergyHeavyTask",
            facts -> facts.contains("energy_low") && facts.contains("task_heavy_available"),
            "rest_recommended"
        ));

        // ── Règle 3 : Dépendance poubelle (règle métier du sujet) ──
        // IF poubelle_collectée THEN jeter_poubelle_autorisé
        rules.add(new Rule(
            "R3_GarbaceCollectedBeforeThrow",
            facts -> facts.contains("garbage_collected"),
            "throw_garbage_unlocked"
        ));

        // ── Règle 4 : Vaisselle sale → priorité cuisine ──
        // IF vaisselle_sale_présente THEN priorité_cuisine
        rules.add(new Rule(
            "R4_DirtyDishesKitchenPriority",
            facts -> facts.contains("dirty_dishes_present"),
            "prioritize_kitchen"
        ));

        // ── Règle 5 : Plusieurs pièces sales → coordination nécessaire ──
        // IF plusieurs_pièces_sales THEN coordination_nécessaire
        rules.add(new Rule(
            "R5_MultipleRoomsNeedCoordination",
            facts -> facts.contains("multiple_rooms_dirty"),
            "coordinate_needed"
        ));

        // ── Règle 6 : Recharger recommandé ET coordination nécessaire → bloquer ──
        // IF recharger_d'abord AND coordination_nécessaire THEN bloquer_actions_lourdes
        // (règle chainée : déduite à partir de conclusions d'autres règles)
        rules.add(new Rule(
            "R6_ChainedRechargeAndCoordinate",
            facts -> facts.contains("recharge_first") && facts.contains("coordinate_needed"),
            "block_heavy_actions"
        ));

        // ── Règle 7 : Salle de bain sale → attention priorité modérée ──
        rules.add(new Rule(
            "R7_BathroomDirtyModerate",
            facts -> facts.contains("bathroom_dirty") && !facts.contains("dirty_dishes_present"),
            "prioritize_bathroom"
        ));
    }

    // ══════════════════════════════════════════════════════
    //  FORWARD CHAINING (ALGORITHME PRINCIPAL)
    // ══════════════════════════════════════════════════════

    /**
     * Exécute le chaînage avant sur une base de faits initiale.
     *
     * Algorithme :
     *   1. Partir des faits initiaux (Working Memory)
     *   2. Appliquer toutes les règles dont les prémisses sont satisfaites
     *   3. Ajouter les nouvelles conclusions à la Working Memory
     *   4. Itérer jusqu'à stabilisation (aucun nouveau fait dérivé)
     *
     * @param initialFacts Base de faits initiale (état du monde)
     * @return Ensemble étendu de faits après clôture logique
     */
    public Set<String> forwardChain(Set<String> initialFacts) {
        Set<String> workingMemory = new HashSet<>(initialFacts);
        boolean changed = true;
        int iteration   = 0;

        System.out.println("\n[LogicReasoner] === Forward Chaining ===");
        System.out.println("[LogicReasoner] Faits initiaux : " + workingMemory);

        while (changed) {
            changed = false;
            iteration++;

            for (Rule rule : rules) {
                if (rule.isApplicable(workingMemory)) {
                    String conclusion = rule.getConclusion();
                    if (workingMemory.add(conclusion)) {  // add() retourne true si nouveau
                        changed = true;
                        System.out.printf("[LogicReasoner] Iter %d | Règle '%s' → +%s%n",
                                iteration, rule.getName(), conclusion);
                    }
                }
            }
        }

        System.out.println("[LogicReasoner] Clôture atteinte. Faits dérivés : " + workingMemory);
        return workingMemory;
    }

    // ══════════════════════════════════════════════════════
    //  FILTRAGE DES ACTIONS (VETO LOGIQUE)
    // ══════════════════════════════════════════════════════

    /**
     * Filtre les actions proposées par le MDP en appliquant le veto logique.
     *
     * Certains faits dérivés bloquent certaines catégories d'actions :
     *   "recharge_first"       → bloque les tâches à coût > 5
     *   "block_heavy_actions"  → bloque les tâches à coût > 3
     *   "rest_recommended"     → bloque les tâches à coût > 6
     *
     * @param tasks         Tâches proposées par le MDPPlanner
     * @param derivedFacts  Faits dérivés par forward chaining
     * @return Sous-liste des tâches logiquement autorisées
     */
    public List<Task> filterActions(List<Task> tasks, Set<String> derivedFacts) {
        List<Task> allowed = new ArrayList<>();

        for (Task task : tasks) {
            boolean blocked = false;

            if (derivedFacts.contains("recharge_first") && task.getEnergyCost() > 5) {
                System.out.printf("[LogicReasoner] VETO sur '%s' (énergie critique, coût=%d>5)%n",
                        task.getName(), task.getEnergyCost());
                blocked = true;
            }

            if (derivedFacts.contains("block_heavy_actions") && task.getEnergyCost() > 3) {
                System.out.printf("[LogicReasoner] VETO sur '%s' (blocage lourd, coût=%d>3)%n",
                        task.getName(), task.getEnergyCost());
                blocked = true;
            }

            if (derivedFacts.contains("rest_recommended") && task.getEnergyCost() > 6) {
                System.out.printf("[LogicReasoner] VETO sur '%s' (repos recommandé, coût=%d>6)%n",
                        task.getName(), task.getEnergyCost());
                blocked = true;
            }

            if (!blocked) allowed.add(task);
        }

        System.out.printf("[LogicReasoner] %d/%d actions autorisées après filtrage logique.%n",
                allowed.size(), tasks.size());
        return allowed;
    }

    // ══════════════════════════════════════════════════════
    //  CONSTRUCTION DE LA BASE DE FAITS
    // ══════════════════════════════════════════════════════

    /**
     * Construit la base de faits initiale à partir de l'état courant de l'agent.
     *
     * Cette méthode fait le pont entre la représentation procédurale du MAS
     * (listes de tâches, niveau d'énergie) et la représentation symbolique
     * du raisonneur logique (ensemble de faits atomiques).
     *
     * @param pendingTasks   Tâches en attente
     * @param completedIds   IDs des tâches terminées
     * @param energyPoints   Énergie actuelle (0-100)
     * @return Base de faits initiale prête pour le forward chaining
     */
    public Set<String> buildFactBase(List<Task> pendingTasks,
                                     List<String> completedIds,
                                     int energyPoints) {
        Set<String> facts = new HashSet<>();

        // ── Faits sur l'énergie ──
        if (energyPoints <= 20) {
            facts.add("energy_critical");
            facts.add("energy_low");
        } else if (energyPoints <= 40) {
            facts.add("energy_low");
        }

        // ── Faits sur les tâches en attente ──
        Set<String> roomsWithTasks = new HashSet<>();
        for (Task task : pendingTasks) {
            roomsWithTasks.add(task.getTargetRoom());

            if (task.getEnergyCost() >= 7) {
                facts.add("task_heavy_available");
            }

            // Reconnaissance de tâches spécifiques par ID
            switch (task.getId()) {
                case "wash_dishes":
                case "washing_dishes":
                    facts.add("dirty_dishes_present");
                    break;
                case "clean_bathroom":
                    facts.add("bathroom_dirty");
                    break;
            }
        }

        if (roomsWithTasks.size() >= 2) {
            facts.add("multiple_rooms_dirty");
        }

        // ── Faits sur les tâches complétées ──
        if (completedIds.contains("collect_garbage")) {
            facts.add("garbage_collected");
        }

        return facts;
    }
}
