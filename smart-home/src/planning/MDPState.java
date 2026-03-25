package planning;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Classe MDPState
 *
 * Représente un état du Processus de Décision Markovien (MDP) modélisant
 * la situation courante de la Smart Home.
 *
 * Dans un MDP (Bellman, 1957), un état s ∈ S capture toute l'information
 * pertinente pour décider de l'action optimale. Ici, un état encode :
 *
 *   - Les tâches en attente (pour savoir ce qu'il reste à faire)
 *   - Les tâches terminées (pour vérifier les dépendances)
 *   - La pièce courante de l'agent (contrainte spatiale)
 *   - Le niveau d'énergie restant (contrainte de ressource)
 *
 * La propriété de Markov est respectée : connaître (pendingTaskIds, completedTaskIds,
 * currentRoom, energyLevel) suffit pour décider de l'action optimale, sans avoir
 * besoin de l'historique des états passés.
 *
 * equals() et hashCode() sont redéfinis pour permettre la recherche dans la
 * table de valeurs V(s) du MDPPlanner (HashMap<MDPState, Double>).
 */
public class MDPState {

    /** IDs des tâches encore en attente d'exécution. */
    private final List<String> pendingTaskIds;

    /** IDs des tâches déjà accomplies (utile pour vérifier les dépendances). */
    private final List<String> completedTaskIds;

    /** Pièce où se trouve l'agent (contrainte spatiale du cahier des charges). */
    private final String currentRoom;

    /**
     * Niveau d'énergie courant de l'agent (0-100).
     * Discrétisé en 5 paliers pour limiter la taille de l'espace d'états :
     *   [0-20] → "very_low", [21-40] → "low", [41-60] → "medium",
     *   [61-80] → "high", [81-100] → "full"
     */
    private final String energyLevel;

    /**
     * Constructeur principal.
     *
     * @param pendingTaskIds   IDs des tâches encore à faire
     * @param completedTaskIds IDs des tâches terminées
     * @param currentRoom      Pièce courante de l'agent
     * @param energyPoints     Énergie brute (0-100), discrétisée automatiquement
     */
    public MDPState(List<String> pendingTaskIds, List<String> completedTaskIds,
                    String currentRoom, int energyPoints) {
        this.pendingTaskIds   = new ArrayList<>(pendingTaskIds);
        this.completedTaskIds = new ArrayList<>(completedTaskIds);
        this.currentRoom      = currentRoom;
        this.energyLevel      = discretizeEnergy(energyPoints);
    }

    /**
     * Discrétise l'énergie brute en paliers symboliques.
     * La discrétisation réduit l'espace d'états continu à un espace fini,
     * condition nécessaire à la convergence de l'itération sur les valeurs.
     *
     * @param points Énergie brute (0-100)
     * @return Palier symbolique
     */
    public static String discretizeEnergy(int points) {
        if (points <= 20)  return "very_low";
        if (points <= 40)  return "low";
        if (points <= 60)  return "medium";
        if (points <= 80)  return "high";
        return "full";
    }

    /**
     * Vérifie si c'est un état terminal (plus aucune tâche en attente).
     * Dans le MDP, V(s_terminal) = 0 par convention.
     *
     * @return true si toutes les tâches sont accomplies
     */
    public boolean isTerminal() {
        return pendingTaskIds.isEmpty();
    }

    // ========================
    // ACCESSEURS
    // ========================

    public List<String> getPendingTaskIds()   { return pendingTaskIds; }
    public List<String> getCompletedTaskIds() { return completedTaskIds; }
    public String       getCurrentRoom()      { return currentRoom; }
    public String       getEnergyLevel()      { return energyLevel; }

    // ========================
    // EQUALS / HASHCODE
    // ========================

    /**
     * Deux états sont égaux si et seulement si toutes leurs composantes sont égales.
     * Indispensable pour utiliser MDPState comme clé dans une HashMap<MDPState, Double>.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MDPState)) return false;
        MDPState other = (MDPState) o;
        return Objects.equals(pendingTaskIds,   other.pendingTaskIds)
            && Objects.equals(completedTaskIds, other.completedTaskIds)
            && Objects.equals(currentRoom,      other.currentRoom)
            && Objects.equals(energyLevel,      other.energyLevel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pendingTaskIds, completedTaskIds, currentRoom, energyLevel);
    }

    @Override
    public String toString() {
        return String.format("MDPState{pending=%s, done=%s, room='%s', energy='%s'}",
                pendingTaskIds, completedTaskIds, currentRoom, energyLevel);
    }
}
