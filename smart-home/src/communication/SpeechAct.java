package communication;

/**
 * Classe SpeechAct
 * Modélise un acte de langage selon la théorie des "Speech Acts" (Austin, 1962 ; Searle, 1969).
 *
 * Dans les Systèmes Multi-Agents, la communication ne se fait pas par de simples appels
 * de méthodes, mais par l'échange de messages sémantiques asynchrones. Cette classe
 * s'inspire du standard de communication FIPA-ACL (Foundation for Intelligent Physical Agents).
 *
 * Chaque acte de communication reflète une intention (Force illocutoire) visant à
 * déclencher un effet (Effet perlocutoire) chez le destinataire.
 */
public class SpeechAct {

    // L'intention illocutoire ou le "performatif" selon FIPA-ACL
    // Exemples : "directive" (donner un ordre), "assertive" (déclarer un fait), "commissive" (promettre)
    private String type;

    // L'acte locutoire : le contenu sémantique du message
    // Exemple : "Nettoie la cuisine" ou "Va faire les tâches en attente"
    private String content;

    // L'agent qui initie la communication
    private String sender;

    // L'agent destinataire (celui qui subira l'effet perlocutoire)
    private String receiver;

    /**
     * Constructeur d'un Acte de Langage.
     *
     * @param type L'intention du message (le performatif, ex: "directive" ou "inform")
     * @param content Le contenu sémantique (données ou ordre) du message
     * @param sender Le nom de l'agent expéditeur
     * @param receiver Le nom de l'agent destinataire
     */
    public SpeechAct(String type, String content, String sender, String receiver) {
        this.type = type;
        this.content = content;
        this.sender = sender;
        this.receiver = receiver;
    }

    /**
     * Récupère le type (performatif) du message pour en déduire l'intention.
     * @return Le type d'acte de langage
     */
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * Récupère le contenu locutoire du message.
     * @return Le contenu du message
     */
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    /**
     * Récupère le nom de l'agent expéditeur.
     * @return L'expéditeur
     */
    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    /**
     * Récupère le nom de l'agent destinataire.
     * @return Le destinataire
     */
    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }
}