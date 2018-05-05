import java.util.HashSet;
import java.util.Set;

/* CompliantNode refers to a node that follows the rules (not malicious)*/
public class CompliantNode implements Node {

    private boolean[] followees;
    private Set<Transaction> pendingTransactions;
    private boolean[] blacklisted;

    public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
        super();
    }

    public void setFollowees(boolean[] followees) {
        this.followees = followees;
        this.blacklisted = new boolean[followees.length];
    }

    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        this.pendingTransactions = pendingTransactions;
    }

    public Set<Transaction> sendToFollowers() {
        Set<Transaction> txToSend = new HashSet<>(pendingTransactions);
        pendingTransactions.clear();
        return txToSend;
    }

    public void receiveFromFollowees(Set<Candidate> candidates) {
        if (candidates == null) return;
        Set<Integer> senders = new HashSet<>();
        for (Candidate candidate : candidates) {
            senders.add(candidate.sender);
        }

        for (int i = 0; i < followees.length; i++) {
            if (followees[i] && !senders.contains(i)) blacklisted[i] = true;
        }

        for (Candidate candidate : candidates) {
            if (!blacklisted[candidate.sender]) pendingTransactions.add(candidate.tx);
        }
    }
}
