package co.rsk.peg;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.TransactionOutput;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;

/**
 * @deprecated Methods included in this class are to be used only prior to the latest HF activation
 */
@Deprecated
public class BridgeUtilsLegacy {

    private BridgeUtilsLegacy() {}

    @Deprecated
    protected static Address deserializeBtcAddressWithVersionLegacy(
        NetworkParameters networkParameters,
        ActivationConfig.ForBlock activations,
        byte[] addressBytes) throws BridgeIllegalArgumentException {

        if (activations.isActive(ConsensusRule.RSKIP284)) {
            throw new DeprecatedMethodCallException(
                "Calling BridgeUtils.deserializeBtcAddressWithVersionLegacy method after RSKIP284 activation"
            );
        }

        if (addressBytes == null || addressBytes.length == 0) {
            throw new BridgeIllegalArgumentException("Can't get an address version if the bytes are empty");
        }

        int version = addressBytes[0];
        byte[] hashBytes = new byte[20];
        System.arraycopy(addressBytes, 1, hashBytes, 0, 20);

        return new Address(networkParameters, version, hashBytes);
    }

    /**
     * Legacy version for getting the amount sent to a btc address.
     *
     *
     * @param activations
     * @param networkParameters
     * @param btcTx
     * @param btcAddress
     * @return total amount sent to the given address.
     */
    @Deprecated
    protected static Coin getAmountSentToAddress(
        ActivationConfig.ForBlock activations,
        NetworkParameters networkParameters,
        BtcTransaction btcTx,
        Address btcAddress
    ) {
        if (activations.isActive(ConsensusRule.RSKIP293)) {
            throw new DeprecatedMethodCallException(
                "Calling BridgeUtils. getAmountSentToAddress method after RSKIP293 activation"
            );
        }
        Coin value = Coin.ZERO;
        for (TransactionOutput output : btcTx.getOutputs()) {
            if (output.getScriptPubKey().getToAddress(networkParameters).equals(btcAddress)) {
                value = value.add(output.getValue());
            }
        }
        return value;
    }
}
