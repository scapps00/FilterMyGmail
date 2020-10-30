package com.FilterMyGmail;

import static com.FilterMyGmail.Credentials.getCredentials;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DoFilter {

    private static final String APPLICATION_NAME = "FilterMyGmail";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private static void listEmails(Gmail service) throws IOException {
        List<Message> message = service.users().messages().list("me").set("maxResults", 1L).execute().getMessages();

        String id = message.get(0).getId();

        MessagePart payload = service.users().messages().get("me", id).execute().getPayload();

        List<MessagePartHeader> headers = payload.getHeaders();

        Map<String, String> data = new HashMap<>();

        for (MessagePartHeader thing : headers) {
            if (thing.getName().equals("From")) {
                data.put("from", thing.getValue());
            } else if (thing.getName().equals("Subject")) {
                data.put("subject", thing.getValue());
            } else if (thing.getName().equals("Date")) {
                data.put("date", thing.getValue());
            }
        }

        System.out.println(data.get("from"));
        System.out.println(data.get("subject"));
        System.out.println(data.get("date"));

        String input = System.console().readLine();

        switch (input) {
            case "f" :
                createFilter(data.get("from"), service);
                deletePreviousEmails(data.get("from"), service);
                break;
            case "k" :
                createFilter(data.get("from"), service);
                break;
            default :
                break;
        }
    }

    private static void createLabel(Gmail service) throws IOException {
        Label label = new Label();

        label.setName("ApprovedEmails");
        label.setColor(new LabelColor().setBackgroundColor("#ffe6c7").setTextColor("#000000"));
        label.setLabelListVisibility("labelShow");
        label.setMessageListVisibility("show");

        String labelId = service.users().labels().create("me", label).execute().getId();

        saveToFile(labelId);

        System.out.println("Label created and ID saved!");
    }

    private static void saveToFile(String labelId) throws IOException {
        File labelIdFile = new File("labelId.txt");
        labelIdFile.createNewFile();
        FileWriter writeLabelId =  new FileWriter("labelId.txt", false);
        writeLabelId.write(labelId);
        writeLabelId.close();
    }

    private static void createFilter(String email, Gmail service) throws IOException {
        String labelId = readLabelId().toString();

        System.out.println(labelId);

        String emailSplit = parseEmail(email);

        FilterCriteria criteria = new FilterCriteria()
                .setFrom(emailSplit);

        FilterAction action = new FilterAction()
                .setAddLabelIds(Collections.singletonList(labelId))
                .setRemoveLabelIds(Collections.singletonList("INBOX"));

        Filter filter = new Filter()
                .setCriteria(criteria)
                .setAction(action);

        Filter result = service.users().settings().filters().create("me", filter).execute();
        System.out.println("Created filter " + result.getId());

    }

    private static StringBuilder readLabelId() throws IOException {
        FileReader reader = new FileReader("labelId.txt");

        char[] chars = new char[40];

        int labelLength = reader.read(chars);

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < labelLength; i++) {
            builder.append(chars[i]);
        }

        return builder;
    }

    private static void deletePreviousEmails(String email, Gmail service) throws IOException {
        String emailSplit = parseEmail(email);
        String query = "from:"
                + emailSplit;

        ListMessagesResponse response = service.users().messages().list("me").setQ(query).execute();

        List<Message> messages = new ArrayList<Message>();
        while (response.getMessages() != null) {
            messages.addAll(response.getMessages());
            if (response.getNextPageToken() != null) {
                String pageToken = response.getNextPageToken();
                response = service.users().messages().list("me").setQ(query)
                        .setPageToken(pageToken).execute();
            } else {
                break;
            }
        }

        for (Message message : messages) {
            service.users().threads().delete("me", message.getThreadId()).execute();
        }

        System.out.println("All messages deleted from " + emailSplit);
    }

    private static String parseEmail(String email) {
        String[] emailSplitLeft = email.split("<");

        String[] emailSplitRight = emailSplitLeft[emailSplitLeft.length - 1].split(">");

        return emailSplitRight[0];
    }

    public static void main(String[] args) throws GeneralSecurityException, IOException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

        Gmail service = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();

        switch ((args.length > 0) ? args[0] : "")
        {
            case "init" :
                createLabel(service);
                break;

            case "list" :
                listEmails(service);
                break;

            default :
                System.out.println("Unrecognized command for FilterMyGmail");
                break;
        }

    }

}