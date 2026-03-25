package agents;

import environment.Environment;
import environment.Task;
import communication.SpeechAct;
import java.util.*;

/**
 * Classe BDIAgent
 * Modélise un agent cognitif (délibératif) basé sur l'architecture BDI (Belief-Desire-Intention).
 *
 * Contrairement à un agent réactif, cet agent possède une représentation interne de son
 * environnement et raisonne avant d'agir. Son comportement est fondé sur le principe
 * d'intentionnalité (Daniel Dennett, 1995) et la formalisation de l'intention comme un
 * "choix avec engagement" (Cohen et Levesque, 1990).
 *
 * Implémente l'interface Agent (Design Pattern Strategy).
 */
public class BDIAgent implements Agent {
    private String name;
    private String currentRoom;
    private Environment env;

    // --- Représentation des états mentaux (Architecture BDI) ---

    // Beliefs (Croyances) : Ce que l'agent sait ou croit savoir sur l'état de l'environnement.
    private Map<String, Boolean> beliefs = new HashMap<>();

    // Desires (Désirs) : Les objectifs à long terme de l'agent.
    private List<String> desires = new ArrayList<>();

    // Intentions (Intentions / Plans) : Les actions sur lesquelles l'agent s'est engagé.
    private Queue<String> currentPlan = new LinkedList<>();

    /**
     * Constructeur de l'agent cognitif.
     * Initialise l'agent et lui assigne son désir fondamental (son but ultime).
     *
     * @param name Le nom de l'agent (ex: "Coordinator")
     * @param startingRoom La pièce de départ de l'agent
     * @param env L'environnement partagé (Singleton)
     */
    public BDIAgent(String name, String startingRoom, Environment env) {
        this.name = name;
        this.currentRoom = startingRoom;
        this.env = env;

        // Le désir principal du Coordinateur est de gérer les tâches de la maison
        this.desires.add("Manage Tasks");
    }

    /**
     * Phase de Perception.
     * L'agent observe l'environnement et met à jour son modèle interne (ses Croyances / Beliefs).
     *
     * @param env L'environnement courant
     */
    @Override
    public void perceive(Environment env) {
        // BELIEFS : Observe s'il reste des tâches en attente dans l'environnement
        boolean hasTasks = !env.getPendingTasks().isEmpty();

        // Mise à jour de la base de croyances
        beliefs.put("tasks_available", hasTasks);

        if (hasTasks) {
            System.out.println("[" + this.name + "] (Perception) -> Tâches en attente : " + hasTasks);
        }
    }

    /**
     * Phase de Décision (Raisonnement BDI).
     * C'est le cœur cognitif de l'agent. Il confronte ses Croyances (ce qui est vrai)
     * à ses Désirs (ce qu'il veut) pour générer des Intentions (un plan d'action).
     */
    @Override
    public void decide() {
        // INTENTIONS : Si l'agent croit qu'il y a des tâches (Belief) et n'a pas de plan en cours...
        if (beliefs.getOrDefault("tasks_available", false) && currentPlan.isEmpty()) {

            // ...et que son désir (Desire) est de les gérer...
            if (desires.contains("Manage Tasks")) {
                System.out.println("[" + this.name + "] (Décision) -> Je décide de déléguer le nettoyage.");

                // ...alors il s'engage sur une intention (Intention) en l'ajoutant à son plan.
                // Note : Son rôle (Organisation) est de coordonner, il planifie donc de déléguer.
                currentPlan.add("DelegateTask");
            }
        }
    }

    /**
     * Phase d'Action.
     * L'agent exécute les intentions empilées dans son plan d'action.
     *
     * @param env L'environnement sur lequel l'agent agit
     */
    @Override
    public void act(Environment env) {
        // ACTION : Dépile et exécute le plan d'action de manière séquentielle
        if (!currentPlan.isEmpty()) {
            String actionToPerform = currentPlan.poll();

            if (actionToPerform.equals("DelegateTask")) {
                System.out.println("[" + this.name + "] (Action) -> Envoi d'une directive au CleanerBot.");

                // Théorie des actes de langage (Speech Acts - Austin & Searle) :
                // L'agent crée un acte illocutoire de type "Directive" pour donner un ordre.
                // Cela respecte la sémantique de communication standard FIPA-ACL.
                SpeechAct order = new SpeechAct("directive", "Va faire les tâches en attente", this.name, "CleanerBot");

                // Poste le message dans la boîte aux lettres partagée de l'environnement (Asynchrone)
                env.broadcastMessage(order);
            }
        }
    }

    /**
     * Traitement des messages reçus.
     * Permet à l'agent de lire les informations ou réponses des autres entités du système.
     *
     * @param message L'acte de langage reçu
     */
    @Override
    public void receiveMessage(SpeechAct message) {
        System.out.println(this.name + " a reçu le message : " + message.getContent());
    }

    /**
     * Boucle de vie de l'agent (Autonomie).
     * Valide le concept de persistance : l'agent opère continuellement dans son propre Thread.
     */
    @Override
    public void run() {
        while (true) {
            try {
                if (this.env != null) {
                    perceive(this.env);
                    decide();
                    act(this.env);
                }
                // Pause pour simuler le temps d'exécution cognitif et éviter la surcharge CPU
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
