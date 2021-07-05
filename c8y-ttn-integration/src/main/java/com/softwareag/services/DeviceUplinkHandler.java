package com.softwareag.services;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import c8y.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.cumulocity.lpwan.devicetype.model.DeviceType;
import com.cumulocity.lpwan.devicetype.service.DeviceTypePayloadConfigurer;
import com.cumulocity.lpwan.payload.exception.DeviceTypeObjectNotFoundException;
import com.cumulocity.lpwan.payload.service.PayloadDecoderService;
import com.cumulocity.microservice.context.ContextService;
import com.cumulocity.microservice.context.credentials.MicroserviceCredentials;
import com.cumulocity.model.ID;
import com.cumulocity.model.idtype.GId;
import com.cumulocity.rest.representation.alarm.AlarmRepresentation;
import com.cumulocity.rest.representation.event.EventRepresentation;
import com.cumulocity.rest.representation.identity.ExternalIDRepresentation;
import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.cumulocity.rest.representation.inventory.ManagedObjects;
import com.cumulocity.sdk.client.SDKException;
import com.cumulocity.sdk.client.alarm.AlarmApi;
import com.cumulocity.sdk.client.event.EventApi;
import com.cumulocity.sdk.client.identity.IdentityApi;
import com.cumulocity.sdk.client.inventory.InventoryApi;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.io.BaseEncoding;
import com.softwareag.model.TTNUplinkMessage;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Service
public class DeviceUplinkHandler {

    private final static String EXTERNAL_ID_TYPE = "c8y_TTN_EUI";

    private final static String DEVICE_TYPE = "c8y_Lora";

    private final static String EVENT_TYPE = "c8y_TTNUplinkRequest";

    private final static String EVENT_TEXT = "New uplink message received";

    private final static String ALARM_TYPE_DEVICE_REGISTRATION = "c8y_NewDeviceRegistered";

    private final static String ALARM_TEXT_DEVICE_REGISTRATION = "Device has been newly registered to this tenant. Assign message mapping to the device.";

    private final static LpwanDevice LPWAN_DEVICE_FRAGMENT = initLpwanDeviceFragment();

    private IdentityApi identityApi;

    private InventoryApi inventoryApi;

    private EventApi eventApi;

    private AlarmApi alarmApi;

    private ContextService<MicroserviceCredentials> contextService;

    private DeviceTypePayloadConfigurer payloadConfigurer;

    private PayloadDecoderService<TTNUplinkMessage> payloadDecoderService;

    private final LoadingCache<ImmutablePair<String, String>, ManagedObjectRepresentation> deviceCache = CacheBuilder
            .newBuilder().maximumSize(10000).expireAfterWrite(10, TimeUnit.MINUTES).build(new CacheLoader<>() {
                @Override
                public ManagedObjectRepresentation load(ImmutablePair<String, String> tenantAndSerial) {
                    ID externalID = new ID();
                    externalID.setType(EXTERNAL_ID_TYPE);
                    externalID.setValue(tenantAndSerial.getRight());
                    try {
                        return inventoryApi.get(identityApi.getExternalId(externalID).getManagedObject().getId());
                    } catch (SDKException e) {
                        if (e.getHttpStatus() == 404) {
                            log.error("device doesn't exist");
                            return createTTNDevice(tenantAndSerial.getRight());
                        } else {
                            throw e;
                        }
                    }
                }
            });

    @Autowired
    public DeviceUplinkHandler(IdentityApi identityApi, InventoryApi inventoryApi, EventApi eventApi, AlarmApi alarmApi,
                               ContextService<MicroserviceCredentials> contextService, DeviceTypePayloadConfigurer payloadConfigurer,
                               PayloadDecoderService<TTNUplinkMessage> payloadDecoderService) {
        this.identityApi = identityApi;
        this.inventoryApi = inventoryApi;
        this.eventApi = eventApi;
        this.alarmApi = alarmApi;
        this.contextService = contextService;
        this.payloadConfigurer = payloadConfigurer;
        this.payloadDecoderService = payloadDecoderService;
    }

