package com.amazonaws.kvstranscribestreaming;

import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.transcribestreaming.model.Result;
import software.amazon.awssdk.services.transcribestreaming.model.TranscriptEvent;

import java.text.NumberFormat;
import java.time.Instant;
import java.util.List;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import java.nio.charset.StandardCharsets;

/**
 * TranscribedSegmentWriter writes the transcript segments to DynamoDB
 *
 * <p>Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.</p>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
public class TranscribedSegmentWriter {

    private String contactId;
    private String customerPhoneNumber;
    private DynamoDB ddbClient;
    private Boolean consoleLogTranscriptFlag;
    private static final boolean SAVE_PARTIAL_TRANSCRIPTS = Boolean.parseBoolean(System.getenv("SAVE_PARTIAL_TRANSCRIPTS"));
    private static final Logger logger = LoggerFactory.getLogger(TranscribedSegmentWriter.class);
    private AWSLambda lambdaClient;
    private String notification_lambda;

    public TranscribedSegmentWriter(String contactId, String customerPhoneNumber, DynamoDB ddbClient, Boolean consoleLogTranscriptFlag, AWSLambda lambdaClient) {

        this.contactId = Validate.notNull(contactId);
        this.customerPhoneNumber = Validate.notNull(customerPhoneNumber);
        this.ddbClient = Validate.notNull(ddbClient);
        this.consoleLogTranscriptFlag = Validate.notNull(consoleLogTranscriptFlag);
        this.lambdaClient = Validate.notNull(lambdaClient);
        this.notification_lambda = System.getenv("WS_SEND_MESSAGE_LAMBDA");
        if(this.notification_lambda == null)
            this.notification_lambda = "ws_send_message";
    }

    public String getContactId() {

        return this.contactId;
    }

    public DynamoDB getDdbClient() {

        return this.ddbClient;
    }

    public void writeToDynamoDB(TranscriptEvent transcriptEvent, String tableName) {
        logger.info("table name: " + tableName);
        logger.info("Transcription event: " + transcriptEvent.transcript().toString());
        List<Result> results = transcriptEvent.transcript().results();
        if (results.size() > 0) {

            Result result = results.get(0);

            if (SAVE_PARTIAL_TRANSCRIPTS || !result.isPartial()) {
                try {
                    Item ddbItem = toDynamoDbItem(result);
                    if (ddbItem != null) {
                        getDdbClient().getTable(tableName).putItem(ddbItem);
                    }

                } catch (Exception e) {
                    logger.error("Exception while writing to DDB: ", e);
                }

                try {
                    if(!result.isPartial()) {
                        InvokeRequest invokeRequest = new InvokeRequest()
                                .withFunctionName(this.notification_lambda)  // Replace with your Lambda function name
                                .withInvocationType("Event")  // Async invocation
                                .withPayload(String.format("{\"connectContactId\": \"%s\", \"customerPhoneNumber\": \"%s\", \"text\": \"%s\"}", this.contactId, this.customerPhoneNumber, result.alternatives().get(0).transcript()));

                        // Invoke the Lambda function asynchronously
                        InvokeResult invokeResult = this.lambdaClient.invoke(invokeRequest);

                        // Check the result status
                        int statusCode = invokeResult.getStatusCode();
                        String response = new String(invokeResult.getPayload().array(), StandardCharsets.UTF_8);

                        System.out.println("Lambda invocation completed with status code: " + statusCode);
                        System.out.println("Response payload: " + response);  // Async invocation has no payload response
                    }
                } catch (Exception e) {
                    logger.error("Exception while invoking lambda asynchronously: ", e);
                }
            }
        }
    }

    private Item toDynamoDbItem(Result result) {

        String contactId = this.getContactId();
        Item ddbItem = null;

        NumberFormat nf = NumberFormat.getInstance();
        nf.setMinimumFractionDigits(3);
        nf.setMaximumFractionDigits(3);

        if (result.alternatives().size() > 0) {
            if (!result.alternatives().get(0).transcript().isEmpty()) {

                Instant now = Instant.now();
                ddbItem = new Item()
                        .withKeyComponent("ContactId", contactId)
                        .withKeyComponent("StartTime", result.startTime())
                        .withString("SegmentId", result.resultId())
                        .withDouble("EndTime", result.endTime())
                        .withString("Transcript", result.alternatives().get(0).transcript())
                        .withBoolean("IsPartial", result.isPartial())
                        // LoggedOn is an ISO-8601 string representation of when the entry was created
                        .withString("LoggedOn", now.toString())
                        // expire after a week by default
                        .withDouble("ExpiresAfter", now.plusSeconds(7 * 24 * 3600).toEpochMilli() / 1000);

                if (consoleLogTranscriptFlag) {
                    logger.info(String.format("Thread %s %d: [%s, %s] - %s",
                            Thread.currentThread().getName(),
                            System.currentTimeMillis(),
                            nf.format(result.startTime()),
                            nf.format(result.endTime()),
                            result.alternatives().get(0).transcript()));
                }
            }
        }

        return ddbItem;
    }
}
