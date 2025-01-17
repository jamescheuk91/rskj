package co.rsk.peg;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.ScriptException;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.Utils;
import co.rsk.bitcoinj.core.VerificationException;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.ErpFederationRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeTestNetConstants;
import co.rsk.peg.resources.TestConstants;
import java.math.BigInteger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.ECKey;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class ErpFederationTest {
    private ErpFederation federation;
    private ActivationConfig.ForBlock activations;

    // Default federation multisig keys
    private static final List<BtcECKey> DEFAULT_FED_KEYS = Arrays.stream(new String[]{
        "03b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b24",
        "029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e43301",
        "03284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd",
        "03776b1fd8f86da3c1db3d69699e8250a15877d286734ea9a6da8e9d8ad25d16c1",
        "03ab0e2cd7ed158687fc13b88019990860cdb72b1f5777b58513312550ea1584bc"
    }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

    // Emergency multisig keys
    private static final List<BtcECKey> ERP_KEYS = Arrays.stream(new String[]{
        "02ed3bace23c5e17652e174c835fb72bf53ee306b3406a26890221b4cef7500f88",
        "0385a7b790fc9d962493788317e4874a4ab07f1e9c78c773c47f2f6c96df756f05",
        "03cd5a3be41717d65683fe7a9de8ae5b4b8feced69f26a8b55eeefbcc2e74b75fb"
    }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

    private static final long ACTIVATION_DELAY_VALUE = 5063;

    @Before
    public void setup() {
        activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        federation = createDefaultErpFederation();
    }

    @Test
    public void getErpPubKeys() {
        Assert.assertEquals(ERP_KEYS, federation.getErpPubKeys());
    }

    @Test
    public void getActivationDelay() {
        Assert.assertEquals(ACTIVATION_DELAY_VALUE, federation.getActivationDelay());
    }

    @Test
    public void getRedeemScript_before_RSKIP293() {
        Script redeemScript = federation.getRedeemScript();
        validateErpRedeemScript(
            redeemScript,
            ACTIVATION_DELAY_VALUE,
            false
        );
    }

    @Test
    public void getRedeemScript_after_RSKIP293() {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        federation = createDefaultErpFederation();
        Script redeemScript = federation.getRedeemScript();

        validateErpRedeemScript(
            redeemScript,
            ACTIVATION_DELAY_VALUE,
            true
        );
    }

    @Test
    public void getRedeemScript_changes_after_RSKIP293() {
        Script preRskip293RedeemScript = federation.getRedeemScript();

        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        federation = createDefaultErpFederation();
        Script postRskip293RedeemScript = federation.getRedeemScript();

        Assert.assertNotEquals(preRskip293RedeemScript, postRskip293RedeemScript);
    }

    @Test
    public void getP2SHScript() {
        Script p2shs = federation.getP2SHScript();
        String expectedProgram = "a914867df89b0837b2fd86eb98751d14b6cef52d342487";

        Assert.assertEquals(expectedProgram, Hex.toHexString(p2shs.getProgram()));
        Assert.assertEquals(3, p2shs.getChunks().size());
        Assert.assertEquals(
            federation.getAddress(),
            p2shs.getToAddress(NetworkParameters.fromID(NetworkParameters.ID_REGTEST))
        );
    }

    @Test
    public void getAddress() {
        String fedAddress = federation.getAddress().toBase58();
        String expectedAddress = "2N5WMScfkbBVWMByrGw6GZDFF3m4tb3qqP8";

        Assert.assertEquals(expectedAddress, fedAddress);
    }

    @Test
    public void getErpPubKeys_compressed_public_keys() {
        Assert.assertEquals(ERP_KEYS, federation.getErpPubKeys());
    }

    @Test
    public void getErpPubKeys_uncompressed_public_keys() {
        // Public keys used for creating federation, but uncompressed format now
        List<BtcECKey> uncompressedErpKeys = ERP_KEYS
            .stream()
            .map(BtcECKey::decompress)
            .collect(Collectors.toList());

        // Recreate federation
        ErpFederation federationWithUncompressedKeys = new ErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(DEFAULT_FED_KEYS),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
            uncompressedErpKeys,
            ACTIVATION_DELAY_VALUE,
            mock(ActivationConfig.ForBlock.class)
        );

        Assert.assertEquals(ERP_KEYS, federationWithUncompressedKeys.getErpPubKeys());
    }

    @Test
    public void getErpRedeemScript_compareOtherImplementation() throws IOException {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

        byte[] rawRedeemScripts;
        try {
            rawRedeemScripts = Files.readAllBytes(Paths.get("src/test/resources/redeemScripts.json"));
        } catch (IOException e) {
            System.out.println("redeemScripts.json file not found");
            throw(e);
        }

        RawGeneratedRedeemScript[] generatedScripts = new ObjectMapper().readValue(rawRedeemScripts, RawGeneratedRedeemScript[].class);
        for (RawGeneratedRedeemScript generatedScript : generatedScripts) {
            Federation erpFederation = new ErpFederation(
                FederationTestUtils.getFederationMembersWithBtcKeys(generatedScript.mainFed),
                ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_TESTNET),
                generatedScript.emergencyFed,
                generatedScript.timelock,
                activations
            );

            Script rskjScript = erpFederation.getRedeemScript();
            Script alternativeScript = generatedScript.script;
            Assert.assertEquals(alternativeScript, rskjScript);
        }
    }

    @Test
    public void createErpFederation_testnet_constants_before_RSKIP293() {
        createErpFederation(BridgeTestNetConstants.getInstance(), false);
    }

    @Test
    public void createErpFederation_testnet_constants_after_RSKIP293() {
        createErpFederation(BridgeTestNetConstants.getInstance(), true);
    }

    @Test
    public void createErpFederation_mainnet_constants_before_RSKIP293() {
        createErpFederation(BridgeMainNetConstants.getInstance(), false);
    }

    @Test
    public void createErpFederation_mainnet_constants_after_RSKIP293() {
        createErpFederation(BridgeMainNetConstants.getInstance(), true);
    }

    @Test(expected = VerificationException.class)
    public void createInvalidErpFederation_negativeCsvValue() {
        new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
            ERP_KEYS,
            -100L,
            activations
        );
    }

    @Test(expected = VerificationException.class)
    public void createInvalidErpFederation_csvValueNegative() {
        new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
            ERP_KEYS,
            -100,
            activations
        );
    }

    @Test(expected = VerificationException.class)
    public void createInvalidErpFederation_csvValueZero() {
        new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
            ERP_KEYS,
            0,
            activations
        );
    }

    @Test(expected = VerificationException.class)
    public void createInvalidErpFederation_csvValueAboveMax() {
        new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
            ERP_KEYS,
            ErpFederationRedeemScriptParser.MAX_CSV_VALUE + 1,
            activations
        );
    }

    @Test
    public void getRedeemScript_before_RSKIP_284_testnet() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        Federation erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_TESTNET),
            ERP_KEYS,
            ACTIVATION_DELAY_VALUE,
            activations
        );

        Assert.assertEquals(TestConstants.ERP_TESTNET_REDEEM_SCRIPT, erpFederation.getRedeemScript());
    }

    @Test
    public void getRedeemScript_before_RSKIP_284_mainnet() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        Federation erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(DEFAULT_FED_KEYS),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_MAINNET),
            ERP_KEYS,
            ACTIVATION_DELAY_VALUE,
            activations
        );

        Assert.assertNotEquals(TestConstants.ERP_TESTNET_REDEEM_SCRIPT, erpFederation.getRedeemScript());
        validateErpRedeemScript(
            erpFederation.getRedeemScript(),
            ACTIVATION_DELAY_VALUE,
            false
        );
    }

    @Test
    public void getRedeemScript_after_RSKIP_284_testnet() {
        Federation erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(DEFAULT_FED_KEYS),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_TESTNET),
            ERP_KEYS,
            ACTIVATION_DELAY_VALUE,
            activations
        );

        Assert.assertNotEquals(TestConstants.ERP_TESTNET_REDEEM_SCRIPT, erpFederation.getRedeemScript());
        validateErpRedeemScript(
            erpFederation.getRedeemScript(),
            ACTIVATION_DELAY_VALUE,
            false
        );
    }

    @Test
    public void getRedeemScript_after_RSKIP_284_mainnet() {
        Federation erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(DEFAULT_FED_KEYS),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_MAINNET),
            ERP_KEYS,
            ACTIVATION_DELAY_VALUE,
            activations
        );

        Assert.assertNotEquals(TestConstants.ERP_TESTNET_REDEEM_SCRIPT, erpFederation.getRedeemScript());
        validateErpRedeemScript(
            erpFederation.getRedeemScript(),
            ACTIVATION_DELAY_VALUE,
            false
        );
    }

    @Test
    public void testEquals_basic() {
        Assert.assertEquals(federation, federation);

        Assert.assertNotEquals(null, federation);
        Assert.assertNotEquals(federation, new Object());
        Assert.assertNotEquals("something else", federation);
    }

    @Test
    public void testEquals_same() {
        Federation otherFederation = new ErpFederation(
            federation.getMembers(),
            federation.getCreationTime(),
            federation.getCreationBlockNumber(),
            federation.getBtcParams(),
            federation.getErpPubKeys(),
            federation.getActivationDelay(),
            activations
        );

        Assert.assertEquals(federation, otherFederation);
    }

    @Test
    public void testEquals_differentNumberOfMembers() {
        Federation otherFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500, 600, 700),
            federation.getCreationTime(),
            federation.getCreationBlockNumber(),
            federation.getBtcParams(),
            federation.getErpPubKeys(),
            federation.getActivationDelay(),
            activations
        );

        Assert.assertNotEquals(federation, otherFederation);
    }

    @Test
    public void testEquals_differentCreationTime() {
        Federation otherFederation = new ErpFederation(
            federation.getMembers(),
            Instant.now(),
            federation.getCreationBlockNumber(),
            federation.getBtcParams(),
            federation.getErpPubKeys(),
            federation.getActivationDelay(),
            activations
        );

        Assert.assertNotEquals(federation, otherFederation);
    }

    @Test
    public void testEquals_differentCreationBlockNumber() {
        Federation otherFederation = new ErpFederation(
            federation.getMembers(),
            federation.getCreationTime(),
            federation.getCreationBlockNumber() + 1,
            federation.getBtcParams(),
            federation.getErpPubKeys(),
            federation.getActivationDelay(),
            activations
        );

        Assert.assertNotEquals(federation, otherFederation);
    }

    @Test
    public void testEquals_differentNetworkParameters() {
        Federation otherFederation = new ErpFederation(
            federation.getMembers(),
            federation.getCreationTime(),
            federation.getCreationBlockNumber(),
            NetworkParameters.fromID(NetworkParameters.ID_MAINNET),
            federation.getErpPubKeys(),
            federation.getActivationDelay(),
            activations
        );

        Assert.assertNotEquals(federation, otherFederation);
    }

    @Test
    public void testEquals_differentMembers() {
        Federation otherFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(101, 201, 301),
            federation.getCreationTime(),
            federation.getCreationBlockNumber(),
            federation.getBtcParams(),
            federation.getErpPubKeys(),
            federation.getActivationDelay(),
            activations
        );

        Assert.assertNotEquals(federation, otherFederation);
    }

    @Test
    public void testEquals_differentRedeemScript() {
        ActivationConfig.ForBlock activationsPre = mock(ActivationConfig.ForBlock.class);
        when(activationsPre.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        ActivationConfig.ForBlock activationsPost = mock(ActivationConfig.ForBlock.class);
        when(activationsPost.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        // Both federations created before RSKIP284 with the same data, should have the same redeem script
        Federation erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_TESTNET),
            ERP_KEYS,
            ACTIVATION_DELAY_VALUE,
            activationsPre
        );

        Federation otherErpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_TESTNET),
            ERP_KEYS,
            ACTIVATION_DELAY_VALUE,
            activationsPre
        );

        Assert.assertEquals(erpFederation, otherErpFederation);

        // One federation created after RSKIP284 with the same data, should have different redeem script
        otherErpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_TESTNET),
            ERP_KEYS,
            ACTIVATION_DELAY_VALUE,
            activationsPost
        );

        Assert.assertNotEquals(erpFederation, otherErpFederation);

        // The other federation created after RSKIP284 with the same data, should have same redeem script
        erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_TESTNET),
            ERP_KEYS,
            ACTIVATION_DELAY_VALUE,
            activationsPost
        );

        Assert.assertEquals(erpFederation, otherErpFederation);
    }

    @Ignore("Can't recreate the hardcoded redeem script since the needed CSV value is above the max. Keeping the test ignored as testimonial")
    @Test(expected = FederationCreationException.class)
    public void createErpFedWithSameRedeemScriptAsHardcodedOne_after_RSKIP293_fails() {
        // We can't test the same condition before RSKIP293 since the serialization used by bj-thin
        // prior to RSKIP293 enforces the CSV value to be encoded using 2 bytes.
        // The hardcoded script has a 3 byte long CSV value
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

        List<BtcECKey> standardMultisigKeys = Arrays.stream(new String[]{
            "0208f40073a9e43b3e9103acec79767a6de9b0409749884e989960fee578012fce",
            "0225e892391625854128c5c4ea4340de0c2a70570f33db53426fc9c746597a03f4",
            "02afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da",
            "0344a3c38cd59afcba3edcebe143e025574594b001700dec41e59409bdbd0f2a09",
            "039a060badbeb24bee49eb2063f616c0f0f0765d4ca646b20a88ce828f259fcdb9"
        }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

        List<BtcECKey> emergencyMultisigKeys = Arrays.stream(new String[]{
            "0216c23b2ea8e4f11c3f9e22711addb1d16a93964796913830856b568cc3ea21d3",
            "0275562901dd8faae20de0a4166362a4f82188db77dbed4ca887422ea1ec185f14",
            "034db69f2112f4fb1bb6141bf6e2bd6631f0484d0bd95b16767902c9fe219d4a6f"
        }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

        long activationDelay = 5_295_360L;

        new ErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(standardMultisigKeys),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_TESTNET),
            emergencyMultisigKeys,
            activationDelay,
            activations
        );
    }

    @Test(expected = Test.None.class)
    public void spendFromErpFed_before_RSKIP293_testnet_using_erp_multisig_can_spend() {
        BridgeConstants constants = BridgeTestNetConstants.getInstance();

        // The CSV value defined in BridgeTestnetConstants,
        // actually allows the emergency multisig to spend before the expected amount of blocks
        // Since it's encoded as BE and decoded as LE, the result is a number lower than the one defined in the constant
        spendFromErpFed(
            constants.getBtcParams(),
            constants.getErpFedActivationDelay(),
            false,
            true
        );
    }

    @Test(expected = ScriptException.class)
    public void spendFromErpFed_before_RSKIP293_testnet_using_erp_multisig_cant_spend() {
        BridgeConstants constants = BridgeTestNetConstants.getInstance();

        // Should fail due to the wrong encoding of the CSV value
        // In this case, the value 300 when encoded as BE and decoded as LE results in a larger number
        // This causes the validation to fail
        spendFromErpFed(
            constants.getBtcParams(),
            300,
            false,
            true
        );
    }

    @Test(expected = Test.None.class)
    public void spendFromErpFed_before_RSKIP293_testnet_using_standard_multisig() {
        BridgeConstants constants = BridgeTestNetConstants.getInstance();

        // Should validate since it's not executing the path of the script with the CSV value
        spendFromErpFed(
            constants.getBtcParams(),
            constants.getErpFedActivationDelay(),
            false,
            false
        );
    }

    @Test(expected = Test.None.class)
    public void spendFromErpFed_before_RSKIP293_mainnet_using_erp_multisig_can_spend() {
        BridgeConstants constants = BridgeMainNetConstants.getInstance();

        // The CSV value defined in BridgeMainnetConstants,
        // actually allows the emergency multisig to spend before the expected amount of blocks
        // Since it's encoded as BE and decoded as LE, the result is a number lower than the one defined in the constant
        spendFromErpFed(
            constants.getBtcParams(),
            constants.getErpFedActivationDelay(),
            false,
            true
        );
    }

    @Test(expected = ScriptException.class)
    public void spendFromErpFed_before_RSKIP293_mainnet_using_erp_multisig_cant_spend() {
        BridgeConstants constants = BridgeMainNetConstants.getInstance();

        // Should fail due to the wrong encoding of the CSV value
        // In this case, the value 300 when encoded as BE and decoded as LE results in a larger number
        // This causes the validation to fail
        spendFromErpFed(
            constants.getBtcParams(),
            300,
            false,
            true
        );
    }

    @Test(expected = Test.None.class)
    public void spendFromErpFed_before_RSKIP293_mainnet_using_standard_multisig() {
        BridgeConstants constants = BridgeMainNetConstants.getInstance();

        // Should validate since it's not executing the path of the script with the CSV value
        spendFromErpFed(
            constants.getBtcParams(),
            constants.getErpFedActivationDelay(),
            false,
            false
        );
    }

    @Test(expected = Test.None.class)
    public void spendFromErpFed_after_RSKIP293_testnet_using_erp_multisig() {
        BridgeConstants constants = BridgeTestNetConstants.getInstance();

        // Post RSKIP293 activation it should encode the CSV value correctly
        spendFromErpFed(
            constants.getBtcParams(),
            constants.getErpFedActivationDelay(),
            true,
            true
        );
    }

    @Test(expected = Test.None.class)
    public void spendFromErpFed_after_RSKIP293_testnet_using_standard_multisig() {
        BridgeConstants constants = BridgeTestNetConstants.getInstance();

        spendFromErpFed(
            constants.getBtcParams(),
            constants.getErpFedActivationDelay(),
            true,
            false
        );
    }

    @Test(expected = Test.None.class)
    public void spendFromErpFed_after_RSKIP293_mainnet_using_erp_multisig() {
        BridgeConstants constants = BridgeMainNetConstants.getInstance();

        // Post RSKIP293 activation it should encode the CSV value correctly
        spendFromErpFed(
            constants.getBtcParams(),
            constants.getErpFedActivationDelay(),
            true,
            true
        );
    }

    @Test(expected = Test.None.class)
    public void spendFromErpFed_after_RSKIP293_mainnet_using_standard_multisig() {
        BridgeConstants constants = BridgeMainNetConstants.getInstance();

        spendFromErpFed(
            constants.getBtcParams(),
            constants.getErpFedActivationDelay(),
            true,
            false
        );
    }

    private void spendFromErpFed(
        NetworkParameters networkParameters,
        long activationDelay,
        boolean isRskip293Active,
        boolean signWithEmergencyMultisig) {

        // Created with GenNodeKeyId using seed 'fed1'
        byte[] publicKeyBytes = Hex.decode("043267e382e076cbaa199d49ea7362535f95b135de181caf66b391f541bf39ab0e75b8577faac2183782cb0d76820cf9f356831d216e99d886f8a6bc47fe696939");
        BtcECKey btcKey = BtcECKey.fromPublicOnly(publicKeyBytes);
        ECKey rskKey = ECKey.fromPublicOnly(publicKeyBytes);
        FederationMember fed1 = new FederationMember(btcKey, rskKey, rskKey);
        BtcECKey fed1PrivKey = BtcECKey.fromPrivate(Hex.decode("529822842595a3a6b3b3e51e9cffa0db66452599f7beec542382a02b1e42be4b"));

        // Created with GenNodeKeyId using seed 'fed3', used for fed2 to keep keys sorted
        publicKeyBytes = Hex.decode("0443e106d90183e2eef7d5cb7538a634439bf1301d731787c6736922ff19e750ed39e74a76731fed620aeedbcd77e4de403fc4148efd3b5dbfc6cef550aa63c377");
        btcKey = BtcECKey.fromPublicOnly(publicKeyBytes);
        rskKey = ECKey.fromPublicOnly(publicKeyBytes);
        FederationMember fed2 = new FederationMember(btcKey, rskKey, rskKey);
        BtcECKey fed2PrivKey = BtcECKey.fromPrivate(Hex.decode("b2889610e66cd3f7de37c81c20c786b576349b80b3f844f8409e3a29d95c0c7c"));

        // Created with GenNodeKeyId using seed 'fed2', used for fed3 to keep keys sorted
        publicKeyBytes = Hex.decode("04bd5b51b1c5d799da190285c8078a2712b8e5dc6f73c799751e6256bb89a4bd04c6444b00289fc76ee853fcfa52b3083d66c42e84f8640f53a4cdf575e4d4a399");
        btcKey = BtcECKey.fromPublicOnly(publicKeyBytes);
        rskKey = ECKey.fromPublicOnly(publicKeyBytes);
        FederationMember fed3 = new FederationMember(btcKey, rskKey, rskKey);
        BtcECKey fed3PrivKey = BtcECKey.fromPrivate(Hex.decode("fa013890aa14dd269a0ca16003cabde1688021358b662d17b1e8c555f5cccc6e"));

        // Created with GenNodeKeyId using seed 'erp1'
        publicKeyBytes = Hex.decode("048f5a88b08d75765b36951254e68060759de5be7e559972c37c67fc8cedafeb2643a4a8a618125530e275fe310c72dbdd55fa662cdcf8e134012f8a8d4b7e8400");
        BtcECKey erp1Key = BtcECKey.fromPublicOnly(publicKeyBytes);
        BtcECKey erp1PrivKey = BtcECKey.fromPrivate(Hex.decode("1f28656deb5f108f8cdf14af34ac4ff7a5643a7ac3f77b8de826b9ad9775f0ca"));

        // Created with GenNodeKeyId using seed 'erp2'
        publicKeyBytes = Hex.decode("04deba35a96add157b6de58f48bb6e23bcb0a17037bed1beb8ba98de6b0a0d71d60f3ce246954b78243b41337cf8f93b38563c3bcd6a5329f1d68c057d0e5146e8");
        BtcECKey erp2Key = BtcECKey.fromPublicOnly(publicKeyBytes);
        BtcECKey erp2PrivKey = BtcECKey.fromPrivate(Hex.decode("4e58ebe9cd04ffea5ab81dd2aded3ab8a63e44f3b47aef334e369d895c351646"));

        // Created with GenNodeKeyId using seed 'erp3'
        publicKeyBytes = Hex.decode("04c34fcd05cef2733ea7337c37f50ae26245646aba124948c6ff8dcdf82128499808fc9148dfbc0e0ab510b4f4a78bf7a58f8b6574e03dae002533c5059973b61f");
        BtcECKey erp3Key = BtcECKey.fromPublicOnly(publicKeyBytes);
        BtcECKey erp3PrivKey = BtcECKey.fromPrivate(Hex.decode("57e8d2cd51c3b076ca96a1043c8c6d32c6c18447e411a6279cda29d70650977b"));

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(isRskip293Active);
        ErpFederation erpFed = new ErpFederation(
            Arrays.asList(fed1, fed2, fed3),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            networkParameters,
            Arrays.asList(erp1Key, erp2Key, erp3Key),
            activationDelay,
            activations
        );

        // Below code can be used to create a transaction spending from the emergency multisig in testnet or mainnet
//        String RAW_FUND_TX = "";
//        BtcTransaction pegInTx = new BtcTransaction(networkParameters, Hex.decode(RAW_FUND_TX));
        int outputIndex = 0; // Remember to change this value accordingly in case of using an existing raw tx
        BtcTransaction pegInTx = new BtcTransaction(networkParameters);
        pegInTx.addOutput(Coin.valueOf(990_000), erpFed.getAddress());

        Address destinationAddress = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        BtcTransaction pegOutTx = new BtcTransaction(networkParameters);
        pegOutTx.addInput(pegInTx.getOutput(outputIndex));
        pegOutTx.addOutput(Coin.valueOf(900_000), destinationAddress);
        pegOutTx.setVersion(2);
        pegOutTx.getInput(0).setSequenceNumber(activationDelay);

        // Create signatures
        Sha256Hash sigHash = pegOutTx.hashForSignature(
            0,
            erpFed.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        BtcECKey.ECDSASignature signature1;
        BtcECKey.ECDSASignature signature2;
        BtcECKey.ECDSASignature signature3;
        if (signWithEmergencyMultisig) {
            signature1 = erp1PrivKey.sign(sigHash);
            signature2 = erp2PrivKey.sign(sigHash);
            signature3 = erp3PrivKey.sign(sigHash);
        } else {
            signature1 = fed1PrivKey.sign(sigHash);
            signature2 = fed2PrivKey.sign(sigHash);
            signature3 = fed3PrivKey.sign(sigHash);
        }

        // Try different signature permutations
        Script inputScript = createInputScript(erpFed.getRedeemScript(), signature1, signature2, signWithEmergencyMultisig);
        pegOutTx.getInput(0).setScriptSig(inputScript);
        inputScript.correctlySpends(pegOutTx,0, pegInTx.getOutput(outputIndex).getScriptPubKey());

        inputScript = createInputScript(erpFed.getRedeemScript(), signature1, signature3, signWithEmergencyMultisig);
        pegOutTx.getInput(0).setScriptSig(inputScript);
        inputScript.correctlySpends(pegOutTx,0, pegInTx.getOutput(outputIndex).getScriptPubKey());

        inputScript = createInputScript(erpFed.getRedeemScript(), signature2, signature3, signWithEmergencyMultisig);
        pegOutTx.getInput(0).setScriptSig(inputScript);
        inputScript.correctlySpends(pegOutTx,0, pegInTx.getOutput(outputIndex).getScriptPubKey());

        // Uncomment to print the raw tx in console and broadcast https://blockstream.info/testnet/tx/push
//        System.out.println(Hex.toHexString(pegOutTx.bitcoinSerialize()));
    }

    private void createErpFederation(BridgeConstants constants, boolean isRskip293Active) {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(isRskip293Active);

        Federation erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(DEFAULT_FED_KEYS),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            constants.getBtcParams(),
            constants.getErpFedPubKeysList(),
            constants.getErpFedActivationDelay(),
            activations
        );

        List<BtcECKey> sortedErpKeys = constants.getErpFedPubKeysList()
            .stream()
            .sorted(BtcECKey.PUBKEY_COMPARATOR)
            .collect(Collectors.toList());
        validateErpRedeemScript(
            erpFederation.getRedeemScript(),
            DEFAULT_FED_KEYS,
            sortedErpKeys,
            constants.getErpFedActivationDelay(),
            isRskip293Active
        );
    }

    private Script createInputScript(
        Script fedRedeemScript,
        BtcECKey.ECDSASignature signature1,
        BtcECKey.ECDSASignature signature2,
        boolean signWithTheEmergencyMultisig) {

        TransactionSignature txSignature1 = new TransactionSignature(
            signature1,
            BtcTransaction.SigHash.ALL,
            false
        );
        byte[] txSignature1Encoded = txSignature1.encodeToBitcoin();

        TransactionSignature txSignature2 = new TransactionSignature(
            signature2,
            BtcTransaction.SigHash.ALL,
            false
        );
        byte[] txSignature2Encoded = txSignature2.encodeToBitcoin();

        int flowOpCode = signWithTheEmergencyMultisig ? 1 : 0;
        ScriptBuilder scriptBuilder = new ScriptBuilder();
        return scriptBuilder
            .number(0)
            .data(txSignature1Encoded)
            .data(txSignature2Encoded)
            .number(flowOpCode)
            .data(fedRedeemScript.getProgram())
            .build();
    }

    private ErpFederation createDefaultErpFederation() {
        return new ErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(DEFAULT_FED_KEYS),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
            ERP_KEYS,
            ACTIVATION_DELAY_VALUE,
            activations
        );
    }

    private void validateErpRedeemScript(
        Script erpRedeemScript,
        Long csvValue,
        boolean isRskip293Active) {

        validateErpRedeemScript(
            erpRedeemScript,
            DEFAULT_FED_KEYS,
            ERP_KEYS,
            csvValue,
            isRskip293Active
        );
    }

    private void validateErpRedeemScript(
        Script erpRedeemScript,
        List<BtcECKey> defaultMultisigKeys,
        List<BtcECKey> emergencyMultisigKeys,
        Long csvValue,
        boolean isRskip293Active) {

        int expectedCsvValueLength = isRskip293Active ? BigInteger.valueOf(csvValue).toByteArray().length : 2;
        byte[] serializedCsvValue = isRskip293Active ?
            Utils.signedLongToByteArrayLE(csvValue) :
            Utils.unsignedLongToByteArrayBE(csvValue, expectedCsvValueLength);

        byte[] script = erpRedeemScript.getProgram();
        Assert.assertTrue(script.length > 0);

        int index = 0;

        // First byte should equal OP_NOTIF
        Assert.assertEquals(ScriptOpCodes.OP_NOTIF, script[index++]);

        // Next byte should equal M, from an M/N multisig
        int m = defaultMultisigKeys.size() / 2 + 1;
        Assert.assertEquals(ScriptOpCodes.getOpCode(String.valueOf(m)), script[index++]);

        // Assert public keys
        for (BtcECKey key: defaultMultisigKeys) {
            byte[] pubkey = key.getPubKey();
            Assert.assertEquals(pubkey.length, script[index++]);
            for (byte b : pubkey) {
                Assert.assertEquals(b, script[index++]);
            }
        }

        // Next byte should equal N, from an M/N multisig
        int n = defaultMultisigKeys.size();
        Assert.assertEquals(ScriptOpCodes.getOpCode(String.valueOf(n)), script[index++]);

        // Next byte should equal OP_ELSE
        Assert.assertEquals(ScriptOpCodes.OP_ELSE, script[index++]);

        // Next byte should equal csv value length
        Assert.assertEquals(expectedCsvValueLength, script[index++]);

        // Next bytes should equal the csv value in bytes
        for (int i = 0; i < expectedCsvValueLength; i++) {
            Assert.assertEquals(serializedCsvValue[i], script[index++]);
        }

        Assert.assertEquals(Integer.valueOf(ScriptOpCodes.OP_CHECKSEQUENCEVERIFY).byteValue(), script[index++]);
        Assert.assertEquals(ScriptOpCodes.OP_DROP, script[index++]);

        // Next byte should equal M, from an M/N multisig
        m = emergencyMultisigKeys.size() / 2 + 1;
        Assert.assertEquals(ScriptOpCodes.getOpCode(String.valueOf(m)), script[index++]);

        for (BtcECKey key: emergencyMultisigKeys) {
            byte[] pubkey = key.getPubKey();
            Assert.assertEquals(Integer.valueOf(pubkey.length).byteValue(), script[index++]);
            for (byte b : pubkey) {
                Assert.assertEquals(b, script[index++]);
            }
        }

        // Next byte should equal N, from an M/N multisig
        n = emergencyMultisigKeys.size();
        Assert.assertEquals(ScriptOpCodes.getOpCode(String.valueOf(n)), script[index++]);

        Assert.assertEquals(ScriptOpCodes.OP_ENDIF, script[index++]);
        Assert.assertEquals(Integer.valueOf(ScriptOpCodes.OP_CHECKMULTISIG).byteValue(), script[index++]);
    }

    private static class RawGeneratedRedeemScript {
        List<BtcECKey> mainFed;
        List<BtcECKey> emergencyFed;
        Long timelock;
        Script script;

        @JsonCreator
        public RawGeneratedRedeemScript(@JsonProperty("mainFed") List<String> mainFed,
            @JsonProperty("emergencyFed") List<String> emergencyFed,
            @JsonProperty("timelock") Long timelock,
            @JsonProperty("script") String script) {
            this.mainFed = parseFed(mainFed);
            this.emergencyFed = parseFed(emergencyFed);
            this.timelock = timelock;
            this.script = new Script(Hex.decode(script));
        }

        private List<BtcECKey> parseFed(List<String> fed) {
            return fed.stream().map(Hex::decode).map(BtcECKey::fromPublicOnly).collect(Collectors.toList());
        }
    }
}
