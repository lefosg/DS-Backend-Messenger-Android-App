package com.ds;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

public class Client implements Publisher, Consumer
{
    private static final String EXIT_CHAT_COND = "quit_topic";
    private static final String SEND_FILE = "send_file";
    private static final String SECRET_CHAT = "secret_chat";
    private static final String SET_NICKNAME = "set_nickname";
    private static final String FRIEND_REQUEST = "friend_request";
    private static final String FRIEND_REQUEST_RESPONSE = "friend_request_response";
    private static final String UPLOAD_TOPIC_IMAGE = "upload_topic_image";
    private static final String SET_TOPIC_NAME = "set_topic_name";

    //Profile Information
    private ProfileName profile;
    private ArrayList<String> retrieved_topics = new ArrayList<>();
    //private ArrayList<MultimediaFile> retrieved_topic_images = new ArrayList<>();
    private String userPath, storyPath, othersStoriesPath, savedMedia;
    private MultimediaFile defaultTopicImage, defaultUserImage;
    private long story_deletion_delay = 1000 * 60;//1000 ms * X sec
    private int get_stories_count=-1;
    private ArrayList<MultimediaFile> retrieved_topic_images;


    //IO Objects
    private Socket socket;
    private ObjectInputStream reader;
    private ObjectOutputStream writer;
    private String currentBroker;

