/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
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

package co.rsk.scoring;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * Created by ajlopez on 27/06/2017.
 */
public class PeerScoringTest {
    @Test
    public void newStatusHasCounterInZero() {
        PeerScoring scoring = new PeerScoring("id1");

        Assert.assertEquals(0, scoring.getEventCounter(EventType.INVALID_BLOCK));
        Assert.assertEquals(0, scoring.getEventCounter(EventType.INVALID_TRANSACTION));
        Assert.assertEquals(0, scoring.getTotalEventCounter());
    }

    @Test
    public void newStatusHasGoodReputation() {
        PeerScoring scoring = new PeerScoring("id1");

        Assert.assertTrue(scoring.refreshReputationAndPunishment());
    }

    @Test
    public void getInformationFromNewScoring() {
        PeerScoring scoring = new PeerScoring("id1");
        PeerScoringInformation info = PeerScoringInformation.buildByScoring(scoring, "nodeid", "node");

        Assert.assertTrue(info.getGoodReputation());
        Assert.assertEquals(0, info.getValidBlocks());
        Assert.assertEquals(0, info.getInvalidBlocks());
        Assert.assertEquals(0, info.getValidTransactions());
        Assert.assertEquals(0, info.getInvalidTransactions());
        Assert.assertEquals(0, info.getScore());
        Assert.assertEquals(0, info.getSuccessfulHandshakes());
        Assert.assertEquals(0, info.getFailedHandshakes());
        Assert.assertEquals(0, info.getRepeatedMessages());
        Assert.assertEquals(0, info.getInvalidNetworks());
        Assert.assertEquals("nodeid", info.getId());
        Assert.assertEquals("node", info.getType());
    }

