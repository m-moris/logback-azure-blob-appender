# Azure Blob Appender for logback

## 概要

Azure Blob Appender は、[logback](http://logback.qos.ch/) 用の Azure Blob Storage へのログ出力を行う Appender です。
利用は自己責任でお願いします。

## 設定

### 接続文字列による接続

| 属性             | 型     | 意味                            |
| ---------------- | ------ | ------------------------------- |
| connectionString | string | Azure Blob Storage の接続文字列 |
| containerName    | string | BLOBコンテナの名前              |

### マネージドIDによる接続

| 属性         | 型     | 意味              |
| ------------ | ------ | ----------------- |
| containerUri | string | BLOBコンテナのURI |

内部的に `DefaultAzureCredential` を利用して認証を行います。

[DefaultAzureCredential Class | Microsoft Learn](https://learn.microsoft.com/en-us/java/api/com.azure.identity.defaultazurecredential?view=azure-java-stable)


## 設定サンプル 

以下は、接続文字列による接続の例です。

```xml
<configuration>
    <appender name="BLOB" class="io.github.m_moris.azure.logback.AzureBlobAppender">
        <connectionString>${connectionString}</connectionString>
        <containerName>${containerName}</containerName>
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>


    <!-- com.azure, netty は 出力しない方がよい -->
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


## 検討事項

### Azure SDK for Java との関係

Azure SDK for Java が logback によるロギングをサポートしてるため、logback で Azure SDK for Java によるBLOB出力をすると、依存関係が発生しています。この件について、どのように対応すればよいのかは検討が必要。


[Log with the Azure SDK for Java and Logback - Java on Azure | Microsoft Learn](https://learn.microsoft.com/en-us/azure/developer/java/sdk/logging-logback)

今のところ、`logback.xml` の設定で、`com.azure` と `io.netty` に対して、`BLOB` に出力しないようにしている。

### Append Blob について

Append Blob の最大追加回数は、50000回です。これを超えると、新しいBLOBを作成する必要があります。この Appender では1時間毎に新しいBLOBを作成するようにしています。これは変更できません。

1時間以内に50000回以上のログ出力がある場合のために、ある程度まとめて出力する検討が必要です。

[Append Block (REST API) - Azure Storage | Microsoft Learn](https://learn.microsoft.com/en-us/rest/api/storageservices/append-block?tabs=microsoft-entra-id#remarks)