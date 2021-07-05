package c8y;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TTNUplinkRequest {

    private String appId;

    private String devId;

    private String devEui;

    private String payloadBase64Encoded;

    private int port;

    @JsonProperty("received_at")
    private String timestamp;

    @JsonProperty("end_device_ids")
    private void unpackDeviceAndApplicationMetadata(final Map<String, Object> metadata) {
        if (metadata.containsKey("device_id")) {
            devId = (String) metadata.get("device_id");
        }

        if (metadata.containsKey("dev_eui")) {
            devEui = (String) metadata.get("dev_eui");
        }

        if (metadata.containsKey("application_ids")
                && ((Map<String, Object>) metadata.get("application_ids")).containsKey("application_id")) {
            appId = (String) ((Map<String, Object>) metadata.get("application_ids")).get("application_id");
        }
    }

    @JsonProperty("uplink_message")
    private void unpackUplinkMessageMetadata(final Map<String, Object> metadata) {
        if (metadata.containsKey("f_port")) {
            port = (int) metadata.get("f_port");
        }

        if (metadata.containsKey("frm_payload")) {
            payloadBase64Encoded = (String) metadata.get("frm_payload");
        }
    }
}
