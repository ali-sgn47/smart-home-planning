package environment;

import java.util.ArrayList;
import java.util.List;

/**
 * Classe Task (enrichie pour la planification)
 *
 * En plus des contraintes spatiales du cahier des charges initial, cette classe
 * intègre désormais les attributs nécessaires à la planification sous incertitude :
 *
 * - priorité  : permet au MDP de définir une politique optimale (reward shaping)
 * - coût en énergie : modélise les ressources limitées (contrainte du sujet)
 * - dépendances : modélise les contraintes d'ordre entre tâches
 *   (ex : "Garbage must be collected before being thrown out")
 *
 * Ces attributs alimentent directement le MDPState et la fonction de récompense
 * du MDPPlanner.
 */
public class Task {

    /** Identifiant unique de la tâche (utile pour les dépendances). */
    private String id;

    /** Nom lisible de la tâche (ex: "Washing dishes"). */
    private String name;

    /** Contrainte spatiale : pièce où la tâche doit être effectuée. */
    private String targetRoom;

    /**
     * Priorité de la tâche (1 = critique, 2 = haute, 3 = normale).
     * Utilisée par le MDPPlanner pour calculer les récompenses immédiates.
     */
    private int priority;

    /**
     * Coût énergétique de la tâche (1-10).
     * Modélise la contrainte de ressources limitées du cahier des charges.
     * Le MDPPlanner pénalise les séquences d'actions trop coûteuses.
     */
    private int energyCost;

    /**
     * Liste des IDs de tâches qui doivent être complétées AVANT celle-ci.
     * Modélise les dépendances de tâches du cahier des charges.
     * Ex : "Collect garbage" doit précéder "Throw out garbage".
     */
    private List<String> dependencies;

    // ========================
    // CONSTRUCTEURS
    // ========================

    /**
     * Constructeur minimal (compatibilité avec l'implémentation existante).
     * Priorité par défaut : 3 (normale). Énergie : 1. Pas de dépendances.
     */
    public Task(String name, String targetRoom) {
        this(name, name.toLowerCase().replace(" ", "_"), targetRoom, 3, 1, new ArrayList<>());
    }

    /**
     * Constructeur complet pour la planification MDP.
     *
     * @param name        Nom de la tâche
     * @param id          Identifiant unique
     * @param targetRoom  Pièce cible
     * @param priority    Niveau de priorité (1=critique, 2=haute, 3=normale)
     * @param energyCost  Coût en énergie (1-10)
     * @param dependencies IDs des tâches prérequises
     */
    public Task(String name, String id, String targetRoom, int priority, int energyCost, List<String> dependencies) {
        this.name         = name;
        this.id           = id;
        this.targetRoom   = targetRoom;
        this.priority     = priority;
        this.energyCost   = energyCost;
        this.dependencies = dependencies != null ? dependencies : new ArrayList<>();
    }

    // ========================
    // MÉTHODES MÉTIER
    // ========================

    /**
     * Vérifie si toutes les dépendances de cette tâche sont satisfaites.
     * Utilisé par le MDPPlanner et le LogicReasoner avant d'autoriser l'exécution.
     *
     * @param completedTaskIds Ensemble des IDs de tâches déjà terminées
     * @return true si la tâche peut être lancée
     */
    public boolean areDependenciesSatisfied(List<String> completedTaskIds) {
        return completedTaskIds.containsAll(this.dependencies);
    }

    /**
     * Calcule la récompense immédiate associée à l'accomplissement de cette tâche.
     * Formule : base 10 − (priority−1)*3 − pénalité énergie.
     * Une tâche critique (priority=1) rapporte plus qu'une tâche normale (priority=3).
     *
     * @return Récompense pour le MDP
     */
    public double getReward() {
        double base = 10.0 - (priority - 1) * 3.0;
        double energyPenalty = energyCost * 0.5;
        return base - energyPenalty;
    }

    // ========================
    // ACCESSEURS
    // ========================

    public String getId()           { return id; }
    public String getName()         { return name; }
    public String getTargetRoom()   { return targetRoom; }
    public int    getPriority()     { return priority; }
    public int    getEnergyCost()   { return energyCost; }
    public List<String> getDependencies() { return dependencies; }

    @Override
    public String toString() {
        return String.format("Task{id='%s', room='%s', priority=%d, energy=%d}",
                id, targetRoom, priority, energyCost);
    }
}
