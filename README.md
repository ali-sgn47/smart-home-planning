
# Smart Home MAS — Planning Extension

Système Multi-Agents pour une Smart Home intelligente.
Extension du projet L3 Agents intelligent avec planification MDP, apprentissage par renforcement (Q-Learning) et raisonnement logique (Forward Chaining).

---

## Architecture du projet

```
src/
├── Main.java                        ← Point d'entrée
│
├── environment/
│   ├── Environment.java             ← Singleton partagé (inchangé + getCompletedTaskIds)
│   └── Task.java                    ← Enrichie : id, priority, energyCost, dependencies
│
├── agents/
│   ├── Agent.java                   ← Interface Strategy (inchangée)
│   ├── ReactiveAgent.java           ← Agent réactif (inchangé)
│   ├── BDIAgent.java                ← Agent BDI (inchangé)
│   └── PlanningBDIAgent.java        ← [NOUVEAU] BDI + MDP + LogicReasoner
│
├── planning/
│   ├── MDPState.java                ← [NOUVEAU] État du MDP (pending, done, room, energy)
│   └── MDPPlanner.java              ← [NOUVEAU] Value Iteration avec probabilités réelles
│
├── learning/
│   └── QLearningAgent.java          ← [NOUVEAU] Agent RL epsilon-greedy + Q-table
│
├── reasoning/
│   └── LogicReasoner.java           ← [NOUVEAU] Forward Chaining + veto logique
│
└── communication/
    └── SpeechAct.java               ← Actes de langage FIPA-ACL (inchangé)
```

---

## Compilation et exécution

### Prérequis
- Java JDK 11 ou supérieur

### Compiler

```bash
mkdir -p out
find src -name "*.java" > sources.txt
javac -cp src -d out @sources.txt
```

### Lancer

```bash
java -cp out main.Main
```

---

## Techniques de planification implémentées

### 1. MDP — Processus de Décision Markovien (`planning/`)

**Formalisme :** MDP = (S, A, T, R, γ)

| Composante | Description |
|---|---|
| S | `MDPState` : tâches pending, tâches done, pièce, énergie discrétisée |
| A | IDs des tâches disponibles (dépendances satisfaites + énergie > 5) |
| T(s,a,s') | Probabilités de transition **réelles** : P(succès)=0.80, P(fatigué)=0.10, P(échec)=0.10 |
| R(s,a) | Récompense basée sur la priorité de la tâche − pénalité déplacement − pénalité énergie |
| γ | 0.95 |

**Algorithme :** Value Iteration (Bellman, 1957)
- Convergence quand `max|V_{k+1}(s) - V_k(s)| < 0.001`
- Exploration BFS on-the-fly des états atteignables
- Politique extraite par greedy pass final : `π*(s) = argmax_a Q(s,a)`

**Probabilités de transition** (incertitude réelle d'un robot domestique) :
- **80%** : succès nominal → tâche complétée, énergie réduite normalement
- **10%** : succès mais fatigué → tâche complétée, −10 énergie supplémentaires
- **10%** : échec → tâche remise en pending, énergie réduite quand même

---

### 2. Q-Learning — Apprentissage par Renforcement (`learning/`)

**Algorithme :** Q-Learning (Watkins & Dayan, 1992)

Règle de mise à jour :
```
Q(s,a) ← Q(s,a) + α · [r + γ · max_{a'} Q(s',a') − Q(s,a)]
```

| Hyperparamètre | Valeur |
|---|---|
| α (learning rate) | 0.10 |
| γ (discount factor) | 0.95 |
| ε₀ (exploration initiale) | 0.90 |
| ε_min | 0.05 |
| ε_decay | 0.995 |

**Différence avec le MDP :**
- Le MDP planifie *offline* en connaissant T et R à l'avance.
- Le Q-Learning apprend *online* par expérience directe sans modèle du monde.

---

### 3. Raisonnement Logique — Forward Chaining (`reasoning/`)

**Algorithme :** Chaînage avant (Nilsson, 1980)

Base de règles (Knowledge Base) :

| Règle | Prémisse | Conclusion |
|---|---|---|
| R1 | énergie_critique | recharger_d'abord |
| R2 | énergie_faible AND tâche_lourde_disponible | repos_recommandé |
| R3 | poubelle_collectée | jeter_poubelle_autorisé |
| R4 | vaisselle_sale_présente | priorité_cuisine |
| R5 | plusieurs_pièces_sales | coordination_nécessaire |
| R6 | recharger_d'abord AND coordination_nécessaire | bloquer_actions_lourdes |
| R7 | salle_de_bain_sale AND NOT vaisselle_sale | priorité_salle_de_bain |

**Rôle :** Veto logique sur les actions proposées par le MDP. Certaines actions sont bloquées si elles violent des contraintes symboliques (énergie critique, dépendances non satisfaites).

---

## Agents dans la simulation

| Agent | Classe | Rôle |
|---|---|---|
| SmartCoordinator | `PlanningBDIAgent` | Planifie (MDP + Logique) et délègue |
| CleanerBot | `ReactiveAgent` | Exécute les tâches déléguées (réflexe) |
| LearnerBot | `QLearningAgent` | Apprend la meilleure séquence par RL |

**Stratégie de délégation du Coordinator :**
- Tâche priorité 1 (critique) → `LearnerBot` (plus adaptatif)
- Tâche priorité 2-3 (standard) → `CleanerBot` (plus rapide)

---

## Tâches modélisées

| Tâche | Pièce | Priorité | Énergie | Dépendances |
|---|---|---|---|---|
| Collect garbage | Kitchen | 1 | 4 | — |
| Throw garbage | Kitchen | 1 | 3 | collect_garbage |
| Wash dishes | Kitchen | 2 | 5 | — |
| Clean bathroom | Bathroom | 2 | 6 | — |
| Organize objects | LivingRoom | 3 | 3 | — |
| Clean living room | LivingRoom | 3 | 7 | — |

La dépendance `Throw garbage → Collect garbage` illustre directement la contrainte du cahier des charges : *"garbage must be collected before being thrown out"*.

---

## Design Patterns utilisés

| Pattern | Où | Pourquoi |
|---|---|---|
| **Singleton** | `Environment` | Un seul espace mémoire partagé entre tous les threads |
| **Strategy** | `Agent` (interface) | Interchangeabilité des architectures agents |
| **Template Method** | Cycle perceive/decide/act | Structure commune, comportement différencié |
| **Observer** (implicite) | File de messages `SpeechAct` | Communication asynchrone non-bloquante |
