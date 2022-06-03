package org.ethereum.listener;

import co.rsk.core.Coin;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.simples.SimpleRskTransaction;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GasPriceTrackerTest {

    private static final long DB_BLOCK_PRICE = 35_000_000_000L;
    private static final long DB_TX_GAS_PRICE = 45_000_000_000L;
    private static final long DEFAULT_GAS_PRICE = 20_000_000_000L;

    private static final int TOTAL_SLOTS = 512;
    private static final int BEST_BLOCK_TX_COUNT = 4;
    private static final int PARENT_BLOCK_TX_COUNT = 8;
    // 512 (total) - 4 (best block) = 508 (to fill with parents) => 64 iter of 8 txs => 512 + 508 ~= 508 => index = 507
    private static final int INDEX_AFTER_DB_FILL = 507;

    private BlockStore blockStore;

    @Before
    public void setUp() throws Exception {
        blockStore = mock(BlockStore.class);
        Block bestBlock = makeBlock(Coin.valueOf(DB_BLOCK_PRICE), BEST_BLOCK_TX_COUNT, i -> makeTx(Coin.valueOf(DB_TX_GAS_PRICE)));
        Block parentBlock = makeBlock(Coin.valueOf(35_000_000_000L), PARENT_BLOCK_TX_COUNT, i -> makeTx(Coin.valueOf(DB_TX_GAS_PRICE)));
        when(blockStore.getBlockByHash(any())).thenReturn(parentBlock);
        when(blockStore.getBestBlock()).thenReturn(bestBlock);
    }

    @Test
    public void getGasPrice_TrackerNotTriggered_NoBestBlockOnDB_ReturnsDefaultPrice() {
        when(blockStore.getBestBlock()).thenReturn(null); // no bestBlock on DB
        GasPriceTracker gasPriceTracker = GasPriceTracker.create(blockStore);
        Coin actualResult = gasPriceTracker.getGasPrice();
        assertEquals(Coin.valueOf(DEFAULT_GAS_PRICE), actualResult);
    }

    @Test
    public void getGasPrice_TrackerNotTriggered_NotEnoughBlocksOnDB_ReturnsDBLastBlockPrice() {
        when(blockStore.getBlockByHash(any())).thenReturn(null); // just bestBlock without parents
        GasPriceTracker gasPriceTracker = GasPriceTracker.create(blockStore);
        Coin actualResult = gasPriceTracker.getGasPrice();
        assertEquals(Coin.valueOf(DB_BLOCK_PRICE), actualResult);
    }

    @Test
    public void getGasPrice_TrackerNotTriggered_EnoughBlocksOnDB_ReturnsDBCalculatedPrice() {
        GasPriceTracker gasPriceTracker = GasPriceTracker.create(blockStore);
        Coin actualResult = gasPriceTracker.getGasPrice();
        assertEquals(Coin.valueOf(DB_TX_GAS_PRICE), actualResult);
    }

    @Test
    public void getGasPrice_PriceWindowNotFilledByDB_BlockReceivedNotFillingWindow_ReturnsBlockPrice() {
        long blockPrice = 30_000_000_000L;

        when(blockStore.getBlockByHash(any())).thenReturn(null); // just bestBlock without parents
        GasPriceTracker gasPriceTracker = GasPriceTracker.create(blockStore);

        int insufficientTxs = TOTAL_SLOTS - BEST_BLOCK_TX_COUNT - 1;
        Block block = makeBlock(Coin.valueOf(blockPrice), insufficientTxs, i -> makeTx(Coin.valueOf(40_000_000_000L)));

        gasPriceTracker.onBlock(block, Collections.emptyList());

        Coin actualResult = gasPriceTracker.getGasPrice();

        assertEquals(Coin.valueOf(blockPrice), actualResult);
    }

    @Test
    public void getGasPrice_PriceWindowFilledByDB_BlockReceivedNotFillingWindow_ReturnsDBCalculatedPrice() {
        GasPriceTracker gasPriceTracker = GasPriceTracker.create(blockStore);

        Block block = makeBlock(Coin.valueOf(30_000_000_000L), 1, i -> makeTx(Coin.valueOf(i * 1_000_000_000L)));

        gasPriceTracker.onBlock(block, Collections.emptyList());

        Coin actualResult = gasPriceTracker.getGasPrice();

        assertEquals(Coin.valueOf(DB_TX_GAS_PRICE), actualResult);
    }

    @Test
    public void getGasPrice_PriceWindowFilledByDB_BlockReceivedFillingWindow_ReturnsBlockCalculatedPrice() {
        GasPriceTracker gasPriceTracker = GasPriceTracker.create(blockStore);

        Block block = makeBlock(Coin.valueOf(30_000_000_000L), TOTAL_SLOTS, i -> makeTx(Coin.valueOf(i * 1_000_000_000L)));

        gasPriceTracker.onBlock(block, Collections.emptyList());

        Coin actualResult = gasPriceTracker.getGasPrice();

        assertEquals(Coin.valueOf(128_000_000_000L), actualResult);
    }

    @Test
    public void getGasPrice_PriceWindowFilled_BestBlockReceivedWithLowerPrice_ReturnsBlockCalculatedPrice() {
        GasPriceTracker gasPriceTracker = GasPriceTracker.create(blockStore);

        Block bestBlock = makeBlock(Coin.valueOf(1_000_000_000L), 0, i -> null);
        Block block = makeBlock(Coin.valueOf(1_000_000_000L), TOTAL_SLOTS, i -> makeTx(Coin.valueOf(i * 1_000_000_000L)));

        gasPriceTracker.onBestBlock(bestBlock, Collections.emptyList());
        gasPriceTracker.onBlock(block, Collections.emptyList());
        Coin actualResult = gasPriceTracker.getGasPrice();
        assertEquals(Coin.valueOf(128_000_000_000L), actualResult); // calculated value has been returned

        block = makeBlock(Coin.valueOf(1_000_000_000L), TOTAL_SLOTS + 1, i -> makeTx(Coin.valueOf(2_000_000_000L)));
        gasPriceTracker.onBlock(block, Collections.emptyList());
        actualResult = gasPriceTracker.getGasPrice();
        assertEquals(Coin.valueOf(2_000_000_000L), actualResult); // re-calculated value has been returned

        block = makeBlock(Coin.valueOf(1_000_000_000L), INDEX_AFTER_DB_FILL, i -> makeTx(Coin.valueOf(10_000_000_000L)));
        gasPriceTracker.onBlock(block, Collections.emptyList());
        actualResult = gasPriceTracker.getGasPrice();
        assertEquals(Coin.valueOf(2_000_000_000L), actualResult); // cached value has been returned
    }

    @Test
    public void getGasPrice_PriceWindowFilled_BestBlockReceivedWithGreaterPrice_ReturnsBestBlockAdjustedPrice() {
        GasPriceTracker gasPriceTracker = GasPriceTracker.create(blockStore);

        Block bestBlock = makeBlock(Coin.valueOf(50_000_000_000L), 0, i -> null);
        Block block = makeBlock(Coin.valueOf(30_000_000_000L), TOTAL_SLOTS, i -> makeTx(Coin.valueOf(40_000_000_000L)));

        gasPriceTracker.onBestBlock(bestBlock, Collections.emptyList());
        gasPriceTracker.onBlock(block, Collections.emptyList());

        Coin actualResult = gasPriceTracker.getGasPrice();

        assertEquals(Coin.valueOf(55_000_000_000L), actualResult);
    }

    @Test
    public void isFeeMarketWorking_falseWhenNotEnoughBlocks() {
        GasPriceTracker gasPriceTracker = GasPriceTracker.create(blockStore);

        // to ensure only bestBlock is included on window on initial fill
        when(blockStore.getBlockByHash(any())).thenReturn(null);
        for (int i = 0; i < 48; i++) { // bestBlock already included => 48 instead of 49
            Block block = makeBlock(Coin.valueOf(30_000_000_000L), 1, j -> makeTx(Coin.valueOf(40_000_000_000L)));
            when(block.getGasUsed()).thenReturn(700_000L);
            gasPriceTracker.onBestBlock(block, Collections.emptyList());
            gasPriceTracker.onBlock(block, Collections.emptyList());
        }

        assertFalse(gasPriceTracker.isFeeMarketWorking());
    }

    @Test
    public void isFeeMarketWorking_falseWhenBelowAverage() {
        GasPriceTracker gasPriceTracker = GasPriceTracker.create(blockStore);

        for (int i = 0; i < 50; i++) {
            Block block = makeBlock(Coin.valueOf(30_000_000_000L), 1, j -> makeTx(Coin.valueOf(40_000_000_000L)));
            when(block.getGasUsed()).thenReturn(5_439_000L); // 6_800_000 * 0.8 - margin
            gasPriceTracker.onBestBlock(block, Collections.emptyList());
            gasPriceTracker.onBlock(block, Collections.emptyList());
        }

        assertFalse(gasPriceTracker.isFeeMarketWorking());
    }

    @Test
    public void isFeeMarketWorking_trueWhenAboveAverage() {
        GasPriceTracker gasPriceTracker = GasPriceTracker.create(blockStore);

        for (int i = 0; i < 50; i++) {
            Block block = makeBlock(Coin.valueOf(30_000_000_000L), 1, j -> makeTx(Coin.valueOf(40_000_000_000L)));
            when(block.getGasUsed()).thenReturn(5_441_000L); // 6_800_000 * 0.8 + margin
            gasPriceTracker.onBestBlock(block, Collections.emptyList());
            gasPriceTracker.onBlock(block, Collections.emptyList());
        }

        assertTrue(gasPriceTracker.isFeeMarketWorking());
    }

    private static Block makeBlock(Coin mgp, int txCount, Function<Integer, Transaction> txMaker) {
        Block block = mock(Block.class);

        when(block.getMinimumGasPrice()).thenReturn(mgp);
        when(block.getParentHash()).thenReturn(new Keccak256(HashUtil.randomHash()));
        when(block.getGasUsed()).thenReturn(700_000L);
        when(block.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(6_800_000));

        List<Transaction> txs = IntStream.range(0, txCount).mapToObj(txMaker::apply).collect(Collectors.toList());
        when(block.getTransactionsList()).thenReturn(txs);

        return block;
    }

    private static Transaction makeTx(Coin gasPrice) {
        return new SimpleRskTransaction(null) {
            @Override
            public Coin getGasPrice() {
                return gasPrice;
            }
        };
    }
}
