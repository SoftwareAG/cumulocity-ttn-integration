# Cumulocity - The Things Network Integration

## Summary

This Cumulocity Microservice enables your [Cumulocity IoT](https://www.softwareag.cloud/site/product/cumulocity-iot.html#/) tenant to integrate with the LoRa network provider [The Things Network (TTN)](https://www.thethingsnetwork.org ). This integration allows receiving uplink messages from TTN and processing them within Cumulocity IoT, e.g. extract Measurements or Events from the message. Based on the data received from TTN you can use the visualization and Streaming Analytics capabilities of Cumulocity for further processing.

The TTN-Integration Microservice is compatible with the latest version of **The Things Stack V3**.

The TTN-Integration Microservice can be downloaded from the [release section](https://github.com/SoftwareAG/cumulocity-ttn-integration/releases/download/v1.0.0/devicemanagement.zip) and uploaded in Cumulocity IoT as a Microservice application.

## Some more details

There are some prerequisites to run this solution properly:

* To upload a custom Microservice to your tenant you need to have the `feature-microservice-hosting` feature subscribed
* LoRa Device Protocols need to be enabled for the tenant
  * Option 1: Subscribe either the `actility` feature ot the `loriot-agent` feature
  * Option 2: Download custom DeviceManagement application from [release section](https://github.com/SoftwareAG/cumulocity-ttn-integration/releases/download/v1.0.0/devicemanagement.zip) and upload it as Web Application to override the existing DeviceManagement application. ONce the TTN Microservice has been uploaded the LoRa Device Protocols feature will be activated.

The Microservice exposes a single REST endpoint. This endpoint will be used in TTN for the Webhooks integration to forward data received from devices in TTN to Cumulocity IoT. 

- `POST /` 
    
    accepts a message coming from TTN using the following format of The Things Stack V3.
    
    ```json
    {
      "end_device_ids": {
        "device_id": "device-id",
        "application_ids": {
          "application_id": "application-id"
        },
        "dev_eui": "11111111111",
        "join_eui": "0000000000000001"
      },
      "correlation_ids": [
        "as:up:01F9TPEM71KNPH12323WGM44",
        "rpc:/ttn.lorawan.v3.AppAs/SimulateUplink:1234567890-412e-1234-8316-83c3b6e993f9"
      ],
      "received_at": "2021-07-05T06:42:23.074340851Z",
      "uplink_message": {
        "f_port": 1,
        "frm_payload": "BAsBAK0DCwYgBgIBQcjMzQNRASQ=",
        "rx_metadata": [
          {
            "gateway_ids": {
              "gateway_id": "test"
            },
            "rssi": 42,
            "channel_rssi": 42,
            "snr": 4.2
          }
        ],
        "settings": {
          "data_rate": {
            "lora": {
              "bandwidth": 125000,
              "spreading_factor": 7
            }
          }
        },
        "received_at": "0001-01-01T00:00:00Z"
      },
      "simulated": true
    }
    ```

For now the fields `device_id`, `application_id`, `dev_eui`, `received_at`, `f_port` and `frm_payload` are processed by the Microservice.

A device will automatically be created in Cumulocity if a message has been received for a LoRa sensor, which hasn't been registered as a device in Cumulocity yet. For mapping the messages to the domain model of Cumulocity, the Microservice uses the [Device Protocol](https://cumulocity.com/guides/protocol-integration/lora-loriot/#create-loriot-device-protocols) feature of Cumulocity IoT. Therefore, if a device has been newly registered in Cumulocity via the TTN integration Microservice the corresponding Device Protocol needs to be assigned to the respective device first. This needs to be done in the Device Management application of Cumulocity on the related device detail page, e.g:

![Assign a device protocol for a device](https://labcase.softwareag.com/storage/d/ebe7a553c15171d8b4e4f230f897a1df "Assign Device Protocol")  

The Microservice will pick up the related Device Protocol for the device. When a new message has been received from TTN for the device, the Microservice will use the Device Protocol to translate the message into the respective domain objects of Cumulocity.

## How to run locally

The Microservice is based on the Cumulocity Java Microservice SDK. For an introduction and more information on the Java SDK have a look at the [documentation](https://cumulocity.com/guides/microservice-sdk/java/#java-microservice). The documentation also describes the necessary steps to run a Microservice locally.

1. Create a new Microservice application on your Cumulocity IoT tenant

    `POST https://{base-url}/application/applications`
    
    Request body:
    
    ```json
    {
        "key": "ttn-integration-key",
        "name": "ttn-integration",
        "contextPath": "ttn-integration",
        "type": "MICROSERVICE",
        "manifest":{},	
        "requiredRoles": [
            "ROLE_INVENTORY_READ",
            "ROLE_INVENTORY_CREATE",
            "ROLE_INVENTORY_ADMIN",
            "ROLE_MEASUREMENT_READ"
        ],
        "roles": []
    }
    ```
    
    Make sure to provide the correct authorization for the request.

2. Subscribe the Microservice application for your tenant via UI

3. Acquire microservice bootstrap credentials

    `GET https://{base-url}/application/applications/{applicationId}/bootstrapUser`
    
    Response body:
    
    ```json
    {
        "password": "************************",
        "name": "servicebootstrap_ttn-integration",
        "tenant": "<your tenant>"
    }
    ```
    Make sure to provide the correct authorization for the request.

4. Provide bootstrap credentials 

    Add the bootstrap credentials to your `src/main/resources/application.properties` and uncomment all C8Y properties.
	
	```text
	C8Y.baseURL=
	C8Y.bootstrap.user=
	C8Y.bootstrap.password=
	C8Y.bootstrap.tenant=
	```

5. Run Microservice

    Run the Microservice inside your IDE as a Spring Boot application.

6. Access Microservice locally

    The Microservice can be accessed on `http://localhost:8080/` by sending a POST request, which contains the corresponding payload as described in the previous section. Make sure to set the correct authorization when running it locally, username needs to be `tenantId/username`. 

## How to deploy to Cumulocity

The Microservice comes with the `microservice-package-maven-plugin`, which enables building the Microservice using Maven, including a Docker image in case Docker is available on the build system. To trigger the build run `mvn clean install` for the project. To enable the build of the Docker Image set the property `<c8y.docker.skip>false</c8y.docker.skip>` to `true` in the `pom.xml` within the `./ttn-integration/c8y-ttn-integration` directory.      

If the build has been successful the build artifacts can be found in the `target` directory. If the Docker build has been enabled the `target` directory also includes a zip archive. The zip archive consists of the Docker Image and the Cumulocity Microservice manifest. Upload the zip archive as Microservice application in the Administration application of Cumulocity to deploy the Microservice within Cumulocity. 

For more information about deploying a Microservice check the [documentation](https://cumulocity.com/guides/microservice-sdk/java/#developing-microservice).  

Check the release section for the latest build of this Microservice.

## Disclaimer

These tools are provided as-is and without warranty or support. They do not constitute part of the Software AG product suite. Users are free to use, fork and modify them, subject to the license agreement. While Software AG welcomes contributions, we cannot guarantee to include every contribution in the master project.

## Contact

For more information you can Ask a Question in the [TechCommunity Forums](http://tech.forums.softwareag.com/techjforum/forums/list.page?product=cumulocity).

You can find additional information in the [Software AG TechCommunity](http://techcommunity.softwareag.com/home/-/product/name/cumulocity).

_________________
Contact us at [TechCommunity](mailto:technologycommunity@softwareag.com?subject=Github/SoftwareAG) if you have any questions.
