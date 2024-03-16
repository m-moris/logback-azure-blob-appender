# Azure Blob Appender for logback

## Overview

Azure Blob Appender is an Appender for logback that outputs logs to Azure Blob Storage.
This is sample implemeation now. This Use at your own risk.

## Configuration

### Connection by connection string

| Attribute        | Type   | Meaning                                 |
| ---------------- | ------ | --------------------------------------- |
| connectionString | string | Connection string of Azure Blob Storage |
| containerName    | string | Name of the BLOB container              |

### Connection by managed identity

| Attribute    | Type   | Meaning                   |
| ------------ | ------ | ------------------------- |
| containerUri | string | URI of the BLOB container |

Authentication is performed internally using `DefaultAzureCredential`.

[DefaultAzureCredential Class | Microsoft Learn](https://learn.microsoft.com/en-us/java/api/com.azure.identity.defaultazurecredential?view=azure-java-stable)

## Configuration sample

The following is an example of connection by connection string.

```xml
<configuration>
    <appender name="BLOB" class="io.github.m_moris.azure.logback.AzureBlobAppender">
        <connectionString>${connectionString}</connectionString>
        <containerName>${containerName}</containerName>
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>


    <!-- exclude com.azure, netty from BLOB appender -->
    <logger name="com.azure" level="DEBUG" additivity="false">
        <appender-ref ref="STDOUT" />
    </logger>
    <logger name="io.netty" level="DEBUG" additivity="false">
        <appender-ref ref="STDOUT" />
    </logger>
    <logger name="reactor.netty" level="DEBUG" additivity="false">
        <appender-ref ref="STDOUT" />
    </logger>

    <root level="DEBUG">
        <appender-ref ref="STDOUT" />
        <appender-ref ref="BLOB" />
    </root>
</configuration>
```

## Considerations

### Azure SDK for Java

Since Azure SDK for Java supports logging by logback, BLOB output by Azure SDK for Java with logback is causing dependencies. How to deal with this issue needs to be considered.

[Log with the Azure SDK for Java and Logback - Java on Azure | Microsoft Learn](https://learn.microsoft.com/en-us/azure/developer/java/sdk/ logging-logback)

Currently, I have `logback.xml` configured not to output to `BLOB` for `com.azure` and `io.netty`.

### About Append Blob

The maximum number of times an Append Blob can be added is 50000 times. If this number is exceeded, a new BLOB must be created. This Appender is designed to create a new BLOB every hour. This cannot be changed.

Consideration should be given to outputting the logs together to some extent in case there are more than 50000 log outputs within an hour.

[Append Block (REST API) - Azure Storage | Microsoft Learn](https://learn.microsoft.com/en-us/rest/api/storageservices/append-block?tabs=microsoft-entra-id#remarks)

