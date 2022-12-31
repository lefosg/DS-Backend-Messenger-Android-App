# DS-Backend-Messenger-Android-App

A project made as a project for the Dirstrubed Systems university course. The project was about building a Messenger-like application with Java. Chat, upload stories, make friends etc, generally including multimedia transmissions. It is based on the Pub-Sub system (Topic-based). This repo is about the backend of the application. There is also the frontend which is an Android Studio app (link of the frontend repo: https://github.com/lefosg/DS-Frontend-Messenger-Android-App). The concept of implementation is as follows: <br>

## Server/Broker
The broker is written in the ```Broker.java``` file. It initiates a typical java ```ServerSocket```. This implementation supports multiple brokers (mainly tested with 3 brokers) and is (somehow) fault tolerant. The broker is multithreaded. For each connection it spawns a thread running the code in ```ClientHandler.java```.

### ClientHandler
The ClientHandler is responsible for fulfilling the Client's requests. The requests are wrapped in the ```Value``` class, indicating if it is a command (e.g. "UPLOAD STORY", "FRIEND REQUEST", ..). If media must be sent to the server, it is wrapped in the ```MultimediaFile``` class.
