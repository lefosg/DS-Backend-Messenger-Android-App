package com.ds;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class ClientHandler implements Runnable {

    //Client information
    private String clientUsername;
    private ArrayList<String> clientSubbedTopics;
    private String currentTopic;
    private boolean clientIsConnectedToTopic;
    private boolean secretChat = false;

    //IO objects
    public static ArrayList<ClientHandler> clientHandlers  = new ArrayList<>();
    private Socket socket;
    private ObjectOutputStream writer;
    private ObjectInputStream reader;
    private Broker broker;
    private ArrayList <String> clientBlockedUsers;

    public ClientHandler(Socket socket, Broker broker, ObjectOutputStream oos, ObjectInputStream ois)
    {
        try {
            //IO Objects Init
            this.socket = socket;
            this.writer = oos;
            this.reader = ois;
            this.broker = broker;
            this.clientSubbedTopics = new ArrayList<>();
            this.clientBlockedUsers = new ArrayList<>();
            //Client Information Init
            clientUsername = (String)reader.readObject();  //read username
            clientBlockedUsers = (ArrayList<String>) reader.readObject();
            writer.writeObject(new Value(new MultimediaFile("res/TopicImages/default_topic_image.png", null)));
            writer.writeObject(new Value(new MultimediaFile("res/defaultpfp.png", null)));
            clientHandlers.add(this);
        } catch (Exception e) {
            closeEverything(this.socket,reader,writer);
        }
    }

    @Override
    public void run() {
        while (socket.isConnected()) {
            try {
                //EISAI STO BACKEND
                String header = ((Value) reader.readObject()).getMessage();
                System.out.println(header);
                switch (header) {
                    case "MESSAGE": {
                        Value messageFromClient;
                        currentTopic = ((Value) reader.readObject()).getMessage();  //first read the topic
                        redirectClient(currentTopic);  //check if client is in the right broker to talk to this topic
                        clientIsConnectedToTopic = true;
                        //obtain previous messages and send them to the client
                        ArrayList<Value> previous_messages = broker.getChatData(currentTopic);
                        if (previous_messages.size() != 0) {
                            for (Value value : previous_messages) {
                                if (!clientBlockedUsers.contains(value.getSender())){
                                    writer.writeObject(value);
                                }
                            }
                        }
                        //obtain nickname
                        String clientNickname = broker.getTopic(currentTopic).getClientNickname(clientUsername);
                        if (clientNickname == null || clientNickname.equalsIgnoreCase("")) {
                            clientNickname = clientUsername;
                        }
                        //Value v = new Value(clientNickname + " has entered the chat");
                        //broadcastMessage(v, currentTopic);  //welcome the client
                        while (clientIsConnectedToTopic) {
                            messageFromClient = (Value) reader.readObject();  //message sent
                            messageFromClient.setSender(clientNickname);
                            if (messageFromClient.getMessage() != null) {
                                if (messageFromClient.isCommand()){
                                    //toggle secret chat mode
                                    if (messageFromClient.getMessage().equalsIgnoreCase("secret_chat")) {
                                        secretChat = !secretChat;
                                        toggleSecretChat(secretChat);
                                        continue;
                                    }
                                    //check if client disconnects from topic
                                    else if (messageFromClient.getMessage().equalsIgnoreCase("quit_topic")) {
                                        //Value temp = new Value(clientNickname + " has left the chat");
                                        //broadcastMessage(temp, currentTopic);
                                        clientIsConnectedToTopic = false;
                                        continue;
                                    }
                                    //check if client wants to change topic image
                                    else if (messageFromClient.getMessage().contains("upload_topic_image")) {
                                        MultimediaFile img = ((Value) reader.readObject()).getMultiMediaFile();
                                        //byte[] img_bytes_chunk = new byte[img_chunk.getMultimediaFileChunk().length];
                                        //for (int i = 0; i < img_chunk.getMultimediaFileChunk().length; i++) {
                                        //    img_bytes_chunk[i] = img_chunk.getMultimediaFileChunk()[i];
                                        //}
                                        //while (img_chunk.getMf() == 1) {
                                        //    System.out.println("more");
                                        //    img_chunk = ((Value) reader.readObject()).getMultiMediaFile();
                                        //    byte[] story_bytes_chunk_temp = new byte[img_chunk.getMultimediaFileChunk().length];
                                        //    for (int i = 0; i < img_chunk.getMultimediaFileChunk().length; i++) {
                                        //        story_bytes_chunk_temp[i] = img_chunk.getMultimediaFileChunk()[i];
                                        //    }
                                        //    ByteBuffer bb = ByteBuffer.allocate(img_bytes_chunk.length + story_bytes_chunk_temp.length);
                                        //    bb.put(img_bytes_chunk);
                                        //    bb.put(story_bytes_chunk_temp);
                                        //    img_bytes_chunk = bb.array();
                                        //}
                                        //MultimediaFile img = new MultimediaFile(img_chunk, img_bytes_chunk);
                                        if (img != null && img.getMf() == 0) {
                                            broker.getTopic(currentTopic).topicPicture = img;
                                            System.out.println(broker.getTopic(currentTopic).topicPicture.toString());
                                        }
                                        continue;
                                    }
                                    //set nickname
                                    else if (messageFromClient.getMessage().contains("set_nickname")) {
                                        String nickname = messageFromClient.getMessage().substring("set_nickname".length() + 1);
                                        broker.getTopic(currentTopic).setClientNickname(clientUsername, nickname);
                                        clientNickname = nickname;
                                        continue;
                                    }
                                }
                                //if topic is in secret mode, don't save message
                                if (!secretChat) {
                                    broker.addMessageToTopic(messageFromClient, currentTopic);
                                }
                            }
                            //messageFromClient.setMessage(clientNickname + ": " + messageFromClient.getMessage());
                            broadcastMessage(messageFromClient, currentTopic);

                            //send file
                            //if (messageFromClient.getMultiMediaFile() != null && messageFromClient.getMultiMediaFile().getMf() == 0) {
                            //    Value file_sent_msg = new Value(clientNickname + " sent a file (" + messageFromClient.getMultiMediaFile().getFileName() + ")");
                            //    file_sent_msg.setSender(clientUsername);
                            //    broadcastMessage(file_sent_msg, currentTopic);
                            //    if (!secretChat) {
                            //        broker.addMessageToTopic(file_sent_msg, currentTopic);
                            //    }
                            //}
                            if (messageFromClient.getMultiMediaFile() != null) {
                                Value file_sent_msg = new Value(clientNickname + " sent a file (" + messageFromClient.getMultiMediaFile().getFileName() + ")");
                                file_sent_msg.setSender(clientUsername);
                                broadcastMessage(file_sent_msg, currentTopic);
                                if (!secretChat) {
                                    broker.addMessageToTopic(file_sent_msg, currentTopic);
                                }
                            }
                        }
                        break;
                    }
                    case "CREATE": {
                        String topic_to_create = ((Value) reader.readObject()).getMessage();
                        BrokerAddressInfo responsible = broker.findResponsibleBrokerAddress(topic_to_create);
                        if (!broker.checkRightBroker(responsible, broker.getAddressInfo())) {
                            Integer newBroker = broker.getAddressToSocketMapper().get(responsible.toString());
                            broker.sendToOtherBroker(newBroker, new Value("CREATE"));
                            broker.sendToOtherBroker(newBroker, new Value(topic_to_create + " " + clientUsername));
                        } else {
                            broker.createTopic(topic_to_create, clientUsername);
                        }
                        clientSubbedTopics.add(topic_to_create);
                        break;
                    }
                    case "SUB": {
                        String topic_to_sub = ((Value) reader.readObject()).getMessage();
                        broker.subscribe(topic_to_sub, clientUsername);
                        clientSubbedTopics.add(topic_to_sub);
                        break;
                    }
                    case "UNSUB": {
                        String topic_to_unsub = ((Value) reader.readObject()).getMessage();  //get the topic
                        BrokerAddressInfo responsible = broker.findResponsibleBrokerAddress(topic_to_unsub);  //find in which broker the topic belongs
                        if (!broker.checkRightBroker(responsible, broker.getAddressInfo())) {  //if current broker is not correct, tell the other broker
                            Integer newBroker = broker.getAddressToSocketMapper().get(responsible.toString());
                            broker.sendToOtherBroker(newBroker, new Value("UNSUB"));
                            broker.sendToOtherBroker(newBroker, new Value(topic_to_unsub + " " + clientUsername));
                        } else {  //else unsub the client
                            broker.unsubscribe(topic_to_unsub, clientUsername);
                        }
                        clientSubbedTopics.remove(topic_to_unsub);
                        break;
                    }
                    case "FRIEND_REQUEST": {
                        String name = ((Value) reader.readObject()).getMessage();
                        sendFriendRequest(name);
                        break;
                    }
                    case "FRIEND_REQUEST_RESPONSE": {
                        String response = ((Value) reader.readObject()).getMessage();
                        sendFriendRequestResponse(response);
                        break;
                    }
                    case "GET_TOPICS": {
                        Value v = new Value("RETRIEVE_TOPICS");
                        v.setCommand(true);
                        sendBack(v);
                        writer.writeObject(broker.getTopicNames());  //send topic names
                        writer.flush();
                        writer.writeObject(broker.getTopicImages());  //send topic images
                        writer.flush();

                        break;
                    }
                    case "BLOCK_USER": {
                        String user_to_block = ((Value)reader.readObject()).getMessage();
                        clientBlockedUsers.add(user_to_block);
                        break;
                    }
                    case "UNBLOCK_USER": {
                        String user_to_unblock = ((Value)reader.readObject()).getMessage();
                        clientBlockedUsers.remove(user_to_unblock);
                        break;
                    }
                    case "UPLOAD_STORY": {
                        //MultimediaFile story_chunk = ((Value) reader.readObject()).getMultiMediaFile();
                        //byte[] story_bytes_chunk = new byte[story_chunk.getMultimediaFileChunk().length];
                        //for(int i=0; i < story_chunk.getMultimediaFileChunk().length; i++) {
                        //    story_bytes_chunk[i] = story_chunk.getMultimediaFileChunk()[i];
                        //}
                        //while (story_chunk.getMf() == 1){
                        //    System.out.println("more");
                        //    story_chunk = ((Value) reader.readObject()).getMultiMediaFile();
                        //    byte[] story_bytes_chunk_temp = new byte[story_chunk.getMultimediaFileChunk().length];
                        //    for(int i=0; i < story_chunk.getMultimediaFileChunk().length; i++) {
                        //        story_bytes_chunk_temp[i] = story_chunk.getMultimediaFileChunk()[i];
                        //    }
                        //    ByteBuffer bb = ByteBuffer.allocate(story_bytes_chunk.length+story_bytes_chunk_temp.length);
                        //    bb.put(story_bytes_chunk);
                        //    bb.put(story_bytes_chunk_temp);
                        //    story_bytes_chunk = bb.array();
                        //}
                        MultimediaFile story = ((Value)reader.readObject()).getMultiMediaFile();
                        broker.addStory(story);
                        break;
                    }
                    case "VIEW_STORIES": {
                        ArrayList<MultimediaFile> broker_stories = broker.getStories();
                        Value v =new Value("GET_STORY");
                        v.setCommand(true);
                        sendBack(v);
                        writer.writeObject(broker_stories.size());
                        for (MultimediaFile story : broker_stories) {
                            System.out.println(story);
                            sendBack(new Value(story));
                        }
                        break;
                    }
                    default:
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
                closeEverything(socket, reader, writer);
                break;
            }
        }
    }

    private void redirectClient(String topic) {
        BrokerAddressInfo responsible = broker.findResponsibleBrokerAddress(topic);
        System.out.println(responsible);
        if(!broker.checkRightBroker(responsible,broker.getAddressInfo())){
            Value v = new Value("WRONG_BROKER");
            v.setCommand(true);
            sendBack(v);
            sendBack(new Value(responsible.toString()));
            sendBack(new Value(topic));
        }
    }

    private void sendFriendRequest(String name) {
        try {
            for (ClientHandler clientHandler : clientHandlers) {
                if (clientHandler.clientUsername.equals(name)) {
                    Value v = new Value(clientUsername + " has sent you a friend request");
                    v.setCommand(true);
                    clientHandler.writer.writeObject(v);
                    clientHandler.writer.flush();
                    return;
                }
            }
            writer.writeObject(new Value("\nUser " + name + " was not found"));
            writer.flush();
        } catch (Exception e ) {
            e.printStackTrace();
        }
    }

    private void sendFriendRequestResponse(String response) {
        try {
            String[] info = response.split(" ");
            String other_person = info[1];
            for (ClientHandler clientHandler : clientHandlers) {
                if (clientHandler.clientUsername.equals(other_person)) {
                    Value v = new Value(response.replace(other_person, clientUsername));
                    v.setCommand(true);
                    clientHandler.writer.writeObject(v);
                    clientHandler.writer.flush();
                    return;
                }
            }
        } catch (Exception e ) {
            e.printStackTrace();
        }
    }

    public void sendBack(Value v) {
        try {
            writer.writeObject(v);
            writer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void broadcastMessage(Value message, String topic){
        for (ClientHandler clientHandler : clientHandlers){
            try{
               if (clientHandler.clientIsConnectedToTopic && clientHandler.currentTopic.equals(currentTopic)
                   && !clientHandler.clientUsername.equals(clientUsername) && !clientBlockedUsers.contains(clientHandler.clientUsername)) {
                    clientHandler.writer.writeObject(message);
                    clientHandler.writer.flush();
                }
            } catch (IOException e) {
                closeEverything(socket,reader,writer);
            }
        }
    }

    public void toggleSecretChat(boolean _secretChat) {
        for (ClientHandler clientHandler : clientHandlers){
            if (clientHandler.clientIsConnectedToTopic && clientHandler.currentTopic.equalsIgnoreCase(currentTopic)
                    && !clientHandler.clientUsername.equals(clientUsername)) {
                clientHandler.setSecretChat(_secretChat);
            }

        }
        Value v = new Value(_secretChat?"Server: entered secret chat":"Server: left secret chat");
        broadcastMessage(v, currentTopic);
    }

    public void removeClientHandler(String topic){
        clientHandlers.remove(this);
        //broadcastMessage(new Value("Server: " + clientUsername + " has exited the app"), topic);
    }

    public void closeEverything(Socket socket, ObjectInputStream reader, ObjectOutputStream writer) {
        try{
            removeClientHandler(currentTopic);
            broker.logToConsole(clientUsername + " has disconnected");
            if(reader != null){
                reader.close();
            }
            if(writer != null){
                writer.close();
            }
            if(socket != null){
                socket.close();
            }
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    public boolean isSecretChat() {
        return secretChat;
    }

    public void setSecretChat(boolean secretChat) {
        this.secretChat = secretChat;
    }
}
