package co.rsk.peg.utils;

public class PegUtils { // TODO:I talk to Vlad about using this as a global ApplicationContext that we do not pass via parameters

    private final BridgeUtils bridgeUtils;
    private final BridgeSerializationUtils bridgeSerializationUtils;
    private final ScriptBuilderWrapper scriptBuilderWrapper;

    private static PegUtils instance;

    public static PegUtils getInstance() {
        if (instance == null) {
            instance = new PegUtils();
        }

        return instance;
    }

    private PegUtils() {
        this.bridgeUtils = BridgeUtils.getInstance();
        this.scriptBuilderWrapper = ScriptBuilderWrapper.getInstance();
        this.bridgeSerializationUtils = BridgeSerializationUtils.getInstance(scriptBuilderWrapper);
    }


    public BridgeUtils getBridgeUtils() {
        return bridgeUtils;
    }

    public BridgeSerializationUtils getBridgeSerializationUtils() {
        return bridgeSerializationUtils;
    }

    public ScriptBuilderWrapper getScriptBuilderWrapper() {
        return scriptBuilderWrapper;
    }
}