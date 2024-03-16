package io.github.m_moris.azure.logback;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.ObjectMapper;

public class AzureConfig {

    public AzureConfig() {
    }

    public String connectionString;
    public String containerName;
    public String containerUri;

    @Override
    public String toString() {
        return "AzureConfig [connectionString=" + connectionString + ", containerName=" + containerName
            + ", containerUri=" + containerUri + "]";
    }

    public static AzureConfig ReadAndSetDefault() throws IOException {
        AzureConfig config = null;
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = ClassLoader.getSystemResourceAsStream("azureconfig.json")) {
            config = mapper.readValue(is, AzureConfig.class);
            System.setProperty("connectionString", config.connectionString);
            System.setProperty("containerName", config.containerName);
            System.setProperty("containerUri", config.containerUri);
        }
        return config;
    }
}
