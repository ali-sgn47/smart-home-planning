package agents;

import environment.Environment;
import environment.Task;
import communication.SpeechAct;

/**
 * Classe ReactiveAgent
 * Modélise un agent réactif de type "Simple Reflex Agent".
 * Contrairement à un agent cognitif (BDI), il ne possède pas de modèle interne complexe
 * ni de capacités de planification. Son comportement est régi par des règles simples
 * de type condition-action (stimulus-réponse) basées sur sa perception immédiate.
 *
 * Implémente l'interface Agent (Applique le Design Pattern Strategy).
 */
public class ReactiveAgent implements Agent {
    private String name;
    private Environment env;

    // La mémoire de l'agent réactif est très limitée, il ne retient que son stimulus immédiat
    private Task perceivedTask = null;

    /**
     * Constructeur de l'agent réactif.
     *
     * @param name Le nom de l'agent (ex: "CleanerBot")
     * @param env L'environnement partagé (Singleton) dans lequel l'agent est situé
     */
    public ReactiveAgent(String name, Environment env) {
        this.name = name;
        this.env = env;
    }

    /**
     * Phase de Perception.
     * L'agent observe son environnement localement (lecture des messages et vision des tâches).
     *
     * @param env L'environnement courant
     */
    @Override
    public void perceive(Environment env) {
        // 1. Perception sociale : Lire la boîte aux lettres asynchrone pour voir s'il y a un ordre
        SpeechAct msg = env.readMessageFor(this.name);
        if (msg != null) {
            receiveMessage(msg);
        }

        // 2. Perception spatiale/visuelle : L'agent regarde s'il y a un "stimulus" dans son environnement
        if (!env.getPendingTasks().isEmpty()) {
            this.perceivedTask = env.getPendingTasks().get(0); // Il perçoit la première tâche
        } else {
            this.perceivedTask = null; // Aucun stimulus perçu
        }
    }

    /**
     * Phase de Décision.
     * En tant qu'agent réactif "Simple Reflex", cet agent n'a pas de processus délibératif.
     * Il ne fait aucune planification à long terme, la décision est implicite dans son action.
     * Cette méthode reste donc intentionnellement vide.
     */
    @Override
    public void decide() {
        // L'agent réactif ne fait pas de plan. Il n'a pas besoin de cette méthode.
    }

    /**
     * Phase d'Action.
     * L'agent applique sa règle "condition-action" : s'il a perçu un stimulus,
     * il réagit immédiatement en modifiant l'état de l'environnement.
     *
     * @param env L'environnement sur lequel l'agent agit
     */
    @Override
    public void act(Environment env) {
        // ACTION (Réflexe) : Il exécute immédiatement la tâche qu'il voit
        if (this.perceivedTask != null) {
            Task t = env.getAvailableTask();
            if (t != null) {
                System.out.println("[" + this.name + "] (Réaction) -> J'exécute immédiatement la tâche : " + t.getName());
                // L'agent modifie l'environnement en complétant la tâche
                env.updateTaskStatus(t, "completed");
            }
            // L'agent "oublie" la tâche une fois traitée (comportement sans mémoire longue)
            this.perceivedTask = null;
        }
    }

    /**
     * Traitement des messages reçus (Théorie des Actes de Langage).
     * L'agent analyse l'intention illocutoire du message (Speech Act).
     *
     * @param message Le message reçu depuis l'environnement
     */
    @Override
    public void receiveMessage(SpeechAct message) {
        // S'il s'agit d'une "directive" (un ordre), le comportement perlocutoire de l'agent est déclenché
        if (message.getType().equals("directive")) {
            System.out.println("[" + this.name + "] a reçu un ordre direct de " + message.getSender() + " : " + message.getContent());
        }
    }

    /**
     * Boucle de vie de l'agent (Autonomie).
     * L'agent s'exécute continuellement dans son propre Thread,
     * enchaînant de manière asynchrone le cycle : Percevoir -> Décider -> Agir.
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
                // Pause pour simuler le temps d'exécution et éviter de surcharger le processeur
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}