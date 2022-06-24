package com.ds;

import java.io.*;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.abs;

public class Broker implements BrokerInterface
{
    private ServerSocket serverSocket;
    private ArrayList<Topic> topics;
    private BrokerAddressInfo addressInfo;
    private final BigInteger brokerHash;
    private final ArrayList<Socket> sockets;
    private final ArrayList<ObjectOutputStream> writers;
    private final ArrayList<ObjectInputStream> readers;
    private final HashMap<Integer, Integer> localPortToPort;
    private final HashMap<BrokerAddressInfo, BigInteger> addressToHash;
    private final ArrayList<BrokerAddressInfo> brokerInfo = new ArrayList<BrokerAddressInfo>();
    public final HashMap<String,BrokerAddressInfo> topicToBroker= new HashMap<String,BrokerAddressInfo>();
    private HashMap<BrokerAddressInfo, Integer> brokerAddressToId = new HashMap<>();
    private HashMap<String, Integer> addressToSocketMapper = new HashMap<>();
    private ArrayList<MultimediaFile> stories = new ArrayList<>();
    private int story_deletion_delay = 60;
    private String imgPath = "res/TopicImages/";
    private ArrayList<String> allTopicNames = new ArrayList<>();
    private ArrayList<MultimediaFile> allTopicImages = new ArrayList<>();
    public final HashMap<String,ArrayList<String>> topicToSubs;


