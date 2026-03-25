package environment;

import communication.SpeechAct;
import java.util.*;

/**
 * Classe Environment (Implémente le Design Pattern "Singleton")
 *
 * Dans les Systèmes Multi-Agents (SMA), l'environnement représente "tout ce qui n'est pas l'agent".
 * C'est l'espace partagé dans lequel les agents sont situés, où ils perçoivent, agissent,
 * et interagissent. Il sert également de médium pour la communication asynchrone.
 *
 * L'utilisation du Design Pattern Singleton garantit qu'il n'existe qu'une seule et unique
 * instance de cet environnement en mémoire, évitant ainsi les désynchronisations entre les
 * différents Threads des agents.
 */
public class Environment {

    // --- TOPOLOGIE ET OBJETS ---
    // Représentation de l'espace et des ressources physiques de la maison
    private List<String> rooms;
    private Map<String, String> objectLocations; // Ex: "Dishes" -> "Kitchen"

    // Instance unique du Singleton (Design Pattern)
    private static Environment instance;

    // Outils de nettoyage (Ressources de l'environnement)
    private List<String> cleaningTools;

    // --- GESTION DU CYCLE DE VIE DES TÂCHES ---
    // Respecte la modélisation des états d'une tâche exigée par le cahier des charges
    private List<Task> pendingTasks;   // Tâches en attente (non assignées)
    private List<Task> ongoingTasks;   // Tâches en cours d'exécution
    private List<Task> completedTasks; // Tâches terminées

    // --- COMMUNICATION ASYNCHRONE ---
    // Boîte aux lettres globale agissant comme un médium d'interaction pour les Actes de Langage
    private Queue<SpeechAct> messageQueue;

    /**
     * Constructeur privé (Design Pattern Singleton).
     * Initialise l'état "zéro" du monde (la maison intelligente) au lancement du système.
     * Le constructeur est privé pour empêcher toute instanciation externe avec "new".
     */
    private Environment() {
        this.rooms = new ArrayList<>(Arrays.asList("Kitchen", "LivingRoom", "Bathroom"));
        this.objectLocations = new HashMap<>();

        // Initialisation des listes d'états des tâches
        this.pendingTasks = new ArrayList<>();
        this.ongoingTasks = new ArrayList<>();
        this.completedTasks = new ArrayList<>();

        // Initialisation de la file de messages asynchrone
        this.messageQueue = new LinkedList<>();

        // Initialisation des ressources matérielles
        this.cleaningTools = new ArrayList<>(Arrays.asList("Vacuum Cleaner", "Sponge"));

        // Placement initial des objets dans l'environnement
        objectLocations.put("Dishes", "Kitchen");
        objectLocations.put("Garbage", "Kitchen");
    }

    /**
     * Méthode d'accès globale au Singleton.
     * Le mot-clé "synchronized" est crucial en IAD (Intelligence Artificielle Distribuée) :
     * il empêche deux Threads d'agents de créer deux environnements différents simultanément.
     *
     * @return L'instance unique de l'environnement
     */
    public static synchronized Environment getInstance() {
        if (instance == null) {
            instance = new Environment();
        }
        return instance;
    }

    // ==========================================
    // MÉTHODES DE GESTION DES TÂCHES
    // ==========================================

    /**
     * Permet à un agent de récupérer la première tâche disponible.
     * Le mot-clé "synchronized" empêche deux agents (ex: 2 robots) de prendre la même tâche
     * exactement au même millième de seconde (Race condition).
     *
     * @return La tâche à accomplir, ou null s'il n'y a plus rien à faire.
     */
    public synchronized Task getAvailableTask() {
        if (!pendingTasks.isEmpty()) {
            Task t = pendingTasks.remove(0); // L'agent prend la première tâche en attente
            ongoingTasks.add(t);             // La tâche passe en état "en cours"
            return t;
        }
        return null; // Retourne null s'il n'y a plus de tâche à faire
    }

    /**
     * Permet à un agent de modifier l'état de l'environnement en signalant la fin d'une tâche.
     *
     * @param t La tâche concernée
     * @param status Le nouveau statut (ex: "completed")
     */
    public synchronized void updateTaskStatus(Task t, String status) {
        if (status.equals("completed")) {
            ongoingTasks.remove(t);
            completedTasks.add(t);
            System.out.println("L'environnement enregistre la fin de la tâche.");
        }
    }

    /**
     * Ajoute une nouvelle tâche dans l'environnement (stimulus dynamique).
     * @param t La nouvelle tâche à effectuer
     */
    public synchronized void addPendingTask(Task t) {
        pendingTasks.add(t);
    }

    // ==========================================
    // MÉTHODES DE GESTION DE LA COMMUNICATION
    // ==========================================

    /**
     * Un agent dépose un message (Speech Act) dans le médium de l'environnement.
     * Cela permet une interaction indirecte et asynchrone entre les entités.
     *
     * @param msg L'acte de langage à diffuser
     */
    public synchronized void broadcastMessage(SpeechAct msg) {
        messageQueue.add(msg);
    }

    /**
     * Permet à un agent d'observer son environnement pour vérifier s'il a reçu du courrier.
     *
     * @param receiverName Le nom de l'agent qui consulte la boîte aux lettres
     * @return Le premier message qui lui est destiné, ou null s'il n'y a rien.
     */
    public synchronized SpeechAct readMessageFor(String receiverName) {
        // On cherche le premier message destiné à cet agent
        for (SpeechAct msg : messageQueue) {
            if (msg.getReceiver().equals(receiverName) || msg.getReceiver().equals("ALL")) {
                messageQueue.remove(msg); // On retire le message lu de la file (consommation)
                return msg;
            }
        }
        return null; // Pas de message pour lui
    }

    /**
     * Permet aux agents de percevoir visuellement les tâches restantes.
     * @return La liste des tâches en attente
     */
    public List<Task> getPendingTasks() {
        return this.pendingTasks;
    }

    /**
     * Retourne les IDs des tâches complétées.
     * Utilisé par le MDPPlanner et le LogicReasoner pour vérifier les dépendances.
     *
     * @return Liste des IDs de tâches terminées
     */
    public synchronized List<String> getCompletedTaskIds() {
        List<String> ids = new ArrayList<>();
        for (Task t : completedTasks) {
            ids.add(t.getId());
        }
        return ids;
    }
}