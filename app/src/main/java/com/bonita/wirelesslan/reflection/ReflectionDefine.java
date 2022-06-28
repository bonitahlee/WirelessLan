package com.bonita.wirelesslan.reflection;

/**
 * Reflection 관련 변수 모음 클래스
 * - class, method, field 명
 *
 * @author bonita
 * @date 2020.10.19
 */
public class ReflectionDefine {

    private ReflectionDefine() {/* Nothing to do */}

    /**
     * WifiConfiguration Reflection 변수 모음
     */
    public static class WifiConfigRef {
        private WifiConfigRef() {
            throw new IllegalStateException("WifiConfigRef class");
        }

        public static final String CLASS_NAME = "android.net.wifi.WifiConfiguration";
        public static final String CLASS_KEY_MGMT = "KeyMgmt";

        // static field
        public static final String FIELD_SAE = "SAE";
        public static final String FIELD_SUITE = "SUITE_B_192";

        // public field
        public static final String FIELD_SELF_ADDED = "selfAdded";
        public static final String FIELD_NUM_ASSOCIATION = "numAssociation";
        public static final String FIELD_REQUIRE_PMF = "requirePMF";
    }

    /**
     * WifiEnterpriseConfig Reflection 변수 모음
     */
    public static class WifiEnterpriseConfigRef {
        private WifiEnterpriseConfigRef() {
            throw new IllegalStateException("WifiEnterpriseConfigRef class");
        }

        public static final String METHOD_GET_CA_PATH = "getCaPath";
        public static final String METHOD_SET_CA_PATH = "setCaPath";
        public static final String METHOD_GET_CA_ALIASES = "getCaCertificateAliases";
        public static final String METHOD_SET_CA_ALIASES = "setCaCertificateAliases";
        public static final String METHOD_GET_CLIENT_ALIASES = "getClientCertificateAlias";
        public static final String METHOD_SET_CLIENT_ALIASES = "setClientCertificateAlias";
    }

    /**
     * WifiManager Reflection 변수 모음
     */
    public static class WifiManagerRef {
        private WifiManagerRef() {
            throw new IllegalStateException("WifiManagerRef class");
        }

        public static final String CLASS_NAME = "android.net.wifi.WifiManager";
        public static final String CLASS_ACTION_LISTENER = "ActionListener";

        public static final String METHOD_IS_CONNECT_SUPPORT = "isEasyConnectSupported";
    }

    /**
     * WifiInfo Reflection 변수 모음
     */
    public static class WifiInfoRef {
        private WifiInfoRef() {
            throw new IllegalStateException("WifiInfoRef class");
        }

        public static final String METHOD_IS_PASS_POINT = "isPasspointAp";
    }

    /**
     * ScanResult Reflection 변수 모음
     */
    public static class ScanResultRef {
        private ScanResultRef() {
            throw new IllegalStateException("ScanResultRef class");
        }

        public static final String FIELD_IS_CARRIER = "isCarrierAp";
        public static final String FIELD_CARRIER_TYPE = "carrierApEapType";
    }

    /**
     * Settings Reflection 변수 모음
     */
    public static class SettingsRef {
        private SettingsRef() {
            throw new IllegalStateException("SettingsRef class");
        }

        public static final String CLASS_NAME = "android.provider.Settings";
        public static final String CLASS_GLOBAL = "Global";

        public static final String FIELD_SCAN_AVAILABLE = "WIFI_SCAN_ALWAYS_AVAILABLE";
        public static final String FIELD_WAKE_UP = "WIFI_WAKEUP_ENABLED";
        public static final String FIELD_NOTIFY_NETWORK = "WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON";
    }

    /**
     * LocationManager Reflection 변수 모음
     */
    public static class LocationRef {
        private LocationRef() {
            throw new IllegalStateException("LocationRef class");
        }

        public static final String METHOD_IS_LOCATION_ENABLED = "isLocationEnabled";
    }

    /**
     * KeyStore Reflection 변수 모음
     */
    public static class KeyStoreRef {
        private KeyStoreRef() {
        }

        public static final String CLASS_NAME = "android.security.KeyStore";

        public static final String METHOD_GET_INSTANCE = "getInstance";
        public static final String METHOD_LIST = "list";
    }
}