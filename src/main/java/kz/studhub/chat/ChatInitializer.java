package kz.studhub.chat;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class ChatInitializer implements ApplicationRunner {
    private final ChatRoomRepository rooms;
    public ChatInitializer(ChatRoomRepository rooms) { this.rooms = rooms; }
    @Override public void run(ApplicationArguments args) {
        if (rooms.existsById("global")) return;
        try { rooms.saveAndFlush(new ChatRoom("global", "GLOBAL", "StudHub", null)); }
        catch (DataIntegrityViolationException error) { if (!rooms.existsById("global")) throw error; }
    }
}
