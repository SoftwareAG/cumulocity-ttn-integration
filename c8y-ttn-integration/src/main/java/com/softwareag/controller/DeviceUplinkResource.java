package com.softwareag.controller;

import java.util.concurrent.ExecutionException;

import c8y.TTNUplinkRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cumulocity.lpwan.payload.exception.DeviceTypeObjectNotFoundException;
import com.cumulocity.sdk.client.SDKException;
import com.softwareag.exceptions.UplinkProcessingException;
import com.softwareag.services.DeviceUplinkHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class DeviceUplinkResource {

    DeviceUplinkHandler handler;

    @Autowired
    public DeviceUplinkResource(DeviceUplinkHandler handler) {
        this.handler = handler;
    }

    @RequestMapping(value = "/", consumes = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    @ResponseStatus(HttpStatus.OK)
    public void uplinkMessage(@RequestBody TTNUplinkRequest request) {
        log.info("message received: {}", request.toString());

        try {
            handler.handle(request);
        } catch (DeviceTypeObjectNotFoundException e) {
            log.warn("Device type error [{}]", e.getMessage());
        } catch (UplinkProcessingException e) {
            log.error("Error processing uplink request", e);
        } catch (SDKException e) {
            log.error("Platform error", e);
        } catch (ExecutionException e) {
            log.error("Error loading external ID from cache", e);
        }
    }
}