    private static class ShutDownTask extends Thread {
        Broker broker;
        ShutDownTask(Broker broker) {
            this.broker = broker;
        }
        @Override
        public void run() {
            try {
                // delete existence from txt file
                File file = new File("src/com/ds/brokers_up.txt");
                List<String> out = Files.lines(file.toPath())
                        .filter(line -> !line.contains(broker.getAddressInfo().getIp()+":"+broker.getAddressInfo().getPort()))
                        .collect(Collectors.toList());
                Files.write(file.toPath(), out, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                // close socket connections with other brokers
                for (ObjectOutputStream oos:broker.writers)
                    oos.close();
                for (ObjectInputStream ois:broker.readers)
                    ois.close();
                for (Socket socket:broker.sockets)
                    socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter ip and address: ");
        String address = scanner.nextLine();
        String ip = address.substring(0, address.lastIndexOf(':'));
        int port = Integer.parseInt(address.substring(address.lastIndexOf(':') + 1));
        Broker broker = new Broker(ip,port);
        ShutDownTask shutDownTask = new ShutDownTask(broker);  //thread
        Runtime.getRuntime().addShutdownHook(shutDownTask);  //thread
        broker.init();  //thread
        broker.connect();
        broker.createDefaultTopics("src/com/ds/default_topics.txt");
        broker.executeCommands();
    }

    public void createDefaultTopics(String path) {
        try {
            int counter = 0;
            Scanner scanner = new Scanner(new File(path));
            Scanner tempScanner = new Scanner(new File("src/com/ds/broker_addresses.txt"));
            ArrayList<String> brokerAddresses = new ArrayList<>();
            HashMap<Integer, String> brokerNumToAddress = new HashMap<>();
            while (tempScanner.hasNext()) {
                brokerAddresses.add(tempScanner.nextLine());
            }
            for (int i = 0; i <brokerAddresses.size(); i++) {
                brokerNumToAddress.put(i, brokerAddresses.get(i));
                String[] info = brokerAddresses.get(i).split(":");
                brokerAddressToId.put(new BrokerAddressInfo(info[0], Integer.parseInt(info[1])), i);
            }
            while (scanner.hasNext()) {
                counter++;
                String topicName = scanner.nextLine();
                BigInteger topicHash = sha1(topicName);
                int rightBroker = topicHash.intValue() % 3;
                if(rightBroker < 0){
                    rightBroker = abs(rightBroker);
                }
                if (brokerNumToAddress.get(rightBroker).equals(addressInfo.toString())) {
                    String temp_imgPath = imgPath + counter + ".jpg";
                    //imgPath+=counter;
                    //imgPath+=".jpg";
                    topics.add(new Topic(topicName,temp_imgPath));
                }
                allTopicNames.add(topicName);
                allTopicImages.add(new MultimediaFile(imgPath + counter + ".jpg", null));
            }
        } catch (Exception e) {
            System.out.print("Could not create default topics");
        }
    }

    public Broker(String IP, int PORT)
    {
        addressInfo = new BrokerAddressInfo(IP, PORT);
        topics = new ArrayList<>();
        brokerHash = sha1(addressInfo.toString());
        sockets = new ArrayList<>();
        writers = new ArrayList<>();
        readers = new ArrayList<>();
        localPortToPort = new HashMap<>();
        addressToHash = new HashMap<>();
        topicToSubs = new HashMap<>();
        try  {
            serverSocket = new ServerSocket(addressInfo.getPort(),20);
            FileWriter fileWriter = new FileWriter("src/com/ds/brokers_up.txt", true);
            fileWriter.append(addressInfo.toString());
            fileWriter.append('\n');
            fileWriter.flush();
            fileWriter.close();
            saveBrokerInfo();
        } catch (Exception e) {
            closeServerSocket();
        }
    }

    public void saveBrokerInfo(){
        File file = new File("src\\com\\ds\\broker_addresses.txt");
        try {
            Scanner sc = new Scanner(file);
            while (sc.hasNext()){
                String[] str = sc.nextLine().split(":");
                brokerInfo.add(new BrokerAddressInfo(str[0],Integer.parseInt(str[1])));
            }
        }
        catch (FileNotFoundException e){
            e.printStackTrace();
        }
    }

    @Override
    public void init() {
        Broker broker = this;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println("Broker is up at " + addressInfo);
                    while(!serverSocket.isClosed()) {
                        Socket socket = broker.serverSocket.accept();
                        ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
                        ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                        String s = (String)ois.readObject();
                        if (s.contains("broker")){
                            broker.sockets.add(socket);
                            broker.readers.add(ois);
                            broker.writers.add(oos);
                            int port = Integer.parseInt(s.substring("broker ".length()));
                            broker.localPortToPort.put(socket.getPort(), port);
                            BrokerAddressInfo newBrokerAddress = new BrokerAddressInfo(socket.getInetAddress().getHostAddress(), port);
                            broker.addressToHash.put(newBrokerAddress, sha1(newBrokerAddress.toString()));
                            broker.addressToSocketMapper.put(newBrokerAddress.toString(), addressToSocketMapper.size());
                            broker.listen(socket, ois);
                            broker.disconnect(socket, ois);
                            continue;
                        }
                        broker.logToConsole("A new Client (" + s + ") has connected");
                        ClientHandler clientHandler = new ClientHandler(socket, broker, oos, ois);
                        Thread thread = new Thread(clientHandler);
                        thread.start();
                    }
                } catch (Exception e) {
                    closeServerSocket();
                }
            }
        }).start();
    }

    public BigInteger sha1(String channelName) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] messageDigest = md.digest(channelName.getBytes());
            return new BigInteger(1, messageDigest);
        } catch (NoSuchAlgorithmException e) {
            closeServerSocket();
            throw new RuntimeException(e);
        }
    }

    public synchronized void createTopic(String topic_name, String username) {
        Topic topic = new Topic(topic_name, imgPath + "default_topic_image.png");
        if (topics.contains(topic)) {
            topics.get(topics.indexOf(topic)).subscribe(username);
        } else {
            topic.subscribe(username);
            topics.add(topic);
        }

        topicToSubs.put(topic_name,getTopic(topic_name).getClientsSubbed());
        updateBrokers();
    }

    public void updateBrokers(){
        for (int i =0; i< sockets.size();i++){
            System.out.println(topicToSubs);
            sendToOtherBroker(i,topicToSubs);
        }
    }

    public synchronized void subscribe(String topic, String username) {
        getTopic(topic).subscribe(username);

        topicToSubs.put(topic,getTopic(topic).getClientsSubbed());
        updateBrokers();
    }

    public synchronized void unsubscribe(String topic_name, String username) {
        Topic topic = new Topic(topic_name);
        if (!topics.get(topics.indexOf(topic)).getClientUsernames().contains(username)) {
            System.out.println("User " + username + " is not subbed to the topic " + topic_name);
        } else {
            System.out.println(username + " exited the topic " + topic_name);
            topics.get(topics.indexOf(topic)).unsubscribe(username);

            topicToSubs.put(topic_name,getTopic(topic_name).getClientsSubbed());
            updateBrokers();
        }
    }

    public synchronized void addMessageToTopic(Value messageFromClient, String currentTopic) {
        Topic topic = new Topic(currentTopic);
        topics.get(topics.indexOf(topic)).addMessage(messageFromClient);
    }

    public synchronized ArrayList<Value> getChatData(String topic_name) {
        return topics.get(topics.indexOf(new Topic(topic_name))).getMessages();
    }

    public void closeServerSocket(){
        System.out.println("An error has occurred");
        try {
            if(serverSocket != null) {
                serverSocket.close();
            }
        } catch (Exception e){
            e.printStackTrace();
        }
        System.exit(0);
    }

    public void executeCommands() {
        Scanner cmdScanner = new Scanner(System.in);
        while (true) {
            System.out.println("Commands: shutdown(1), show_sockets(2), show_topics(3), show_broker_info(4), refresh_brokers(5), " +
                    "show_stories(6)");
            int command = cmdScanner.nextInt();
            if (command == 1) {
                System.exit(0);
            }
            if (command == 2) {
                System.out.println(sockets);
                System.out.println(writers);
                System.out.println(readers);
            }
            if (command == 3) {
                ArrayList<Topic> topics = getTopics();
                if (topics.size() == 0) {
                    System.out.println("No topics to show");
                    continue;
                }
                for (Topic topic : topics)
                    System.out.println(topic);
            }
            if (command == 4) {
                System.out.println(this);
            }
            if (command == 5) {
                updateNodes();
            }
            if (command == 6) {
                if (stories.size() == 0) {
                    System.out.println("No stories available");
                    continue;
                }
                for (MultimediaFile story : stories) {
                    System.out.println(story);
                };
            }
        }
    }
    public void logToConsole(String string) {
        System.out.println(string);
    }

    public void closeConnections(int index) throws Exception{
        //BrokerAddressInfo b = new BrokerAddressInfo(sockets.get(index).getInetAddress().getHostAddress())
        //addressToHash.remove(b);
        sockets.get(index).close();
        sockets.remove(index);
        readers.get(index).close();
        readers.remove(index);
        writers.get(index).close();
        writers.remove(index);
    }

    public void listen(Socket socket, ObjectInputStream ois) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int brokerIndex = sockets.indexOf(socket);
                System.out.println(brokerIndex);
                while (!socket.isClosed()) {
                    try {
                        Object msg2 = ois.readObject();
                        System.out.println("Good " + msg2);
                        if(msg2 instanceof HashMap){
                            HashMap<String,ArrayList<String>> mapinfo = (HashMap<String,ArrayList<String>>) msg2;
                            System.out.println("Map Info " + mapinfo);
                            System.out.println(msg2);
                            System.out.println(((HashMap<?, ?>) msg2).get("zzzzzzzzzzzzzzzzzzz"));
                        }else {
                            Value msg = (Value) ois.readObject();
                            System.out.println("LISTEN TOY BROKER: " + msg);
                            // if broker must create topic
                            if (msg.getMessage().equals("CREATE")) {
                                String topic_name = ((Value) ois.readObject()).getMessage(); //format of this message: ds alex
                                System.out.println(topic_name);
                                String[] info = topic_name.split(" ");
                                createTopic(info[0], info[1]);
                            }
                            // if broker must unsubscribe a user from a topic
                            else if (msg.getMessage().equals("UNSUB")) {
                                String topic_name = ((Value) ois.readObject()).getMessage();
                                System.out.println(topic_name);
                                String[] info = topic_name.split(" ");
                                unsubscribe(info[0], info[1]);
                            }
                            //if broker must subscribe a user to a topic
                            else if (msg.getMessage().equals("SUB")) {
                                String[] info = ((Value) ois.readObject()).getMessage().split(" ");
                                String topicName = info[0];
                                String username = info[1];
                                subscribe(topicName, username);
                            }
                            //if another broker wants to get the available topics
//                        else if (msg.getMessage().equals("GET_AVAILABLE_TOPICS")) {
//                            ArrayList<String> topicNames = getTopicNames();
//                            sendToOtherBroker(brokerIndex, new Value("GET_TOPICS"));
//                            writers.get(brokerIndex).writeObject(topicNames);
//
//                        }
//                        //get the topics that we asked for
//                        else if (msg.getMessage().contains("GET_TOPICS")) {
//                            allTopicNames.add(msg.getMessage().split(" ")[1]);
//                        }
//                        //if another broker wants to get the available topics images
//                        else if (msg.getMessage().equals("GET_AVAILABLE_TOPICS_IMAGES")) {
//                            ArrayList<MultimediaFile> topicNames = getTopicImages();
//                            for (MultimediaFile topicImage : topicNames) {
//                                sendToOtherBroker(brokerIndex, new Value("GET_TOPIC_IMAGE"));
//                                sendToOtherBroker(brokerIndex, new Value(topicImage));
//                            }
//                        }
//                        //get the topic images that we asked for
//                        else if (msg.getMessage().contains("GET_TOPIC_IMAGE")) {
//                            MultimediaFile topicImage = ((Value)ois.readObject()).getMultiMediaFile();
//                            allTopicImages.add(topicImage);
//                        }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }).start();
    }

    public synchronized void sendToOtherBroker(int broker, Value v) {
        try {
            System.out.println("send to broker: " + broker);
            System.out.println("value: " + v);
            writers.get(broker).writeObject(v);
            writers.get(broker).flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void sendToOtherBroker(int broker, HashMap<String,ArrayList<String>> info) {
        try {
            System.out.println("AAAA");
            writers.get(broker).writeObject(info);
            writers.get(broker).flush();
            writers.get(broker).reset();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void connect() {
        try {
            System.out.println("started broker discovery");
            //collect all the ip addresses
            Scanner addressesScanner = new Scanner(new File("src/com/ds/broker_addresses.txt"));
            ArrayList<String> addresses = new ArrayList<>();
            synchronized (this){
                while (addressesScanner.hasNext()) {
                    addresses.add(addressesScanner.nextLine());
                }
            }
            //loop the addresses
            for (String address : addresses) {
                String ip = address.substring(0, address.lastIndexOf(':'));
                int port = Integer.parseInt(address.substring(address.lastIndexOf(':') + 1));
                //check if address is the same as current broker
                if (ip.equals(addressInfo.getIp()) && port == addressInfo.getPort()) {
                    continue;
                }
                //check if there is already a connection made with this address
                boolean create_socket = true;
                for (Socket existing_socket:sockets) {
                    try {
                        String existing_ip = existing_socket.getInetAddress().getHostAddress();
                        int existing_port = localPortToPort.get(existing_socket.getPort());
                        if (ip.equals(existing_ip) && (port == existing_port || port == existing_socket.getPort()
                                || localPortToPort.containsKey(port) || localPortToPort.containsValue(port))) {
                            create_socket = false;
                            break;
                        }
                    } catch (Exception ignored) {}
                }
                //if there is no connection, create one
                if (create_socket) {
                    System.out.print("trying " + ip + ":" + port + "... ");
                    try {
                        //create socket connection
                        Socket socket = new Socket(ip, port);
                        sockets.add(socket);
                        ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                        oos.writeObject("broker " + addressInfo.getPort());
                        ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
                        writers.add(oos);
                        readers.add(ois);
                        System.out.println("connection established");
                        localPortToPort.put(socket.getLocalPort(), port);
                        localPortToPort.put(port, socket.getLocalPort());
                        BrokerAddressInfo newBrokerAddress = new BrokerAddressInfo(ip, port);
                        addressToHash.put(newBrokerAddress, sha1(newBrokerAddress.toString()));
                        addressToSocketMapper.put(newBrokerAddress.toString(), addressToSocketMapper.size());
                        listen(socket, ois);
                        disconnect(socket, ois);
                    } catch (Exception e) {
                        System.out.println("destination unreachable");
                    }
                }
            }
            System.out.println("broker discovery ended");
            addressesScanner.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void connect(String ip, int port) {

    }

    @Override
    public void disconnect() {
        Broker broker = this;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setPriority(8);
                while (!broker.serverSocket.isClosed()) {
                    for (int i = 0; i < broker.sockets.size(); i++) {
                        try {
                            if (broker.readers.get(i).read() == -1
                                    || !broker.sockets.get(i).getInetAddress().isReachable(1500)) {
                                broker.logToConsole("removing socket " + broker.sockets.get(i));
                                broker.closeConnections(i);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }).start();
    }

    public void disconnect(Socket socket, ObjectInputStream ois) {
        Broker broker = this;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setPriority(8);
                while (!broker.serverSocket.isClosed()) {
                    while (!socket.isClosed()) {
                        try {
                            if (!socket.getInetAddress().isReachable(1500)) {
                                broker.logToConsole("removing socket " + broker.sockets.get(broker.sockets.indexOf(socket)));
                                broker.closeConnections(broker.sockets.indexOf(socket));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }).start();
    }

    @Override
    public Consumer acceptConnection(Consumer consumer) {
        return null;
    }

    @Override
    public Publisher acceptConnection(Publisher publisher) {
        return null;
    }

    @Override
    public void calculateKeys() {

    }

    @Override
    public void filterConsumers(String filter) {

    }

    @Override
    public void notifyBrokersOnChanges() {

    }

    @Override
    public void notifyPublisher(String notification) {

    }

    @Override
    public void pull(String s) {

    }


    @Override
    public void updateNodes() {
        connect();
        for (int i = 0; i < sockets.size(); i++) {
            try {
                if (readers.get(i).read() == -1
                        || !sockets.get(i).getInetAddress().isReachable(1500)) {
                    logToConsole("removing socket " + sockets.get(i));
                    closeConnections(i);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public BrokerAddressInfo findResponsibleBrokerAddress(String topic){
        BigInteger topicHashKey = sha1(topic);
        int rightBroker = topicHashKey.intValue() % 3;
        if(rightBroker < 0){
            rightBroker = abs(rightBroker);
        }
        for (BrokerAddressInfo address : brokerInfo){
            if(rightBroker == brokerInfo.indexOf(address)){
                return address;
            }
        }
        return null;
    }
    public synchronized boolean checkRightBroker(BrokerAddressInfo rightBrokerAddress, BrokerAddressInfo addressInfo) {
        System.out.println(rightBrokerAddress.equals(addressInfo));
        if(!rightBrokerAddress.equals(addressInfo)){
            return false;
        }
        return true;
    }

    public synchronized void addStory(MultimediaFile story) {
        stories.add(story);
        scheduleDeletion(story);
    }

    public synchronized ArrayList<MultimediaFile> getStories() {
        return stories;
    }

    private void scheduleDeletion(MultimediaFile story) {
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                synchronized (this){
                    try {
                        stories.remove(story);
                    } catch (Exception e) {
                        System.out.println("Story was not found");
                    }
                }
            }
        };
        timer.schedule(task, 1000*story_deletion_delay);  //1000 ms * X sec
    }


    //getters & setters
    public synchronized ArrayList<Topic> getTopics() {
        return topics;
    }

    public synchronized ArrayList<String> getTopicNames() {
        ArrayList<String> topic_names = new ArrayList<>();
        for (Topic topic:topics) {
            topic_names.add(topic.getName());
        }
//        for (int i = 0; i < sockets.size(); i++) {
//            sendToOtherBroker(i, new Value("GET_AVAILABLE_TOPICS"));
//        }
        return topic_names;
    }

    public synchronized ArrayList<MultimediaFile> getTopicImages() {
        ArrayList<MultimediaFile> topicImages = new ArrayList<>();
        for(Topic topic : topics) {
            topicImages.add(topic.getTopicPicture());
        }
        return topicImages;
    }

    public synchronized void setTopics(ArrayList<Topic> topics) {
        this.topics = topics;
    }
    public synchronized Topic getTopic(String topic_name) {
        return topics.get(topics.indexOf(new Topic(topic_name)));
    }
    public synchronized BrokerAddressInfo getAddressInfo() {
        return addressInfo;
    }
    public synchronized void setAddressInfo(BrokerAddressInfo addressInfo) {
        this.addressInfo = addressInfo;
    }
    public synchronized ArrayList<BrokerAddressInfo> getBrokerInfo(){
        return brokerInfo;
    }
    public synchronized HashMap<BrokerAddressInfo, Integer> getBrokerAddressToId() {
        return brokerAddressToId;
    }
    public synchronized void setBrokerAddressToId(HashMap<BrokerAddressInfo, Integer> brokerAddressToId) {
        this.brokerAddressToId = brokerAddressToId;
    }
    public synchronized HashMap<String, Integer> getAddressToSocketMapper() {
        return addressToSocketMapper;
    }
    public ArrayList<String> getAllTopicNames() {
        return allTopicNames;
    }
    public void setAllTopicNames(ArrayList<String> allTopicNames) {
        this.allTopicNames = allTopicNames;
    }
    public ArrayList<MultimediaFile> getAllTopicImages() {
        return allTopicImages;
    }
    public void setAllTopicImages(ArrayList<MultimediaFile> allTopicImages) {
        this.allTopicImages = allTopicImages;
    }

    @Override
    public String toString() {
        return "Broker at " + addressInfo.toString() + '\n' +
                "Broker hash: " + brokerHash + '\n' +
                "Server socket: " + serverSocket + '\n' +
                "Addresses hash map: " + addressToHash.toString() + '\n' +
                "Address to socket mapper: " + addressToSocketMapper;
    }
}