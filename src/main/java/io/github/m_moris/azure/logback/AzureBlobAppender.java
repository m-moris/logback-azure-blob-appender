package io.github.m_moris.azure.logback;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.azure.core.credential.TokenCredential;
import com.azure.core.http.HttpClient;
import com.azure.core.http.netty.NettyAsyncHttpClientBuilder;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.HttpLogOptions;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.specialized.AppendBlobClient;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.net.QueueFactory;

public class AzureBlobAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    public static final int DEFAULT_QUEUE_SIZE = 128;

    private static final int DEFAULT_ACCEPT_CONNECTION_DELAY = 5000;

    private static final Duration BLOB_CONNEDTION_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration BLOB_READ_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration BLOB_WRITE_TIMEOUT = Duration.ofSeconds(30);

    private final QueueFactory queueFactory;
    private Encoder<ILoggingEvent> encoder;
    private Future<?> task;
    private BlockingDeque<ILoggingEvent> deque;
    private Duration delay;
    private int queueSize = 10000;

    private String containerUri = "";
    private String connectionString = "";
    private String containerName = "logback";
    private String prefix1 = "logback";
    private String prefix2 = "";

    private BlobContainerClient container;

    public AzureBlobAppender() {
        this.queueFactory = new QueueFactory();
    }

    /**
     * Start the appender
     */
    @Override
    public void start() {

        int errorCount = 0;
        if (isStarted()) {
            return;
        }

        HttpLogOptions httpLogOptions = new HttpLogOptions()
            .setLogLevel(HttpLogDetailLevel.NONE)
            .setRequestLogger(null)
            .setAllowedHeaderNames(null)
            .setAllowedQueryParamNames(null)
            .setRequestLogger(null)
            .disableRedactedHeaderLogging(true);

        if (encoder == null) {
            addError("No encoder set for the appender named [" + name + "].");
            errorCount++;
        }

        if (queueSize < 0) {
            queueSize = DEFAULT_QUEUE_SIZE;
            addWarn("Queue size must be greater than zero");
        }

        if (!containerUri.isEmpty()) {
            TokenCredential credential = new DefaultAzureCredentialBuilder().build();
            container = new BlobContainerClientBuilder()
                .credential(credential)
                .endpoint(containerUri)
                .httpClient(buildHttpClient())
                .buildClient();

        } else if (!connectionString.isEmpty()) {
            container = new BlobContainerClientBuilder()
                .connectionString(connectionString)
                .containerName(containerName)
                .httpLogOptions(httpLogOptions)
                .httpClient(buildHttpClient())
                .buildClient();

        } else {
            addError("Connection string and container name, or container URI is required.");
            errorCount++;
        }

        if (errorCount > 0) {
            return;
        }

        // TODO configurable
        delay = Duration.ofMillis(DEFAULT_ACCEPT_CONNECTION_DELAY);

        container.createIfNotExists();
        deque = queueFactory.newLinkedBlockingDeque(getQueueSize());
        task = getContext().getExecutorService().submit(new Runnable() {
            @Override
            public void run() {
                dispatchEvents();
            }
        });

        super.start();
    }

    /**
     * Build the HttpClient for the appender
     * 
     * @implNote
     * There is room for consideration
     * 
     * @return HttpClient
     */
    private HttpClient buildHttpClient() {
        return new NettyAsyncHttpClientBuilder()
            .connectTimeout(BLOB_CONNEDTION_TIMEOUT)
            .readTimeout(BLOB_READ_TIMEOUT)
            .writeTimeout(BLOB_WRITE_TIMEOUT)
            .build();
    }

    private void dispatchEvents() {

        String prevBlobName = "";
        while (true) {
            try {
                var event = deque.takeFirst();
                String blobName = getBlobName();
                AppendBlobClient client = container.getBlobClient(blobName).getAppendBlobClient();

                // If the blob name is different from the previous one, create a new blob
                if (!prevBlobName.equals(blobName)) {
                    client.createIfNotExists();
                }

                prevBlobName = blobName;
                appendToBlob(client, event);
            } catch (InterruptedException | IOException e) {
                addError("Failed to append log to Azure Blob Storage: " + e.getMessage());
            }
        }

    }

    /**
     * Append the event to the blob
     * @param append AppendBlobClient
     * @param event ILoggingEvent
     * @throws IOException 
     */
    private void appendToBlob(AppendBlobClient append, ILoggingEvent event) throws IOException {
        byte[] bin = encoder.encode(event);
        try (InputStream is = new ByteArrayInputStream(bin)) {
            append.appendBlock(is, bin.length);
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!isStarted()) {
            return;
        }
        try {
            final boolean inserted = deque.offer(event, delay.toMillis(), TimeUnit.MILLISECONDS);
            if (!inserted) {
                addInfo("Dropping event due to timeout");
            }
        } catch (InterruptedException e) {
            addError("Interrupted while appending event to SocketAppender", e);
        }
    }

    @Override
    public void stop() {
        if (!isStarted()) {
            return;
        }
        task.cancel(true);
        super.stop();
    }

    private String getBlobName() {

        String dfmt = (new SimpleDateFormat("yyyy/MM/dd/HH")).format(new Date());
        if (prefix2.isEmpty()) {
            return String.format("%s/%s_applicationLog.txt", dfmt, prefix1);
        }
        return String.format("%s/%s/%s_applicationLog.txt", prefix1, dfmt, prefix2);
    }

    /**
     * Set the URI of container. If this value is set, authentication is performed using the DefaultAzureCredential.
     * This value takes precedence over the connection string.
     * 
     * @param uri
     */
    public void setContainerUri(String uri) {
        this.containerUri = uri;
    }

    /**
     * Set the connection string to authenticate with the Azure Storage
     * @param connectionString
     */
    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    /**
     * Set the name of the container
     * @param containerName
     */
    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public void setPrefix1(String prefix1) {
        this.prefix1 = prefix1;
    }

    public void setPrefix2(String prefix2) {
        this.prefix2 = prefix2;
    }

    public void setQueueSize() {
        this.queueSize = queueSize;
    }

    public int getQueueSize() {
        return queueSize;
    }

    /**
     * Set the encoder for the appender
     * @param encoder
     */
    public void setEncoder(Encoder<ILoggingEvent> encoder) {
        this.encoder = encoder;
    }

    /**
     * Set the encoder for the appender
     * @return
     */
    public Encoder<ILoggingEvent> getEncoder() {
        return encoder;
    }
}