    public static void main (String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter your username for the group chat: ");
        String username = scanner.nextLine();
        System.out.print("Enter broker address: ");
        String address = scanner.nextLine();
        String[] info = address.split(":");
        Socket socket = null;
        try {
            socket = new Socket(info[0], Integer.parseInt(info[1]));
        } catch (Exception e) {
            System.out.print("Server is offline :(");
            System.exit(0);
        }
        ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
        ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
        oos.writeObject(username);
        Client client = new Client(socket, username, ois, oos);
        client.setCurrentBroker(address);
        client.init();
        client.listenForMessage();

        boolean flag = true;
        while (flag)
        {
            System.out.println();
            System.out.println("1. Enter a topic to send message");
            System.out.println("2. Register to a topic");
            System.out.println("3. Create a topic");
            System.out.println("4. Change account information");
            System.out.println("5. Display account information");
            System.out.println("6. Add friend");
            System.out.println("7. Display notifications");
            System.out.println("8. Exit");
            System.out.println("9. Upload story");
            System.out.println("10. Block user");
            System.out.println("11. Unblock user");
            System.out.println("12. View stories");
            System.out.print("Select operation: ");
            int operation = client.chooseAction(scanner);
            switch (operation)
            {
                case 1: {  //enter chat
                    if (client.profile.getSubbedTopics().isEmpty()) {
                        System.out.println("You are not subscribed to any topics!");
                        continue;
                    }
                    int i=0;
                    for (i = 0; i < client.getSubbedTopics().size(); i++) {
                        System.out.println((i+1) + ": " + client.getSubbedTopics().get(i));
                    }
                    System.out.print("Choose topic (or press 0 to return to menu): ");
                    int x = client.chooseAction(scanner);
                    if (x == 0) {
                        break;
                    }
                    while (x > client.getSubbedTopics().size()) {
                        x = client.chooseAction(scanner);
                    }
                    String topic = client.getSubbedTopics().get(x - 1);
                    client.initiateChat(topic);
                    break;
                }
                case 2: {  //register to a topic
                    System.out.print("Retrieving topics.");
                    client.push(new Value("GET_TOPICS"));  //get topics from broker
                    try {
                        Thread.sleep(250);
                        System.out.print(".");
                        Thread.sleep(250);
                        System.out.println(".");
                    } catch (Exception ignored) {}

                    if (client.retrieved_topics.size() == 0) {
                        System.out.print("No topics available right now! Go ahead and create one");
                        break;
                    }
                    ArrayList<String> temp_topics = new ArrayList<>();
                    temp_topics.addAll(client.retrieved_topics);
                    for (int i = 0; i < temp_topics.size(); i++) {
                        System.out.println((i+1) + ": " + temp_topics.get(i));
                    }
                    System.out.print("Choose topic (or press 0 to return to menu): ");
                    int x = client.chooseAction(scanner);
                    if (x == 0) {
                        break;
                    }
                    while (x > temp_topics.size()) {
                        x = client.chooseAction(scanner);
                    }
                    String topic = temp_topics.get(x - 1);
                    if (!client.getSubbedTopics().contains(topic)) {
                        client.sub(topic);
                    }
                    break;
                }
                case 3: {  //create topic
                    System.out.print("Insert a topic name to register: ");
                    String topic = scanner.nextLine();
                    client.register(topic);
                    break;
                }
                case 4: {  //edit account
                    System.out.println("1. Change username");
                    System.out.println("2. Leave group chat (topic)");
                    System.out.print("Select (or press 0 to return to menu): ");
                    int choice = client.chooseAction(scanner);
                    if (choice == 0) {
                        break;
                    }
                    if (choice == 1) {
                        System.out.print("Enter name: ");
                        String name = scanner.nextLine();
                        client.setUsername(name);
                    } else if (choice == 2) {
                        for (int i = 0; i < client.profile.getSubbedTopics().size(); i++) {
                            System.out.println((i + 1) + ": " + client.profile.getSubbedTopics().get(i));
                        }
                        System.out.print("Select group to leave: ");
                        int group_idx = client.chooseAction(scanner);
                        client.unsubscribe(client.profile.getSubbedTopics().get(group_idx - 1));
                    }
                    break;
                }
                case 5 : {  //print client information
                    System.out.print(client);
                    break;
                }
                case 6 : {  //send a friend request
                    //get the name
                    System.out.print("Enter name: ");
                    String name = scanner.nextLine();
                    //check if a request has already been sent
                    if (client.getProfile().getFriendRequestsSent().contains(name)) {
                        System.out.print("Request has already been sent to " + name);
                        break;
                    }
                    //check if he/she is already in friends list
                    else if (client.getProfile().getFriendsList().contains(name)) {
                        System.out.print(name + " is already in friends list");
                        break;
                    }
                    else if (name.equalsIgnoreCase(client.getUsername())) {
                        System.out.println("Cannot send friend request to yourself!");
                        break;
                    }
                    //send the request
                    client.push(new Value("FRIEND_REQUEST"));
                    client.push(new Value(name));
                    client.getProfile().addFriendRequest(name);
                    //System.out.println("Friend request sent!");
                    break;
                }
                case 7 : {  //show notifications
                    //if there are no notifications, don't show any
                    ArrayList<String> notifications = client.getNotifications();
                    if (notifications.size() == 0) {
                        System.out.print("No notifications to show :(" + '\n');
                        break;
                    }
                    //print notifications
                    for (int i = 0; i < notifications.size(); i++) {
                        System.out.println((i+1) + ": " + notifications.get(i));
                    }
                    System.out.print("Choose notification to preview (or press 0 to return to menu): ");
                    int notif = client.chooseAction(scanner);
                    if (notif == 0) {
                        break;
                    }
                    String notification = client.getNotification(notif-1);
                    if (notification.contains("friend request")) {
                        String other_person = notification.substring(0, notification.indexOf(" "));
                        System.out.println("Accept friend request? (yes/no) ");
                        String answer = scanner.nextLine();
                        if (answer.equals("yes")) {
                            client.getProfile().addFriend(other_person);
                        }
                        client.push(new Value(FRIEND_REQUEST_RESPONSE.toUpperCase()));
                        client.push(new Value(FRIEND_REQUEST_RESPONSE + " " + other_person + " " + answer));
                        client.getProfile().removeNotification(notif-1);
                    } else {
                        System.out.print(notification);
                    }
                    break;
                }
                case 8: {  //exit program
                    System.out.print("Exit program? ");
                    String s = scanner.nextLine();
                    if (s.equalsIgnoreCase("yes") || s.equalsIgnoreCase("y")) {
                        flag = false;
                        System.out.println("Goodbye");
                    }
                    break;
                }
                case 9: {  //upload story
                    System.out.println("Enter the path of the file you wish to upload as a story.");
                    String path = scanner.nextLine();
                    MultimediaFile story = new MultimediaFile(path, client.getUsername());
                    if(story.getFileName() == null){
                        System.out.println("File not found (" + path + ")");
                        continue;
                    }
                    story.setExpiryDate(new Date(new Date().getTime()+ client.story_deletion_delay));
                    client.saveFile(story, client.storyPath);
                    client.getProfile().addStory(story);
                    client.scheduleDeletion(story, client.storyPath);
                    client.push(new Value("UPLOAD_STORY"));
                    client.push(new Value(story));
                    break;
                }
                case 10: {  //block a user
                    System.out.print("Please enter the name of the user you wish to block: ");
                    String name = scanner.nextLine();
                    if(client.getProfile().getBlockList().contains(name)){
                        System.out.println("The user " + name + "has already been blocked");
                    }else if(client.getUsername().equalsIgnoreCase(name)){
                        System.out.println("You can't block yourself!");
                    }else{
                        client.getProfile().blockUser(name);
                        System.out.println("User " + name + " is now blocked!");
                        client.push(new Value("BLOCK_USER"));
                        client.push(new Value(name));
                    }
                    break;
                }
                case 11: {  //unblock user
                    System.out.print("Enter the name of the user to unblock: ");
                    String name = scanner.nextLine();
                    if (client.getProfile().getBlockList().contains(name)) {
                        client.getProfile().removeBlockedUser(name);
                        client.push(new Value("UNBLOCK_USER"));
                        client.push(new Value(name));
                        System.out.println("User " + name + " is now unblocked");
                    } else {
                        System.out.println("Cannot unblock that user because he is not blocked!");
                    }
                    break;
                }
                case 12: { //view stories
                    client.push(new Value("VIEW_STORIES"));
                    break;
                }
                default: {
                    System.out.println("Invalid operation!");
                    break;
                }
            }//switch
        }//while
        client.closeEverything();
        System.exit(0);
    }

