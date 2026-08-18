package com.company.rotations.spi;

import com.company.rotations.models.GenericAlertModel;
import java.util.List;
import java.util.Map;

public interface AlertAdapter {
    String VERSION = "1.0.0";

    GenericAlertModel toGenericAlert(Map<String, Object> rawPayload);

    String getProviderName();

    default String getVersion() {
        return VERSION;
    }
}