    public void handle(TTNUplinkRequest request) throws DeviceTypeObjectNotFoundException, ExecutionException {
        ManagedObjectRepresentation device = deviceCache
                .get(new ImmutablePair<>(contextService.getContext().getTenant(), request.getDevEui()));
        try {
            handleDefaultData(device.getId(), request);
            handlePayloadConfigData(request, device);
        } catch (SDKException e) {
            if (e.getHttpStatus() == 422 && e.getMessage().equals("Source object does not exist in inventory.")) {
                log.warn("Device [{}] from tenant [{}] was deleted. Invalidating cache.", device.getId().getValue(),
                        contextService.getContext().getTenant());
                deviceCache
                        .invalidate(new ImmutablePair<>(contextService.getContext().getTenant(), request.getDevEui()));
            } else {
                throw e;
            }
        }
    }

    private void handleDefaultData(GId id, TTNUplinkRequest request) {
        createEvent(id, request);
    }

    private void handlePayloadConfigData(TTNUplinkRequest request, ManagedObjectRepresentation device)
            throws DeviceTypeObjectNotFoundException {
        DeviceType deviceType = payloadConfigurer.getDeviceTypeObject(contextService.getContext().getTenant(), device);

        final String payloadAsHex = BaseEncoding.base16().encode(Base64.decodeBase64(request.getPayloadBase64Encoded()));
        log.info("decoded payload: {}", payloadAsHex);

        payloadDecoderService.decodeAndMap(
                new TTNUplinkMessage(payloadAsHex, request.getDevEui(),
                        new DateTime(request.getTimestamp(), DateTimeZone.UTC), request.getPort()),
                ManagedObjects.asManagedObject(device.getId()), deviceType);
    }

    private ManagedObjectRepresentation createTTNDevice(String eui) {
        ManagedObjectRepresentation mo = new ManagedObjectRepresentation();
        mo.setName("TTN_Device_" + eui);
        mo.setType(DEVICE_TYPE);
        mo.set(new IsDevice());
        mo.set(new Lora());
        mo.set(LPWAN_DEVICE_FRAGMENT);
        mo = inventoryApi.create(mo);
        createIdentityBinding(mo.getId(), eui);
        createAlarmForDeviceRegistration(mo.getId());

        return mo;
    }

    private ExternalIDRepresentation createIdentityBinding(GId id, String eui) {
        ExternalIDRepresentation externalID = new ExternalIDRepresentation();
        externalID.setType(EXTERNAL_ID_TYPE);
        externalID.setExternalId(eui);
        externalID.setManagedObject(ManagedObjects.asManagedObject(id));
        return identityApi.create(externalID);
    }

    private AlarmRepresentation createAlarmForDeviceRegistration(GId id) {
        final AlarmRepresentation alarm = new AlarmRepresentation();
        alarm.setSource(ManagedObjects.asManagedObject(id));
        alarm.setDateTime(new DateTime());
        alarm.setType(ALARM_TYPE_DEVICE_REGISTRATION);
        alarm.setText(ALARM_TEXT_DEVICE_REGISTRATION);
        alarm.setSeverity("CRITICAL");
        alarm.setStatus("ACTIVE");
        return alarmApi.create(alarm);
    }

    private void createEvent(GId id, TTNUplinkRequest data) {
        EventRepresentation event = new EventRepresentation();
        event.setType(EVENT_TYPE);
        event.setDateTime(new DateTime());
        event.setSource(ManagedObjects.asManagedObject(id));
        event.setText(EVENT_TEXT);
        event.set(data);
        eventApi.create(event);
    }

    private static LpwanDevice initLpwanDeviceFragment() {
        final LpwanDevice lpwanFragment = new LpwanDevice();
        lpwanFragment.setLpwanDeviceType("Lora");

        return lpwanFragment;
    }
}