    /**
     * Constructs the object, creates the user path, and loads subbed topics
     * @param socket
     * @param username
     * @param ois
     * @param oos
     */
    public Client(Socket socket, String username, ObjectInputStream ois, ObjectOutputStream oos) {
        try {
            //Profile Information Init
            profile = new ProfileName(username);
            //IO Objects Init
            this.socket = socket;
            this.reader = ois;
            this.writer = oos;
            this.userPath = "user/" + getUsername();
            this.storyPath = "user/" + getUsername() + "/stories/";
            this.othersStoriesPath = "user/" + getUsername() + "/others_stories/";
            this.savedMedia = "user/" + getUsername() + "/saved media/";
            if (!Files.exists(Paths.get(userPath))) {
                new File(userPath).mkdirs();
            }
            //loadSubbedTopics(userPath + "/subbed_topics.txt");
        } catch (Exception e) {
            closeEverything();
        }
    }

    /**
    * A thread that accepts chat messages, checking for special messages for features such
    * as friend requests, redirection/reconnection to other broker etc
    */
    public void listenForMessage() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setPriority(8);
                Value msgFromGroupChat;
                try {
                    defaultTopicImage = ((Value) reader.readObject()).getMultiMediaFile();  //get topic names
                    defaultUserImage = ((Value) reader.readObject()).getMultiMediaFile();  //get topic images
                    System.out.println("OLAOK");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                while(socket.isConnected()){
                    try{
                        msgFromGroupChat = (Value) reader.readObject();
                        if (msgFromGroupChat.getMessage() != null) {
                            String msg = msgFromGroupChat.getMessage();
                            //check if message is friend request response
                            if (msg.contains(FRIEND_REQUEST_RESPONSE)) {
                                String[] info = msg.split(" ");
                                String name = info[1];
                                String answer = info[2];
                                if (answer.equals("yes")) {
                                    profile.addFriend(name);
                                } else {
                                    profile.removeFriendRequest(name);
                                }
                                continue;
                            }
                            //check if message is friend request
                            else if (msg.contains("friend request")) {
                                profile.addNotification(msg);
                                continue;
                            }
                            //check if message is topic retrieval from broker
                            else if (msg.equalsIgnoreCase("RETRIEVE_TOPICS")) {
                                ArrayList<String> topic_names = (ArrayList<String>) reader.readObject();  //get topic names
                                ArrayList<MultimediaFile> topic_images = (ArrayList<MultimediaFile>) reader.readObject();  //get topic images
                                retrieved_topics = topic_names;
                                retrieved_topic_images = topic_images;
                                continue;
                            }
                            //check if client asked for story
                            else if (msg.equalsIgnoreCase("GET_STORY")) {
                                get_stories_count = ((Integer)reader.readObject());
                                while (get_stories_count != 0){
                                    com.ds.MultimediaFile story = ((com.ds.Value) reader.readObject()).getMultiMediaFile();
                                    System.out.println(story);
                                    saveFile(story, othersStoriesPath);
                                    //scheduleDeletion(story, othersStoriesPath);
                                    get_stories_count--;
                                }
                                get_stories_count = -1;
                                continue;
                            }
                            //check for broker message
                            else if (msg.equals("WRONG_BROKER")) {
                                String correct_address = ((Value)reader.readObject()).getMessage();
                                currentBroker = correct_address;
                                String topic = (((Value)reader.readObject()).getMessage());
                                String[] info = correct_address.split(":");
                                disconnect();
                                reconnect(new BrokerAddressInfo(info[0], Integer.parseInt(info[1])), topic);
                                continue;
                            }
                            if (!profile.getBlockList().contains(msgFromGroupChat.getSender())){
                                System.out.println(msgFromGroupChat.getDisplayMessage());
                            }
                        }
                        if (msgFromGroupChat.getMultiMediaFile() != null) {
                            saveFile((com.ds.MultimediaFile)msgFromGroupChat.getMultiMediaFile(), savedMedia);
                        }
                    } catch (Exception e) {
                        closeEverything();
                    }
                }
            }
        }).start();
    }

    /**
     * Enters a topic conversation
     * @param topic
     */
    public void initiateChat(String topic){
        push(new Value("MESSAGE"));
        push(new Value(topic));
        Scanner sc = new Scanner(System.in);
        while (socket.isConnected()){
            String messageToSend = sc.nextLine();
            if (messageToSend.length() > 512*1024) {
                try {
                    File temp = new File("user/"+getUsername()+"/temp_msg.txt");
                    FileWriter fileWriter = new FileWriter(temp);
                    fileWriter.write(messageToSend);
                    MultimediaFile fileToSend = new MultimediaFile(temp, getUsername());
                    push(new Value(fileToSend));
                    fileWriter.close();
                    temp.deleteOnExit();
                    continue;
                } catch (Exception e) {e.printStackTrace();}
            }
            //exit chat
            if (messageToSend.equalsIgnoreCase(EXIT_CHAT_COND)) {
                push(new Value("quit_topic"));
                System.out.println("Exited topic..");
                break;
            }
            //send multimedia file
            else if (messageToSend.contains(SEND_FILE)) {
                String file_path = messageToSend.substring(SEND_FILE.length()+1);
                MultimediaFile fileToSend = new MultimediaFile(file_path, getUsername());
                if (fileToSend.getFileName() == null) {
                    System.out.println("File not found (" + file_path + ")");
                    continue;
                }
                push(new Value(fileToSend));
                continue;
            }
            //set topic name
            else if(messageToSend.contains(SET_TOPIC_NAME)){
                push(new Value(messageToSend));
                continue;
            }
            //enter secret chat
            else if (messageToSend.equals(SECRET_CHAT)) {
                push(new Value(messageToSend));
                continue;
            }
            else if(messageToSend.contains(UPLOAD_TOPIC_IMAGE)){
                String file_path = messageToSend.substring(UPLOAD_TOPIC_IMAGE.length()+1);
                MultimediaFile fileToSend = new MultimediaFile(file_path, getUsername());
                if (fileToSend.getFileName() == null) {
                    System.out.println("File not found (" + file_path + ")");
                    continue;
                }
                push(new Value(messageToSend));
                push(new Value(fileToSend));
                continue;
            }
            //set your nickname in the topic
            else if (messageToSend.contains(SET_NICKNAME)) {
                push(new Value(messageToSend));
                continue;
            }
            //else just send a normal string message
            push(new Value(messageToSend));
        }
    }

    /**
     * Sends a Value to the clientHandler. It can be a string for the chat or a file
     * @param value
     */
    @Override
    public void push(Value value) {
        try {
            //send message
            value.setSender(getUsername());
            if (value.getMessage() != null) {
                writer.writeObject(value);
                writer.flush();
            }
            //send media
            if (value.getMultiMediaFile() != null) {
                //if the same media is sent again => media is cached, retrieve the chunks
                if (profile.getUserVideoFilesMap().containsKey(value.getMultiMediaFile().getFileName())) {
                    ArrayList<Value> chunks = profile.getUserVideoFilesMap().get(value.getMultiMediaFile().getFileName());
                    for (Value chunk : chunks) {
                        writer.writeObject(chunk);
                        writer.flush();
                    }
                    return;
                }
                //else generate chunks
                ArrayList<Value> chunks = generateChunks(value);
                profile.getUserVideoFilesMap().put(value.getMultiMediaFile().getFileName(), chunks);
                for (Value chunk : chunks) {
                    writer.writeObject(chunk);
                    writer.flush();
                }
            }
        } catch (Exception e){
            closeEverything();
        }
    }

    /**
     * Pushes a Value to the clientHandler containing the topic to subscribe
     * @param topic
     */
    public void sub(String topic) {
        if (!profile.getSubbedTopics().contains(topic)) {
            push(new Value("SUB"));
            push(new Value(topic));
            profile.getSubbedTopics().add(topic);
            profile.getDateRegistered().add(new Date());
            saveTopic(topic);
        } else {
            System.out.println("You are already subscribed");
        }
    }

    /**
     * Pushes a Value to the clientHandler containing the topic to create
     * It creates a topic and automatically subscribes this user to the topic
     * @param topic
     */
    @Override
    public void register(String topic) {
        if (!profile.getSubbedTopics().contains(topic)) {
            push(new Value("CREATE"));
            push(new Value(topic));
            profile.getSubbedTopics().add(topic);
            profile.getDateRegistered().add(new Date());
            saveTopic(topic);
        } else {
            System.out.println("You are already subscribed");
        }
    }

    /**
     * Saves a topic in the subbed_topics.txt file located in the directory of the user
     * @param topic
     */
    public void saveTopic(String topic) {
        //saves topic to subbed_topics.txt
        try (FileWriter fw = new FileWriter(userPath + "/subbed_topics.txt", true)) {
            fw.append(topic);
            fw.append('\n');
            fw.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Unsubscribes a user from a topic
     * @param topic
     */
    public void unsubscribe(String topic) {
        if (profile.getSubbedTopics().contains(topic)) {
            push(new Value("UNSUB"));
            push(new Value(topic));
            profile.getSubbedTopics().remove(topic);
            try {
                //removes the topic from subbed_topics.txt
                File file = new File(userPath+"/subbed_topics.txt");
                List<String> out = Files.lines(file.toPath())
                        .filter(line -> !line.contains(topic))
                        .collect(Collectors.toList());
                Files.write(file.toPath(), out, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("You are not subscribed to the topic " + topic);
        }
    }

    /**
     * Loads the subscribed topics of the user from previous sessions from the subbed_topics.txt file
     * @param user_path
     */
    private void loadSubbedTopics(String user_path) {
        try {
            File savedSubbedTopics = new File(user_path);
            if (!savedSubbedTopics.exists()) {
                savedSubbedTopics.createNewFile();
                return;
            }
            if (savedSubbedTopics.length() == 0) {
                return;
            }
            Scanner scanner = new Scanner(savedSubbedTopics);
            while (scanner.hasNext()) {
                profile.getSubbedTopics().add(scanner.nextLine());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Breaks the Value given to chunks if the MultimediaFile contained in the Value is larger
     * than 512 kb
     * @param value
     * @return An array list of type Value. Each position is a chunk of the original Value
     */
    @Override
    public ArrayList<Value> generateChunks(Value value) {
        MultimediaFile file = value.getMultiMediaFile();
        ArrayList<Value> chunks = new ArrayList<Value>();
        int sizePerChunk = 524288;  //512kb = 512*1024 byte
        try {
            if(file.getLength() > sizePerChunk) {
                int numberOfChunks = (int) file.getLength() / sizePerChunk;
                for (int i = 0; i < numberOfChunks; i++) {
                    byte[] currentChunk = new byte[sizePerChunk];
                    for (int j = 0; j < sizePerChunk; j++) {
                        currentChunk[j] = file.getMultimediaFileChunk()[j + i * sizePerChunk];
                    }
                    Value temp_value = new Value(file, currentChunk);
                    temp_value.getMultiMediaFile().setMf(1);
                    chunks.add(temp_value);
                }

                int remainingChunk = (int) file.getLength() % sizePerChunk;
                byte[] currentChunk = new byte[remainingChunk];
                for (int j = 0; j < remainingChunk; j++) {
                    currentChunk[j] = file.getMultimediaFileChunk()[j + (int)file.getLength()-remainingChunk];
                }
                Value temp_value = new Value(file, currentChunk);
                chunks.add(temp_value);
            }else{
                chunks.add(value);
            }
            chunks.get(chunks.size()-1).getMultiMediaFile().setMf(0);
        }catch(Exception e){
            e.printStackTrace();
        }
        return chunks;
    }

    /**
     * Saves a file locally to the user_path/saved media
     * @param mmf
     */
    public void saveFile(MultimediaFile mmf, String path){
        try{
            File file = new File(userPath);
            if (!Files.exists(Paths.get(userPath))) {
                file.mkdirs();
            }
            file = new File(path);
            if (!Files.exists(Paths.get(path))) {
                file.mkdirs();
            }
            file = new File(path+mmf.getFileName());
            file.createNewFile();
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            os.write(mmf.getMultimediaFileChunk());
            byte[] data = os.toByteArray();

            FileOutputStream fos = new FileOutputStream(file, true);
            fos.write(data);
            fos.close();
        }catch(Exception e){

        }
    }

    private void scheduleDeletion(MultimediaFile story, String path) {
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                synchronized (this){
                    try {
                        File storyfile = new File(path + story.getFileName());
                        FileWriter fw = new FileWriter(storyfile);
                        if (storyfile.exists()) {
                            fw.close();
                            storyfile.delete();
                        }
                        if (path.equals(storyPath)) {
                            int index = profile.getStories().indexOf(story);
                            profile.removeStory(index);
                        }
                    } catch (Exception e) {
                        System.out.println("File was not found");
                    }
                }
            }
        };
        timer.schedule(task, story.getExpiryDate());
    }

    /**
     * Using regex we prevent the user from giving invalid input when we ask
     * integers as input. e.g. "1q" is invalid, we need an int value
     * @param scanner
     * @return The int value that was given as input
     */
    public int chooseAction(Scanner scanner) {
        String action = scanner.nextLine();
        while (action.matches("[a-zA-Z]*$")) {
            System.out.print("Invalid input, choose action again: ");
            action = scanner.nextLine();
        }
        return Integer.parseInt(action);
    }

    /**
     * Closes all the connections of the user with the clientHandler - socket, input and output stream
     */
    public void closeEverything() {
        try {
            if(reader != null){
                reader.close();
            }
            if(writer != null){
                writer.close();
            }
            if(socket != null){
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("An error occurred - Exiting program");
            System.exit(0);
        }
    }

    /**
     * Send some initialization data to the clientHandler
     */
    @Override
    public void init() {
        //at the start send username and subbed topics
        try {
            writer.writeObject(profile.getUsername());
            writer.writeObject(profile.getBlockList());
            writer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void showConversationData(String topic, Value v) {

    }

    @Override
    public void connect(String ip, int port) {

    }

    /**
     * Reconnects to another broker when user needs to chat to a topic that does not belong to
     * the current one
     * @param address address of the broker to connect to
     * @param topic in which topic the client wants to connect
     */
    public void reconnect(BrokerAddressInfo address, String topic) {
        try {
            socket = new Socket(address.getIp(), address.getPort());
            writer = new ObjectOutputStream(socket.getOutputStream());
            writer.writeObject(getUsername());  //readObject ston broker
            reader = new ObjectInputStream(socket.getInputStream());
            init();  //readObject ston clientHandler
            writer.writeObject(new Value("MESSAGE"));
            writer.writeObject(new Value(topic));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Calls <i>closeEverything</i> method
     */
    @Override
    public void disconnect() {
        closeEverything();
    }


    @Override
    public void updateNodes() {

    }

    @Override
    public void disconnect(String s) {
    }

    @Override
    public void getBrokerList() {

    }

    @Override
    public BrokerInterface hashTopic(String topic) {
        return null;
    }

    @Override
    public void notifyBrokersNewMessage(String message) {

    }

    @Override
    public void notifyFailure(BrokerInterface broker) {

    }

    // getters & setters
    public void setUsername(String username) {
        this.profile.setUsername(username);
    }

    public String getUsername(){
        return profile.getUsername();
    }

    public ArrayList<String> getSubbedTopics() {
        return profile.getSubbedTopics();
    }

    public ProfileName getProfile() {
        return profile;
    }

    public ArrayList<String> getNotifications() {
        return profile.getNotifications();
    }

    public String getNotification(int index) {
        return profile.getNotifications().get(index);
    }

    public String getCurrentBroker() {
        return currentBroker;
    }

    public void setCurrentBroker(String currentBroker) {
        this.currentBroker = currentBroker;
    }



    @Override
    public String toString() {
        return "Username: " + profile.getUsername() + "\n" +
                "Date account created: " + profile.getDateAccountCreated() + "\n" +
                "Subscribed topics: " + String.join(", ", profile.getSubbedTopics()) + "\n" +
                "Friends: " + String.join(", ", profile.getFriendsList()) + '\n' +
                "Current broker: " + currentBroker + '\n' +
                "Blocked users: " + String.join(", ", profile.getBlockList()) + '\n' +
                "Stories: " + profile.getStories() + '\n' +
                "Socket: " + socket + " " + socket.isConnected();
    }
}