    @Test
    public void getInformationFromScoringWithTwoValidBlocks() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.VALID_BLOCK);
        scoring.updateScoring(EventType.VALID_BLOCK);

        PeerScoringInformation info = PeerScoringInformation.buildByScoring(scoring, "nodeid", "node");

        Assert.assertTrue(info.getGoodReputation());
        Assert.assertEquals(2, info.getValidBlocks());
        Assert.assertEquals(0, info.getInvalidBlocks());
        Assert.assertEquals(0, info.getValidTransactions());
        Assert.assertEquals(0, info.getInvalidTransactions());
        Assert.assertTrue(info.getScore() > 0);
    }

    @Test
    public void getInformationFromScoringWithThreeInvalidBlocks() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.INVALID_BLOCK);
        scoring.updateScoring(EventType.INVALID_BLOCK);
        scoring.updateScoring(EventType.INVALID_BLOCK);

        PeerScoringInformation info = PeerScoringInformation.buildByScoring(scoring, "node", "nodeid");

        Assert.assertTrue(info.getGoodReputation());
        Assert.assertEquals(0, info.getValidBlocks());
        Assert.assertEquals(3, info.getInvalidBlocks());
        Assert.assertEquals(0, info.getValidTransactions());
        Assert.assertEquals(0, info.getInvalidTransactions());
        Assert.assertTrue(info.getScore() < 0);
    }

    @Test
    public void getInformationFromScoringWithTwoValidTransactions() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.VALID_TRANSACTION);
        scoring.updateScoring(EventType.VALID_TRANSACTION);

        PeerScoringInformation info = PeerScoringInformation.buildByScoring(scoring, "nodeid", "node");

        Assert.assertTrue(info.getGoodReputation());
        Assert.assertEquals(0, info.getValidBlocks());
        Assert.assertEquals(0, info.getInvalidBlocks());
        Assert.assertEquals(2, info.getValidTransactions());
        Assert.assertEquals(0, info.getInvalidTransactions());
        Assert.assertTrue(info.getScore() > 0);
    }

    @Test
    public void getInformationFromScoringWithThreeInvalidTransactions() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.INVALID_TRANSACTION);
        scoring.updateScoring(EventType.INVALID_TRANSACTION);
        scoring.updateScoring(EventType.INVALID_TRANSACTION);

        PeerScoringInformation info = PeerScoringInformation.buildByScoring(scoring, "nodeid", "node");

        Assert.assertTrue(info.getGoodReputation());
        Assert.assertEquals(0, info.getValidBlocks());
        Assert.assertEquals(0, info.getInvalidBlocks());
        Assert.assertEquals(0, info.getValidTransactions());
        Assert.assertEquals(3, info.getInvalidTransactions());
        Assert.assertTrue(info.getScore() < 0);
    }

    @Test
    public void newStatusHasNoTimeLostGoodReputation() {
        PeerScoring scoring = new PeerScoring("id1");

        Assert.assertEquals(0, scoring.getTimeLostGoodReputation());
    }

    @Test
    public void recordEvent() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.INVALID_BLOCK);

        Assert.assertEquals(1, scoring.getEventCounter(EventType.INVALID_BLOCK));
        Assert.assertEquals(0, scoring.getEventCounter(EventType.INVALID_TRANSACTION));
        Assert.assertEquals(1, scoring.getTotalEventCounter());
    }

    @Test
    public void recordManyEvent() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.INVALID_BLOCK);
        scoring.updateScoring(EventType.INVALID_BLOCK);
        scoring.updateScoring(EventType.INVALID_BLOCK);

        Assert.assertEquals(3, scoring.getEventCounter(EventType.INVALID_BLOCK));
        Assert.assertEquals(0, scoring.getEventCounter(EventType.INVALID_TRANSACTION));
        Assert.assertEquals(3, scoring.getTotalEventCounter());
    }

    @Test
    public void recordManyEventOfDifferentType() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.INVALID_BLOCK);
        scoring.updateScoring(EventType.INVALID_BLOCK);
        scoring.updateScoring(EventType.INVALID_BLOCK);
        scoring.updateScoring(EventType.INVALID_TRANSACTION);
        scoring.updateScoring(EventType.INVALID_TRANSACTION);

        Assert.assertEquals(3, scoring.getEventCounter(EventType.INVALID_BLOCK));
        Assert.assertEquals(2, scoring.getEventCounter(EventType.INVALID_TRANSACTION));
        Assert.assertEquals(5, scoring.getTotalEventCounter());
    }

    @Test
    public void getZeroScoreWhenEmpty() {
        PeerScoring scoring = new PeerScoring("id1");

        Assert.assertEquals(0, scoring.getScore());
    }

    @Test
    public void getPositiveScoreWhenValidBlock() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.VALID_BLOCK);

        Assert.assertTrue(scoring.getScore() > 0);
    }

    @Test
    public void getNegativeScoreWhenInvalidBlock() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.INVALID_BLOCK);

        Assert.assertTrue(scoring.getScore() < 0);
    }

    @Test
    public void getPositiveScoreWhenValidTransaction() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.VALID_TRANSACTION);

        Assert.assertTrue(scoring.getScore() > 0);
    }

    @Test
    public void getNegativeScoreWhenInvalidTransaction() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.INVALID_TRANSACTION);

        Assert.assertTrue(scoring.getScore() < 0);
    }

    @Test
    public void getNegativeScoreWhenValidAndInvalidTransaction() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.VALID_TRANSACTION);
        scoring.updateScoring(EventType.INVALID_TRANSACTION);

        Assert.assertTrue(scoring.getScore() < 0);
    }

    @Test
    public void getNegativeScoreWhenInvalidAndValidTransaction() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.INVALID_TRANSACTION);
        scoring.updateScoring(EventType.VALID_TRANSACTION);

        Assert.assertTrue(scoring.getScore() < 0);
    }

    @Test
    public void twoValidEventsHasBetterScoreThanOnlyOne() {
        PeerScoring scoring1 = new PeerScoring("id1");
        PeerScoring scoring2 = new PeerScoring("id2");

        scoring1.updateScoring(EventType.VALID_TRANSACTION);
        scoring1.updateScoring(EventType.VALID_TRANSACTION);

        scoring2.updateScoring(EventType.VALID_TRANSACTION);

        Assert.assertTrue(scoring1.getScore() > scoring2.getScore());
    }

    @Test
    public void startPunishment() throws InterruptedException {
        PeerScoring scoring = new PeerScoring("id1");

        Assert.assertEquals(0, scoring.getPunishmentTime());
        Assert.assertEquals(0, scoring.getPunishmentCounter());

        int expirationTime = 1000;
        scoring.startPunishment(expirationTime);

        Assert.assertEquals(1, scoring.getPunishmentCounter());
        Assert.assertFalse(scoring.refreshReputationAndPunishment());
        Assert.assertEquals(expirationTime, scoring.getPunishmentTime());
        Assert.assertEquals(scoring.getTimeLostGoodReputation() + expirationTime, scoring.getPunishedUntil());
        TimeUnit.MILLISECONDS.sleep(10);
        Assert.assertFalse(scoring.refreshReputationAndPunishment());
        TimeUnit.MILLISECONDS.sleep(2000);
        Assert.assertTrue(scoring.refreshReputationAndPunishment());
        Assert.assertEquals(1, scoring.getPunishmentCounter());
        Assert.assertEquals(0, scoring.getPunishedUntil());
    }
}
