package im.angry.openeuicc.bridge;

import android.os.Bundle;
import java.util.List;

interface IDadaEsimBridge {
    Bundle getRouteState();
    Bundle switchSlot2Route(boolean toEsim);
    List<Bundle> listProfiles();
    Bundle setProfileEnabled(String iccid, boolean enabled);
    Bundle renameProfile(String iccid, String newNickname);
    Bundle downloadProfile(String smdpActivationCode, String confirmationCode);
}
