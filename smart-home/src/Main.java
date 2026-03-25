package main;

import agents.PlanningBDIAgent;
import agents.ReactiveAgent;
import environment.Environment;
import environment.Task;
import learning.QLearningAgent;

import java.util.Arrays;
import java.util.List;

/**
 * Classe Main (version Planning)
 *
 * Point d'entrée de la Smart Home étendue avec planification, apprentissage
 * et raisonnement logique.
 *
 * Architecture du MAS :
 *
 *   ┌─────────────────────────────────────────────┐
 *   │            ENVIRONMENT (Singleton)           │
 *   │  Tasks enrichies : priority, energy, deps   │
 *   └────────┬────────────────┬───────────────────┘
 *            │                │
 *   ┌────────▼──────┐  ┌──────▼──────────────────┐
 *   │  CleanerBot   │  │    SmartCoordinator      │
 *   │ (Réactif)     │  │  (PlanningBDIAgent)      │
 *   │               │  │  BDI + MDP + Logic       │
 *   └────────▲──────┘  └──────┬──────────────────┘
 *            │                │
 *   ┌────────┴──────┐  ┌──────▼──────┐
 *   │  SpeechActs   │  │ LearnerBot  │
 *   │  (FIPA-ACL)   │  │ (Q-Learning)│
 *   └───────────────┘  └─────────────┘
 *
 * Tâches modélisées (avec dépendances réelles du sujet) :
 *   1. "Collect garbage"  (Kitchen, priorité 1, energy 4) — précède "Throw garbage"
 *   2. "Throw garbage"    (Kitchen, priorité 1, energy 3) — dépend de "collect_garbage"
 *   3. "Wash dishes"      (Kitchen, priorité 2, energy 5)
 *   4. "Clean bathroom"   (Bathroom, priorité 2, energy 6)
 *   5. "Organize objects" (LivingRoom, priorité 3, energy 3)
 *   6. "Clean living room"(LivingRoom, priorité 3, energy 7)
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║  Smart Home MAS — Planning Extension     ║");
        System.out.println("║  MDP + Q-Learning + Logic Reasoning      ║");
        System.out.println("╚══════════════════════════════════════════╝\n");

        // ──────────────────────────────────────────────
        // 1. INITIALISATION DE L'ENVIRONNEMENT
        // ──────────────────────────────────────────────
        Environment env = Environment.getInstance();

        // Tâches enrichies avec id, priorité, coût énergie et dépendances
        Task collectGarbage = new Task(
            "Collect garbage", "collect_garbage", "Kitchen",
            1, 4, Arrays.asList()
        );
        Task throwGarbage = new Task(
            "Throw garbage", "throw_garbage", "Kitchen",
            1, 3, Arrays.asList("collect_garbage") // dépendance explicite
        );
        Task washDishes = new Task(
            "Wash dishes", "wash_dishes", "Kitchen",
            2, 5, Arrays.asList()
        );
        Task cleanBathroom = new Task(
            "Clean bathroom", "clean_bathroom", "Bathroom",
            2, 6, Arrays.asList()
        );
        Task organizeObjects = new Task(
            "Organize objects", "organize_objects", "LivingRoom",
            3, 3, Arrays.asList()
        );
        Task cleanLivingRoom = new Task(
            "Clean living room", "clean_living_room", "LivingRoom",
            3, 7, Arrays.asList()
        );

        // Catalogue complet des tâches (utilisé par le MDPPlanner)
        List<Task> allTasks = Arrays.asList(
            collectGarbage, throwGarbage, washDishes,
            cleanBathroom, organizeObjects, cleanLivingRoom
        );

        // Ajout des tâches dans l'environnement
        for (Task t : allTasks) {
            env.addPendingTask(t);
        }

        System.out.println("Tâches initialisées :");
        for (Task t : allTasks) {
            System.out.printf("  - %-22s | priorité=%d | énergie=%d | deps=%s%n",
                    t.getName(), t.getPriority(), t.getEnergyCost(), t.getDependencies());
        }
        System.out.println();

        // ──────────────────────────────────────────────
        // 2. CRÉATION DES AGENTS
        // ──────────────────────────────────────────────

        // Agent 1 : Coordinator enrichi (BDI + MDP + LogicReasoner)
        PlanningBDIAgent coordinator = new PlanningBDIAgent(
            "SmartCoordinator", "LivingRoom", env, allTasks
        );

        // Agent 2 : Agent Réactif (inchangé, compatibilité ascendante)
        ReactiveAgent cleaner = new ReactiveAgent("CleanerBot", env);

        // Agent 3 : Agent Q-Learning (nouvel agent apprenant)
        QLearningAgent learner = new QLearningAgent("LearnerBot", env);

        // ──────────────────────────────────────────────
        // 3. LANCEMENT DES THREADS (Intelligence Distribuée)
        // ──────────────────────────────────────────────
        System.out.println("Lancement des agents...\n");

        Thread t1 = new Thread(coordinator, "Thread-SmartCoordinator");
        Thread t2 = new Thread(cleaner,     "Thread-CleanerBot");
        Thread t3 = new Thread(learner,     "Thread-LearnerBot");

        // Démarrage décalé : le coordinator planifie avant que les agents agissent
        t1.start();
        Thread.sleep(500);
        t2.start();
        Thread.sleep(300);
        t3.start();

        // Simulation 20 secondes puis stats RL
        Thread.sleep(20_000);
        learner.printStats();

        System.out.println("\n[Main] Simulation terminée.");
        System.exit(0);
    }
}
