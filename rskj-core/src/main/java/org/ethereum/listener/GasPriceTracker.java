/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.ethereum.listener;

import co.rsk.core.Coin;
import co.rsk.crypto.Keccak256;
import co.rsk.remasc.RemascTransaction;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.BlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Calculates a 'reasonable' Gas price based on statistics of the latest transaction's Gas prices
 *
 * Normally the price returned should be sufficient to execute a transaction since ~25% of the latest
 * transactions were executed at this or lower price.
 *
 * Created by Anton Nashatyrev on 22.09.2015.
 */
public class GasPriceTracker extends EthereumListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger("gaspricetracker");

    private static final int TX_WINDOW_SIZE = 512;

    private static final int BLOCK_WINDOW_SIZE = 50;

    private static final double BLOCK_COMPLETION_PERCENT_FOR_FEE_MARKET_WORKING = 0.8;

    private static final BigInteger BI_10 = BigInteger.valueOf(10);
    private static final BigInteger BI_11 = BigInteger.valueOf(11);

    private final Coin[] txWindow = new Coin[TX_WINDOW_SIZE];

    private final Double[] blockWindow = new Double[BLOCK_WINDOW_SIZE];

    private final AtomicReference<Coin> bestBlockPriceRef = new AtomicReference<>();
    private final BlockStore blockStore;

    private Coin defaultPrice = Coin.valueOf(20_000_000_000L);
    private int txIdx = TX_WINDOW_SIZE - 1;

    private int blockIdx = 0;

    private Coin lastVal;

    public static GasPriceTracker create(BlockStore blockStore) {
        GasPriceTracker gasPriceTracker = new GasPriceTracker(blockStore);
        gasPriceTracker.initializeWindowsFromDB();
        return gasPriceTracker;
    }

    private GasPriceTracker(BlockStore blockStore) {
        this.blockStore = blockStore;
    }

    @Override
    public void onBestBlock(Block block, List<TransactionReceipt> receipts) {
        bestBlockPriceRef.set(block.getMinimumGasPrice());
    }

    @Override
    public synchronized void onBlock(Block block, List<TransactionReceipt> receipts) {
        logger.trace("Start onBlock");

        defaultPrice = block.getMinimumGasPrice();

        trackBlockCompleteness(block);

        for (Transaction tx : block.getTransactionsList()) {
            onTransaction(tx);
        }

        logger.trace("End onBlock");
    }

    private void onTransaction(Transaction tx) {
        if (tx instanceof RemascTransaction) {
            return;
        }

        trackGasPrice(tx);
    }

    public synchronized Coin getGasPrice() {
        if (txWindow[0] == null) { // for some reason, not filled yet (i.e. not enough blocks on DB)
            return defaultPrice;
        } else {
            if (lastVal == null) {
                Coin[] values = Arrays.copyOf(txWindow, TX_WINDOW_SIZE);
                Arrays.sort(values);
                lastVal = values[values.length / 4];  // 25% percentile
            }

            Coin bestBlockPrice = bestBlockPriceRef.get();
            if (bestBlockPrice == null) {
                return lastVal;
            } else {
                return Coin.max(lastVal, bestBlockPrice.multiply(BI_11).divide(BI_10));
            }
        }
    }

    public synchronized boolean isFeeMarketWorking() {
        if (blockWindow[BLOCK_WINDOW_SIZE - 1] == null) {
            logger.warn("Not enough blocks on window, default to Fee Market not working");
            return false;
        }

        double accumulatedCompleteness = Arrays.stream(blockWindow).reduce(0d, Double::sum);
        double totalBlocks = blockWindow.length;
        double avgBlockCompleteness = accumulatedCompleteness / totalBlocks;

        return avgBlockCompleteness >= BLOCK_COMPLETION_PERCENT_FOR_FEE_MARKET_WORKING;
    }

    private void initializeWindowsFromDB() {
        List<Block> blocks = getRequiredBlocksToFillWindowsFromDB();
        if (blocks.isEmpty()) {
            return;
        }

        onBestBlock(blocks.get(0), Collections.emptyList());
        blocks.forEach(b -> onBlock(b, Collections.emptyList()));
    }

    private List<Block> getRequiredBlocksToFillWindowsFromDB() {
        List<Block> blocks = new ArrayList<>();

        Optional<Block> block = Optional.ofNullable(blockStore.getBestBlock());

        int txCount = 0;
        while ((txCount < TX_WINDOW_SIZE || blocks.size() < BLOCK_WINDOW_SIZE) && block.isPresent()) {
            blocks.add(block.get());
            txCount += block.get().getTransactionsList().stream().filter(tx -> !(tx instanceof RemascTransaction)).count();
            block = block.map(Block::getParentHash).map(Keccak256::getBytes).map(blockStore::getBlockByHash);
        }

        if (txCount < TX_WINDOW_SIZE) {
            logger.warn("Not enough blocks ({}) found on DB to fill tx window", blocks.size());
        }

        if (blocks.size() < BLOCK_WINDOW_SIZE) {
            logger.warn("Not enough blocks ({}) found on DB to fill block window", blocks.size());
        }

        // to simulate processing order
        Collections.reverse(blocks);

        return blocks;
    }

    private void trackGasPrice(Transaction tx) {
        if (txIdx == -1) {
            txIdx = TX_WINDOW_SIZE - 1;
            lastVal = null;  // recalculate only 'sometimes'
        }
        txWindow[txIdx--] = tx.getGasPrice();
    }

    private void trackBlockCompleteness(Block block) {
        double gasUsed = block.getGasUsed();
        double gasLimit = block.getGasLimitAsInteger().doubleValue();
        double completeness = gasUsed / gasLimit;

        if (blockIdx == BLOCK_WINDOW_SIZE) {
            blockIdx = 0;
        }
        blockWindow[blockIdx++] = completeness;
    }

}
