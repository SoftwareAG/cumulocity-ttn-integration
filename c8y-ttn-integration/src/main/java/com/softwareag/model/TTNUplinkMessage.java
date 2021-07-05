package com.softwareag.model;

import org.joda.time.DateTime;

import com.cumulocity.lpwan.payload.uplink.model.UplinkMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class TTNUplinkMessage extends UplinkMessage {
	
	String payloadHex;
	
	String externalId;
	
	DateTime dateTime;
	
	int port;
}
