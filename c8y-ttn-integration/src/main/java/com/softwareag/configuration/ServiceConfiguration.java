package com.softwareag.configuration;

import static com.cumulocity.lpwan.payload.service.PayloadDecoderService.messageIdFromPayload;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cumulocity.lpwan.devicetype.service.DeviceTypeMapper;
import com.cumulocity.lpwan.devicetype.service.DeviceTypePayloadConfigurer;
import com.cumulocity.lpwan.payload.service.PayloadDecoderService;
import com.cumulocity.lpwan.payload.service.PayloadMappingService;
import com.cumulocity.lpwan.payload.uplink.model.MessageIdConfiguration;
import com.cumulocity.sdk.client.alarm.AlarmApi;
import com.cumulocity.sdk.client.event.EventApi;
import com.cumulocity.sdk.client.inventory.InventoryApi;
import com.cumulocity.sdk.client.measurement.MeasurementApi;
import com.softwareag.exceptions.UplinkProcessingException;
import com.softwareag.model.TTNUplinkMessage;

@Configuration
public class ServiceConfiguration {
	public static final class TTNMessageIdReader implements PayloadDecoderService.MessageIdReader<TTNUplinkMessage> {
		@Override
		public Integer read(TTNUplinkMessage uplinkMessage, MessageIdConfiguration messageIdConfiguration) {
			String source = messageIdConfiguration.getSource();
			if (source.equals("FPORT")) {
				return uplinkMessage.getPort();
			} else if (source.equals("PAYLOAD")) {
				return messageIdFromPayload(uplinkMessage, messageIdConfiguration);
			} else {
				throw new UplinkProcessingException("Message id configuration is not valid.");
			}
		}
	}

	@Bean
	public PayloadMappingService payloadMappingService(EventApi eventApi, InventoryApi inventoryApi, AlarmApi alarmApi,
			MeasurementApi measurementApi) {
		return new PayloadMappingService(measurementApi, alarmApi, eventApi, inventoryApi);
	}

	@Bean
	public DeviceTypePayloadConfigurer deviceTypePayloadConfigurer(InventoryApi inventoryApi,
			DeviceTypeMapper deviceTypeMapper) {
		return new DeviceTypePayloadConfigurer(inventoryApi, deviceTypeMapper);
	}

	@Bean
	public PayloadDecoderService<TTNUplinkMessage> payloadDecoderService(PayloadMappingService payloadMappingService) {
		return new PayloadDecoderService<>(payloadMappingService, new TTNMessageIdReader());
	}
}
