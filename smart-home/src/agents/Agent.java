package agents;

import environment.Environment;
import communication.SpeechAct;

/**
 * Interface Agent (Implémente le Design Pattern "Strategy")
 *
 * Selon les définitions de Ferber (1995) et Shoham (1993), un agent est une entité
 * autonome située dans un environnement, capable d'agir, de percevoir et de communiquer.
 *
 * En étendant l'interface native "Runnable", l'agent acquiert la propriété d'opérer
 * continuellement et de manière asynchrone (Thread indépendant).
 *
 * Le Design Pattern "Strategy" est appliqué ici : cette interface définit le
 * contrat global du cycle de vie des agents. Cela permet au système d'interchanger
 * dynamiquement les algorithmes internes (architecture Réactive ou Cognitive BDI)
 * de manière transparente.
 */
public interface Agent extends Runnable {

    /**
     * Phase 1 : Perception (Local Perception)
     * L'agent observe l'état de l'environnement partagé à un instant T.
     * Cette méthode lui permet de capter des stimuli immédiats (pour un agent réactif)
     * ou de mettre à jour ses croyances / Beliefs (pour un agent cognitif).
     *
     * @param env L'environnement dans lequel l'agent est situé.
     */
    void perceive(Environment env);

    /**
     * Phase 2 : Décision (Internal Process)
     * Représente le processus interne de prise de décision de l'agent.
     * - Pour un agent délibératif (BDI) : c'est ici qu'il confronte ses croyances
     *   et ses désirs pour former une intention et élaborer un plan.
     * - Pour un agent réactif : cette phase est généralement vide ou implicite,
     *   la décision découlant directement par réflexe de la perception.
     */
    void decide();

    /**
     * Phase 3 : Action (Local Action)
     * L'agent exécute l'action qu'il a choisie, modifiant ainsi l'état de l'environnement
     * (par exemple, modifier le statut d'une tâche ménagère de "pending" à "completed").
     *
     * @param env L'environnement sur lequel l'agent agit.
     */
    void act(Environment env);

    /**
     * Phase 4 : Sociabilité et Communication (Théorie des Actes de Langage)
     * Permet à l'agent de recevoir et d'interpréter des messages asynchrones.
     * L'interaction repose sur la théorie des "Speech Acts" (Austin, 1962 ; Searle, 1969).
     * L'agent analyse l'intention illocutoire du message (ex: une Directive)
     * pour déclencher un effet perlocutoire (adapter son comportement).
     *
     * @param message L'acte de langage (message sémantique) reçu.
     */
    void receiveMessage(SpeechAct message);
}