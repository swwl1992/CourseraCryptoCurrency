import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TxHandler {

    private UTXOPool utxoPool;
    private TxValidator validator;

    private class Result {
        final private double sum;
        final private boolean isValid;

        private Result(double sum, boolean isValid) {
            this.sum = sum;
            this.isValid = isValid;
        }
    }

    private class TxValidator {

        private boolean isValid(Transaction tx) {
            if (tx == null) return false;
            Result inputValidationResult = validateInput(utxoPool, tx);
            boolean isInputValid = inputValidationResult.isValid;
            double inputSum = inputValidationResult.sum;
            return isInputValid && isOutputValid(inputSum, tx);
        }

        private Result validateInput(UTXOPool utxoPool, Transaction tx) {
            double inputSum = 0.0;
            Set<UTXO> spentTxs = new HashSet<>();
            for (int i = 0; i < tx.numInputs(); i++) {
                Transaction.Input input = tx.getInput(i);
                if (input == null) return new Result(inputSum, false);
                UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);

                if (!utxoPool.contains(utxo) || spentTxs.contains(utxo)) return new Result(inputSum, false);

                Transaction.Output prevTxOutput = utxoPool.getTxOutput(utxo);

                PublicKey pk = prevTxOutput.address;
                byte[] msg = tx.getRawDataToSign(i);
                byte[] sig = input.signature;

                if (!Crypto.verifySignature(pk, msg, sig)) return new Result(inputSum, false);
                spentTxs.add(utxo);
                inputSum += prevTxOutput.value;
            }

            return new Result(inputSum, true);
        }

        private boolean isOutputValid(double inputSum, Transaction tx) {
            double outputSum = 0.0;
            for (int i = 0; i < tx.numOutputs(); i++) {
                Transaction.Output output = tx.getOutput(i);
                if (output.value < 0) return false;
                outputSum += output.value;
            }
            return inputSum >= outputSum;
        }
    }

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
        this.validator = new TxValidator();
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO utxoPool,
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        return this.validator.isValid(tx);
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO utxoPool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        if (possibleTxs == null) return new Transaction[0];
        ConcurrentHashMap<byte[], Transaction> txs = initSigToTxMap(possibleTxs);
        ArrayList<Transaction> validTxs = new ArrayList<>();
        int txCount;
        do {
            txCount = txs.size();
            for (Transaction tx : txs.values()) {
                if (!isValidTx(tx)) continue;
                validTxs.add(tx);
                applyTx(tx);
                txs.remove(tx.getHash());
            }
            if (txCount == txs.size() || txCount == 0) break;
        } while (true);

        return validTxs.toArray(new Transaction[validTxs.size()]);
    }

    private ConcurrentHashMap<byte[], Transaction> initSigToTxMap(Transaction[] possibleTxs) {
        ConcurrentHashMap<byte[], Transaction> txs = new ConcurrentHashMap<>();
        for (Transaction tx : possibleTxs) {
            if (tx == null) continue;
            tx.finalize();
            txs.put(tx.getHash(), tx);
        }
        return txs;
    }

    private void applyTx(Transaction tx) {
        if (tx == null) return;
        for (Transaction.Input input : tx.getInputs()) {
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            utxoPool.removeUTXO(utxo);
        }
        byte[] txHash = tx.getHash();
        int index = 0;
        for (Transaction.Output output : tx.getOutputs()) {
            UTXO utxo = new UTXO(txHash, index);
            index += 1;
            utxoPool.addUTXO(utxo, output);
        }
    }
}
