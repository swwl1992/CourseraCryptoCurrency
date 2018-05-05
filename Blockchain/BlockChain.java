// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockChain {

    private Map<ByteArrayWrapper, BlockNode> innerChain;
    private BlockNode maxHeightNode;
    private TransactionPool txPool;

    public static final int CUT_OFF_AGE = 10;

    private class BlockNode {
        int height;
        Block block;
        BlockNode parent;
        List<BlockNode> children;
        private UTXOPool utxoPool;

        BlockNode(Block block, BlockNode parent, UTXOPool utxoPool) {
            this.block = block;
            this.parent = parent;
            children = new ArrayList<>();
            this.utxoPool = utxoPool;
            if (parent == null) {
                height = 1;
            } else {
                this.height = parent.height + 1;
                parent.children.add(this);
            }
        }
    }

    /**
     * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    public BlockChain(Block genesisBlock) {
        innerChain = new HashMap<>();
        UTXOPool utxoPool = new UTXOPool();
        addCoinBaseToUTXOPool(genesisBlock, utxoPool);
        BlockNode genesisBlockNode = new BlockNode(genesisBlock, null, utxoPool);
        innerChain.put(wrapBytes(genesisBlock.getHash()), genesisBlockNode);
        maxHeightNode = genesisBlockNode;
        txPool = new TransactionPool();
    }

    /** Get the maximum height block */
    public Block getMaxHeightBlock() {
        return maxHeightNode.block;
    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
        return maxHeightNode.utxoPool;
    }

    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
        return this.txPool;
    }

    /**
     * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
     * 
     * <p>
     * For example, you can try creating a new block over the genesis block (block height 2) if the
     * block chain height is {@code <=
     * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block
     * at height 2.
     * 
     * @return true if block is successfully added
     */
    public boolean addBlock(Block block) {
        byte[] prevBlockHash = block.getPrevBlockHash();
        if (prevBlockHash == null) return false;

        BlockNode parentBlockNode = innerChain.get(wrapBytes(prevBlockHash));
        if (parentBlockNode == null) return false;

        TxHandler txHandler = new TxHandler(new UTXOPool(parentBlockNode.utxoPool));
        Transaction[] txs = block.getTransactions().toArray(new Transaction[0]);
        Transaction[] validTxs = txHandler.handleTxs(txs);
        if (txs.length != validTxs.length) return false;

        int newHeight = parentBlockNode.height + 1;
        if (maxHeightNode.height - CUT_OFF_AGE >= newHeight) return false;

        UTXOPool utxoPool = txHandler.getUTXOPool();
        addCoinBaseToUTXOPool(block, utxoPool);
        BlockNode blockNode = new BlockNode(block, parentBlockNode, utxoPool);
        innerChain.put(wrapBytes(block.getHash()), blockNode);
        if (newHeight > maxHeightNode.height) maxHeightNode = blockNode;
        return true;
    }

    private static void addCoinBaseToUTXOPool(Block block, UTXOPool utxoPool) {
        Transaction coinbase = block.getCoinbase();
        for (int i = 0; i < coinbase.numOutputs(); i++) {
            Transaction.Output output = coinbase.getOutput(i);
            UTXO utxo = new UTXO(coinbase.getHash(), i);
            utxoPool.addUTXO(utxo, output);
        }
    }

    private static ByteArrayWrapper wrapBytes(byte[] bytes) {
        return new ByteArrayWrapper(bytes);
    }

    /** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
        txPool.addTransaction(tx);
    }
}